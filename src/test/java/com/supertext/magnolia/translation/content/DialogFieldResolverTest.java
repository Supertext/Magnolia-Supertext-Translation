package com.supertext.magnolia.translation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.magnolia.ui.editor.ConfiguredFormDefinition;
import info.magnolia.ui.field.CheckBoxFieldDefinition;
import info.magnolia.ui.field.CompositeFieldDefinition;
import info.magnolia.ui.field.LinkFieldDefinition;
import info.magnolia.ui.field.RichTextFieldDefinition;
import info.magnolia.ui.field.TextFieldDefinition;

class DialogFieldResolverTest {

    private static <T extends info.magnolia.ui.field.ConfiguredFieldDefinition<?>> T field(T definition, String name, boolean i18n) {
        definition.setName(name);
        definition.setI18n(i18n);
        return definition;
    }

    @Test
    void takesI18nTextAndRichTextFields() {
        ConfiguredFormDefinition<Object> form = new ConfiguredFormDefinition<>();
        CompositeFieldDefinition<Object> composite = new CompositeFieldDefinition<>();
        composite.setName("teaser");
        composite.setProperties(List.of(field(new TextFieldDefinition(), "teaserTitle", false), field(new RichTextFieldDefinition(), "teaserText", true)));
        form.setProperties(List.of(
                field(new TextFieldDefinition(), "title", true),
                field(new TextFieldDefinition(), "navigationTitle", false),
                field(new RichTextFieldDefinition(), "text", true),
                field(new CheckBoxFieldDefinition(), "hideInNav", true),
                field(new LinkFieldDefinition<>(), "link", true),
                composite));

        List<TranslatableField> fields = new ArrayList<>();
        DialogFieldResolver.collect(form, false, "", fields);

        assertEquals(List.of(new TranslatableField("title", false), new TranslatableField("text", true), new TranslatableField("teaserText", true, "teaser")), fields,
                "composites store in a child node named after them by default");
    }

    @Test
    void i18nCompositeMakesItsSubFieldsTranslatable() {
        ConfiguredFormDefinition<Object> form = new ConfiguredFormDefinition<>();
        CompositeFieldDefinition<Object> composite = new CompositeFieldDefinition<>();
        composite.setName("teaser");
        composite.setI18n(true);
        composite.setItemProvider(new info.magnolia.ui.editor.CurrentItemProviderDefinition<>());
        composite.setProperties(List.of(field(new TextFieldDefinition(), "teaserTitle", false)));
        form.setProperties(List.of(composite));

        List<TranslatableField> fields = new ArrayList<>();
        DialogFieldResolver.collect(form, false, "", fields);
        assertEquals(List.of(new TranslatableField("teaserTitle", false)), fields);
    }

    @Test
    void i18nCompositeWithChildNodesIsSkipped() {
        ConfiguredFormDefinition<Object> form = new ConfiguredFormDefinition<>();
        CompositeFieldDefinition<Object> composite = new CompositeFieldDefinition<>();
        composite.setName("teaser");
        composite.setI18n(true);
        composite.setProperties(List.of(field(new TextFieldDefinition(), "teaserTitle", true)));
        form.setProperties(List.of(composite));

        List<TranslatableField> fields = new ArrayList<>();
        DialogFieldResolver.collect(form, false, "", fields);
        assertEquals(List.of(), fields);
    }

    @Test
    void htmlDetection() {
        assertTrue(DialogFieldResolver.looksLikeHtml(" <p>Hi</p> "));
        assertFalse(DialogFieldResolver.looksLikeHtml("1 < 2 > 0"));
    }
}
