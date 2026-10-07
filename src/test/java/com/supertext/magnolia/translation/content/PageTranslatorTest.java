package com.supertext.magnolia.translation.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import info.magnolia.cms.i18n.DefaultI18nContentSupport;
import info.magnolia.cms.i18n.LocaleDefinition;
import info.magnolia.test.mock.jcr.MockSession;

import com.supertext.magnolia.translation.api.SupertextException;

class PageTranslatorTest {

    private static final Locale DE = new Locale("de", "CH");
    private static final Locale FR = new Locale("fr", "CH");

    private SiteLanguages languages;
    private Node page;
    private Node text;
    private final List<String> sent = new ArrayList<>();
    private final List<Node> touched = new ArrayList<>();

    /** page: title, text component with rich text, a subpage. */
    @BeforeEach
    void setUp() throws Exception {
        DefaultI18nContentSupport i18n = new DefaultI18nContentSupport();
        i18n.setEnabled(true);
        i18n.setFallbackLocale(Locale.ENGLISH);
        i18n.addLocale(LocaleDefinition.make("en", null, true));
        i18n.addLocale(LocaleDefinition.make("de", "CH", true));
        i18n.addLocale(LocaleDefinition.make("fr", "CH", true));
        languages = new SiteLanguages(i18n);

        MockSession session = new MockSession("website");
        page = session.getRootNode().addNode("home", "mgnl:page");
        page.setProperty("mgnl:template", "demo:pages/page");
        page.setProperty("title", "Welcome");
        page.setProperty("hideInNav", "true");
        Node main = page.addNode("main", "mgnl:area");
        text = main.addNode("0", "mgnl:component");
        text.setProperty("mgnl:template", "demo:components/text");
        text.setProperty("text", "<p>Hello <b>world</b></p>");
        text.setProperty("headline", "  ");
        text.addNode("teaser", "mgnl:contentNode").setProperty("teaserTitle", "Read more");
        Node sub = page.addNode("about", "mgnl:page");
        sub.setProperty("mgnl:template", "demo:pages/page");
        sub.setProperty("title", "About us");
    }

    private static final FieldResolver FIELDS = node -> {
        String template = node.hasProperty("mgnl:template") ? node.getProperty("mgnl:template").getString() : "";
        return switch (template) {
            case "demo:pages/page" -> List.of(new TranslatableField("title", false));
            case "demo:components/text" -> List.of(new TranslatableField("headline", false), new TranslatableField("text", true),
                    new TranslatableField("teaserTitle", false, "teaser"), new TranslatableField("missing", false, "nowhere"));
            default -> List.of();
        };
    };

    /** Stand-in for Supertext: prefixes every segment with the language. */
    private PageTranslator translator(Set<String> excluded) {
        return new PageTranslator(languages, FIELDS, (html, target, source) -> {
            sent.add(target + ":" + html);
            assertEquals(Locale.ENGLISH, source);
            return html.replaceAll("(<div data-st-id=\"\\d+\">)", "$1[" + target.getLanguage() + "] ");
        }, touched::add, excluded);
    }

    @Test
    void translatesPageAndComponentsIntoLocalizedProperties() throws Exception {
        PageTranslator.Report report = translator(Set.of()).translate(new PageTranslator.Request(page, List.of(DE, FR), false, false));

        assertEquals("[de] Welcome", page.getProperty("title_de_CH").getString());
        assertEquals("[fr] Welcome", page.getProperty("title_fr_CH").getString());
        assertEquals("[de] <p>Hello <b>world</b></p>", text.getProperty("text_de_CH").getString());
        assertEquals("Welcome", page.getProperty("title").getString(), "source untouched");
        assertFalse(text.hasProperty("headline_de_CH"), "blank texts are skipped");
        assertFalse(page.getNode("about").hasProperty("title_de_CH"), "subpages only when asked");
        assertEquals(1, report.pages());
        assertEquals("[de] Read more", text.getNode("teaser").getProperty("teaserTitle_de_CH").getString(), "nested composite");
        assertEquals(6, report.translated());
        assertEquals(2, sent.size(), "one document per language");
        assertTrue(touched.contains(page) && touched.contains(text), "changed nodes are marked modified");
    }

    @Test
    void keepsExistingTranslationsUnlessOverwrite() throws Exception {
        page.setProperty("title_de_CH", "Willkommen (edited)");

        PageTranslator.Report report = translator(Set.of()).translate(new PageTranslator.Request(page, List.of(DE), false, false));
        assertEquals("Willkommen (edited)", page.getProperty("title_de_CH").getString());
        assertEquals(1, report.languages().get(0).kept());
        assertEquals(2, report.languages().get(0).translated(), "rich text and teaser");

        translator(Set.of()).translate(new PageTranslator.Request(page, List.of(DE), true, false));
        assertEquals("[de] Welcome", page.getProperty("title_de_CH").getString());
    }

    @Test
    void includesSubpagesWhenAsked() throws Exception {
        PageTranslator.Report report = translator(Set.of()).translate(new PageTranslator.Request(page, List.of(DE), false, true));
        assertEquals("[de] About us", page.getNode("about").getProperty("title_de_CH").getString());
        assertEquals(2, report.pages());
    }

    @Test
    void excludedPropertiesAreNotSent() throws Exception {
        translator(Set.of("title")).translate(new PageTranslator.Request(page, List.of(DE), false, false));
        assertFalse(page.hasProperty("title_de_CH"));
        assertFalse(sent.get(0).contains("Welcome"));
    }

    @Test
    void sourceLanguageIsNeverATarget() throws Exception {
        PageTranslator.Report report = translator(Set.of()).translate(new PageTranslator.Request(page, List.of(Locale.ENGLISH), false, false));
        assertEquals(0, report.languages().size());
        assertEquals(List.of(), sent);
    }

    @Test
    void oneFailingLanguageDoesNotStopTheOthers() throws Exception {
        PageTranslator t = new PageTranslator(languages, FIELDS, (html, target, source) -> {
            if (target.equals(DE)) {
                throw new SupertextException("The Supertext service is currently unavailable.");
            }
            return html.replace("Welcome", "Bienvenue");
        }, n -> { }, Set.of());

        PageTranslator.Report report = t.translate(new PageTranslator.Request(page, List.of(DE, FR), false, false));
        assertTrue(report.anyFailed());
        assertEquals("The Supertext service is currently unavailable.", report.languages().get(0).error());
        assertEquals("Bienvenue", page.getProperty("title_fr_CH").getString());
    }

    @Test
    void rejectedKeyAbortsEverything() {
        PageTranslator t = new PageTranslator(languages, FIELDS, (html, target, source) -> {
            throw new SupertextException("Authentication failed.", true, null);
        }, n -> { }, Set.of());
        assertThrows(SupertextException.class, () -> t.translate(new PageTranslator.Request(page, List.of(DE, FR), false, false)));
    }

    @Test
    void largePagesAreSplitIntoSeveralDocuments() throws Exception {
        PageTranslator t = new PageTranslator(languages, FIELDS, (html, target, source) -> {
            sent.add(html);
            return html;
        }, n -> { }, Set.of(), 60);
        t.translate(new PageTranslator.Request(page, List.of(DE), false, false));
        assertEquals(3, sent.size(), "each segment is too big to share a document");
        assertEquals("Welcome", page.getProperty("title_de_CH").getString());
        assertEquals("<p>Hello <b>world</b></p>", text.getProperty("text_de_CH").getString());
    }

    @Test
    void siteLanguagesNameProperties() {
        assertEquals("title", languages.propertyName("title", Locale.ENGLISH));
        assertEquals("title_de_CH", languages.propertyName("title", DE));
        assertEquals(List.of(DE, FR), languages.targetLocales());
        assertEquals(Map.of("de_CH", DE).get("de_CH"), languages.byId("de_CH").orElseThrow());
        assertTrue(languages.isEnabled());
    }
}
