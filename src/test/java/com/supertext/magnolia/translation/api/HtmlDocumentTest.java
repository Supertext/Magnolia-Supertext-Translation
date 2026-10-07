package com.supertext.magnolia.translation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HtmlDocumentTest {

    @Test
    void plainTextIsEscapedAndLineBreaksSurvive() {
        List<HtmlDocument.Segment> segments = List.of(new HtmlDocument.Segment("Fish & chips <cheap>\nsecond line", false));
        String html = HtmlDocument.build(segments);

        assertTrue(html.contains("<div data-st-id=\"0\">Fish &amp; chips &lt;cheap&gt;<br>second line</div>"));
        assertEquals("Fish & chips <cheap>\nsecond line", HtmlDocument.parse(html, segments).get(0));
    }

    @Test
    void richTextIsSentAsOneSegmentAndComesBackAsHtml() {
        String rich = "<h2>One click</h2>\n<p>Supertext uses <strong>professional</strong> <a href=\"${link:{uuid:{abc},repository:{website}}}\">links</a>.</p>";
        List<HtmlDocument.Segment> segments = List.of(new HtmlDocument.Segment(rich, true));
        String html = HtmlDocument.build(segments);
        assertEquals(1, html.split("data-st-id").length - 1, "the whole field is one segment");

        String translated = html.replace("One click", "Ein Klick").replace("uses", "verwendet");
        String back = HtmlDocument.parse(translated, segments).get(0);
        assertEquals("<h2>Ein Klick</h2>\n<p>Supertext verwendet <strong>professional</strong> <a href=\"${link:{uuid:{abc},repository:{website}}}\">links</a>.</p>", back);
    }

    @Test
    void umlautsStayLiteral() {
        List<HtmlDocument.Segment> segments = List.of(new HtmlDocument.Segment("<p>x</p>", true));
        Map<Integer, String> parsed = HtmlDocument.parse("<div data-st-id=\"0\"><p>Übersetzen – «schön»</p></div>", segments);
        assertEquals("<p>Übersetzen – «schön»</p>", parsed.get(0));
    }

    @Test
    void whitespaceSupertextAddsIsCollapsedInPlainText() {
        List<HtmlDocument.Segment> segments = List.of(new HtmlDocument.Segment("a", false), new HtmlDocument.Segment("b\nc", false));
        Map<Integer, String> parsed = HtmlDocument.parse("<body><div data-st-id=\"0\">\n  Hallo\n  Welt </div><div data-st-id=\"1\">b <br/> c</div></body>", segments);
        assertEquals("Hallo Welt", parsed.get(0));
        assertEquals("b\nc", parsed.get(1));
    }

    @Test
    void missingAndUnknownSegmentsAreIgnored() {
        List<HtmlDocument.Segment> segments = List.of(new HtmlDocument.Segment("a", false), new HtmlDocument.Segment("b", false));
        Map<Integer, String> parsed = HtmlDocument.parse("<div data-st-id=\"1\">B</div><div data-st-id=\"7\">X</div><div data-st-id=\"x\">Y</div>", segments);
        assertFalse(parsed.containsKey(0));
        assertEquals("B", parsed.get(1));
        assertEquals(1, parsed.size());
    }
}
