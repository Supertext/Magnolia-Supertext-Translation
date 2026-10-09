package com.supertext.magnolia.translation.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import com.supertext.magnolia.translation.api.HtmlDocument;
import com.supertext.magnolia.translation.api.SupertextClient;
import com.supertext.magnolia.translation.api.SupertextException;

/**
 * Translates a page (its own properties, and those of its areas and components) from the
 * site's default language into other site languages. For every target language it collects
 * the translatable texts, sends them to Supertext as one HTML document (split when large),
 * and writes the translations into the language's properties ({@code title_de_CH}, …).
 *
 * <p>Existing translations are kept unless {@code overwrite} is set. Each language is saved
 * on its own: if one fails, the others are still saved, and the failed one is rolled back.</p>
 *
 * <p>Works on plain JCR nodes; Magnolia specifics (which fields are translatable, how a node is
 * marked modified, how Supertext is called) are passed in, so it is unit-tested with mock nodes.</p>
 */
public final class PageTranslator {

    public static final String PAGE_TYPE = "mgnl:page";
    private static final String DELETED_MIXIN = "mgnl:deleted";

    /** Sends one HTML document to Supertext and returns the translated document. */
    @FunctionalInterface
    public interface DocumentTranslator {
        String translate(String html, Locale target, Locale source) throws SupertextException, InterruptedException;
    }

    /** Marks a node as changed (Magnolia: last modified date and user). */
    @FunctionalInterface
    public interface ModificationMarker {
        void touched(Node node) throws RepositoryException;
    }

    /** What to translate. */
    public record Request(Node page, List<Locale> targets, boolean overwrite, boolean includeSubpages) {
    }

    /** Outcome for one language. {@code problem} is null when it worked. */
    public record LanguageResult(Locale locale, int translated, int kept, int unchanged, SupertextException problem) {
        public boolean failed() {
            return problem != null;
        }

        /** The problem's English message, or null when it worked. */
        public String error() {
            return problem == null ? null : problem.getMessage();
        }
    }

    /** Outcome for all languages. */
    public record Report(int pages, List<LanguageResult> languages) {
        public boolean anyFailed() {
            return languages.stream().anyMatch(LanguageResult::failed);
        }

        public int translated() {
            return languages.stream().mapToInt(LanguageResult::translated).sum();
        }
    }

    private final SiteLanguages languages;
    private final FieldResolver fields;
    private final DocumentTranslator translator;
    private final ModificationMarker marker;
    private final Set<String> excluded;
    private final int maxDocumentCharacters;

    public PageTranslator(SiteLanguages languages, FieldResolver fields, DocumentTranslator translator, ModificationMarker marker, Set<String> excluded) {
        this(languages, fields, translator, marker, excluded, SupertextClient.MAX_DOCUMENT_CHARACTERS);
    }

    PageTranslator(SiteLanguages languages, FieldResolver fields, DocumentTranslator translator, ModificationMarker marker, Set<String> excluded, int maxDocumentCharacters) {
        this.languages = languages;
        this.fields = fields;
        this.translator = translator;
        this.marker = marker;
        this.excluded = excluded == null ? Set.of() : excluded;
        this.maxDocumentCharacters = maxDocumentCharacters;
    }

    /**
     * @throws SupertextException when the API key is missing or rejected (nothing is saved then);
     *     other Supertext errors are reported per language in the {@link Report}
     */
    public Report translate(Request request) throws RepositoryException, SupertextException, InterruptedException {
        Locale source = languages.defaultLocale();
        List<Node> nodes = new ArrayList<>();
        int pages = collect(request.page(), request.includeSubpages(), nodes);

        List<LanguageResult> results = new ArrayList<>();
        for (Locale target : request.targets()) {
            if (target.equals(source)) {
                continue;
            }
            try {
                results.add(translateInto(nodes, source, target, request.overwrite()));
                request.page().getSession().save();
            } catch (SupertextException e) {
                request.page().getSession().refresh(false);
                if (e.isAuthenticationProblem()) {
                    throw e;
                }
                results.add(new LanguageResult(target, 0, 0, 0, e));
            } catch (RepositoryException | RuntimeException e) {
                request.page().getSession().refresh(false);
                throw e;
            }
        }
        return new Report(pages, results);
    }

    private LanguageResult translateInto(List<Node> nodes, Locale source, Locale target, boolean overwrite)
            throws RepositoryException, SupertextException, InterruptedException {
        List<Unit> units = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        int kept = 0;
        for (Node owner : nodes) {
            for (TranslatableField field : fields.fieldsOf(owner)) {
                if (excluded.contains(field.name())) {
                    continue;
                }
                Node node = field.childNode().isEmpty() ? owner : owner.hasNode(field.childNode()) ? owner.getNode(field.childNode()) : null;
                if (node == null || !seen.add(node.getPath() + "@" + field.name())) {
                    continue;
                }
                String text = stringValue(node, field.name());
                if (text == null || text.isBlank()) {
                    continue;
                }
                String targetName = languages.propertyName(field.name(), target);
                String existing = stringValue(node, targetName);
                if (existing != null && !existing.isBlank() && !overwrite) {
                    kept++;
                    continue;
                }
                units.add(new Unit(node, targetName, new HtmlDocument.Segment(text, field.html())));
            }
        }

        int translated = 0;
        int unchanged = 0;
        Set<Node> touched = new LinkedHashSet<>();
        for (List<Unit> chunk : chunks(units)) {
            List<HtmlDocument.Segment> segments = chunk.stream().map(Unit::segment).toList();
            String answer = translator.translate(HtmlDocument.build(segments), target, source);
            Map<Integer, String> parsed = HtmlDocument.parse(answer, segments);
            for (int i = 0; i < chunk.size(); i++) {
                String value = parsed.get(i);
                Unit unit = chunk.get(i);
                if (value == null) {
                    unchanged++; // came back damaged or missing: leave the field as it was
                    continue;
                }
                unit.node().setProperty(unit.property(), value);
                touched.add(unit.node());
                translated++;
            }
        }
        Set<Node> pagesTouched = new LinkedHashSet<>();
        for (Node node : touched) {
            marker.touched(node);
            Node page = pageOf(node);
            if (page != null && !touched.contains(page)) {
                pagesTouched.add(page);
            }
        }
        for (Node page : pagesTouched) {
            marker.touched(page);
        }
        return new LanguageResult(target, translated, kept, unchanged, null);
    }

    private List<List<Unit>> chunks(List<Unit> units) {
        List<List<Unit>> chunks = new ArrayList<>();
        List<Unit> current = new ArrayList<>();
        int size = 0;
        for (Unit unit : units) {
            int s = HtmlDocument.measure(unit.segment());
            if (!current.isEmpty() && size + s > maxDocumentCharacters) {
                chunks.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(unit);
            size += s;
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    /** Adds the page, its areas and components (and subpages if asked) to {@code out}; returns the page count. */
    private static int collect(Node page, boolean includeSubpages, List<Node> out) throws RepositoryException {
        out.add(page);
        int pages = 1;
        NodeIterator children = page.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            if (child.getName().startsWith("jcr:") || isDeleted(child)) {
                continue;
            }
            if (isPage(child)) {
                if (includeSubpages) {
                    pages += collect(child, true, out);
                }
                continue;
            }
            collectContent(child, out);
        }
        return pages;
    }

    private static void collectContent(Node node, List<Node> out) throws RepositoryException {
        out.add(node);
        NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            Node child = children.nextNode();
            if (!child.getName().startsWith("jcr:") && !isPage(child) && !isDeleted(child)) {
                collectContent(child, out);
            }
        }
    }

    static boolean isPage(Node node) throws RepositoryException {
        return node.isNodeType(PAGE_TYPE);
    }

    private static boolean isDeleted(Node node) throws RepositoryException {
        try {
            return node.isNodeType(DELETED_MIXIN);
        } catch (RepositoryException | UnsupportedOperationException e) {
            return false;
        }
    }

    private static Node pageOf(Node node) throws RepositoryException {
        Node current = node;
        while (current != null) {
            if (isPage(current)) {
                return current;
            }
            if (current.getDepth() == 0) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String stringValue(Node node, String name) throws RepositoryException {
        if (!node.hasProperty(name)) {
            return null;
        }
        Property property = node.getProperty(name);
        if (property.isMultiple() || property.getType() != PropertyType.STRING) {
            return null;
        }
        return property.getString();
    }

    private record Unit(Node node, String property, HtmlDocument.Segment segment) {
    }
}
