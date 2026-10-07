package com.supertext.magnolia.translation.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.nodes.TextNode;

/**
 * Packs many strings into one HTML document and splits the translated document back apart.
 * Supertext keeps markup and attributes and translates text nodes, so every segment travels
 * inside {@code <div data-st-id="N">…</div>}.
 *
 * <p>Plain-text segments (text fields) are escaped, line breaks sent as {@code <br>}, so they
 * survive the round trip unchanged. HTML segments (rich text fields) are sent as they are:
 * one segment per field, formatting and links as inline tags inside it, never one segment
 * per formatted run (sentences would break at the formatting). Magnolia's internal links
 * ({@code ${link:{…}}} in {@code href}) are attributes, so Supertext leaves them alone.</p>
 */
public final class HtmlDocument {

    /** One string to translate. */
    public record Segment(String text, boolean html) {
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String LINE_BREAK = "\u001E";
    private static final Pattern LINE_BREAK_SPACES = Pattern.compile(" ?" + LINE_BREAK + " ?");

    private HtmlDocument() {
    }

    public static String build(List<Segment> segments) {
        StringBuilder sb = new StringBuilder("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"></head><body>\n");
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            sb.append("<div data-st-id=\"").append(i).append("\">")
                    .append(s.html() ? s.text() : encodeText(s.text()))
                    .append("</div>\n");
        }
        return sb.append("</body></html>").toString();
    }

    /** Approximate size of a segment inside the document (for splitting large pages). */
    public static int measure(Segment s) {
        return (s.html() ? s.text().length() : encodeText(s.text()).length()) + 32;
    }

    /**
     * @return segment index → translated text. Segments missing from the answer are left out
     *     (the field then keeps what it had).
     */
    public static Map<Integer, String> parse(String html, List<Segment> segments) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        doc.outputSettings().prettyPrint(false).escapeMode(Entities.EscapeMode.base).charset("UTF-8");
        Map<Integer, String> result = new HashMap<>();
        for (Element element : doc.select("div[data-st-id]")) {
            int id;
            try {
                id = Integer.parseInt(element.attr("data-st-id").trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (id < 0 || id >= segments.size() || result.containsKey(id)) {
                continue;
            }
            if (segments.get(id).html()) {
                result.put(id, element.html().trim());
                continue;
            }
            // Plain text: <br> are the real line breaks; other whitespace (including formatting
            // newlines Supertext may add) collapses to one space.
            for (Element br : element.select("br")) {
                br.replaceWith(new TextNode(LINE_BREAK));
            }
            String text = element.wholeText(); // already decoded by the parser
            text = WHITESPACE.matcher(text.replace(' ', ' ')).replaceAll(" ");
            text = LINE_BREAK_SPACES.matcher(text).replaceAll("\n");
            result.put(id, text.strip());
        }
        return result;
    }

    static String encodeText(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder sb = new StringBuilder(normalized.length() + 16);
        for (char c : normalized.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\n' -> sb.append("<br>");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
