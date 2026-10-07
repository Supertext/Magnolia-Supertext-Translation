package com.supertext.magnolia.translation.content;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.rendering.template.TemplateDefinition;
import info.magnolia.rendering.template.registry.TemplateDefinitionRegistry;
import info.magnolia.ui.dialog.DialogDefinition;
import info.magnolia.ui.dialog.DialogDefinitionRegistry;
import info.magnolia.ui.dialog.FormDialogDefinition;
import info.magnolia.ui.editor.CurrentItemProviderDefinition;
import info.magnolia.ui.field.EditorPropertyDefinition;
import info.magnolia.ui.editor.FormDefinition;
import info.magnolia.ui.editor.JcrChildNodeProviderDefinition;
import info.magnolia.ui.editor.NestedContentProvider;
import info.magnolia.ui.field.CompositeFieldDefinition;
import info.magnolia.ui.field.NoopNameDecorator;
import info.magnolia.ui.field.RichTextFieldDefinition;
import info.magnolia.ui.field.TextFieldDefinition;

/**
 * Finds the translatable fields of a page, area or component the way Magnolia's own dialogs
 * see them: the node's template ({@code mgnl:template}) names a dialog, and every text or rich
 * text field in that dialog marked {@code i18n: true} is translated. Fields inside composite
 * fields are included, whether they store on the same node or in a child node (except an
 * {@code i18n} composite with child nodes, which keeps one child node per language). Settings
 * can add property names (translated wherever they hold text) and exclude others.
 */
public class DialogFieldResolver implements FieldResolver {

    private static final Logger log = LoggerFactory.getLogger(DialogFieldResolver.class);

    private final TemplateDefinitionRegistry templates;
    private final DialogDefinitionRegistry dialogs;
    private final Set<String> additional;
    private final Map<String, List<TranslatableField>> cache = new ConcurrentHashMap<>();

    public DialogFieldResolver(TemplateDefinitionRegistry templates, DialogDefinitionRegistry dialogs, Set<String> additional) {
        this.templates = templates;
        this.dialogs = dialogs;
        this.additional = additional == null ? Set.of() : additional;
    }

    @Override
    public List<TranslatableField> fieldsOf(Node node) throws RepositoryException {
        Map<String, TranslatableField> result = new LinkedHashMap<>();
        String templateId = NodeTypes.Renderable.getTemplate(node);
        if (templateId != null && !templateId.isEmpty()) {
            for (TranslatableField field : cache.computeIfAbsent(templateId, this::fieldsOfTemplate)) {
                result.put(field.childNode() + "/" + field.name(), field);
            }
        }
        for (String name : additional) {
            if (!result.containsKey("/" + name) && node.hasProperty(name)) {
                Property property = node.getProperty(name);
                if (!property.isMultiple() && property.getType() == PropertyType.STRING) {
                    result.put("/" + name, new TranslatableField(name, looksLikeHtml(property.getString())));
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private List<TranslatableField> fieldsOfTemplate(String templateId) {
        try {
            TemplateDefinition template = templates.getProvider(templateId).get();
            String dialogId = template.getDialog();
            if (dialogId == null || dialogId.isEmpty()) {
                return List.of();
            }
            DialogDefinition dialog = dialogs.getProvider(dialogId).get();
            if (!(dialog instanceof FormDialogDefinition<?> formDialog) || formDialog.getForm() == null) {
                log.info("Supertext: dialog {} of template {} is not a Magnolia 6 form dialog; add its fields under additionalProperties to translate them.", dialogId, templateId);
                return List.of();
            }
            List<TranslatableField> fields = new ArrayList<>();
            collect(formDialog.getForm(), false, "", fields);
            return List.copyOf(fields);
        } catch (RuntimeException e) {
            // Unknown template or dialog (e.g. a removed module): nothing from the dialog.
            log.debug("Supertext: no dialog fields for template {}: {}", templateId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Adds the i18n text fields of {@code form}.
     *
     * @param i18nParent the enclosing composite is i18n (stored on the same node)
     * @param childNode  where the form stores its values, relative to the template's node
     */
    static void collect(FormDefinition<?> form, boolean i18nParent, String childNode, List<TranslatableField> out) {
        for (EditorPropertyDefinition property : form.getProperties()) {
            boolean i18n = i18nParent || property.isI18n();
            if (property instanceof RichTextFieldDefinition) {
                if (i18n) {
                    out.add(new TranslatableField(property.getName(), true, childNode));
                }
            } else if (property instanceof TextFieldDefinition) {
                if (i18n) {
                    out.add(new TranslatableField(property.getName(), false, childNode));
                }
            } else if (property instanceof CompositeFieldDefinition<?> composite) {
                collectComposite(composite, i18n, childNode, out);
            }
        }
    }

    private static void collectComposite(CompositeFieldDefinition<?> composite, boolean i18n, String childNode, List<TranslatableField> out) {
        Class<?> decorator = composite.getPropertyNameDecorator();
        if (decorator != null && !NoopNameDecorator.class.equals(decorator)) {
            log.debug("Supertext: composite field {} uses a property name decorator; not translated", composite.getName());
            return;
        }
        Object provider = composite.getItemProvider();
        if (provider instanceof CurrentItemProviderDefinition) {
            // Sub-fields are stored on the same node under their own names.
            collect(composite, i18n, childNode, out);
        } else if (provider instanceof NestedContentProvider.Definition || provider instanceof JcrChildNodeProviderDefinition) {
            if (composite.isI18n()) {
                // One child node per language (teaser, teaser_de_CH): not supported yet.
                log.debug("Supertext: i18n composite field {} with nested content is not translated", composite.getName());
                return;
            }
            String nodeName = provider instanceof JcrChildNodeProviderDefinition child && child.getNodeName() != null
                    ? child.getNodeName() : composite.getName();
            collect(composite, false, childNode.isEmpty() ? nodeName : childNode + "/" + nodeName, out);
        }
    }

    static boolean looksLikeHtml(String value) {
        String v = value == null ? "" : value.trim();
        return v.startsWith("<") && v.endsWith(">");
    }
}
