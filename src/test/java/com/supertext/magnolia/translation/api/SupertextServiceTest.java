package com.supertext.magnolia.translation.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.supertext.sdk.ErrorCode;
import com.supertext.sdk.Segment;
import com.supertext.sdk.Transport;

/** The adapter on the SDK, against a fake transport that behaves like Supertext. */
class SupertextServiceTest {

    private static final SupertextConnection CONNECTION =
            new SupertextConnection("https://api.example.test/v1", "Supertext-Auth-Key secret-key", 100, 30);

    /** Requests seen; answers queued per path suffix, otherwise Supertext's normal answers. */
    private final List<Transport.Request> requests = new ArrayList<>();
    private final Deque<Object> uploadAnswers = new ArrayDeque<>();
    private final List<Duration> sleeps = new ArrayList<>();
    private String translationPrefix = "[de] ";
    private boolean dropSecondSegment;

    private final Transport fake = request -> {
        requests.add(request);
        String path = request.getUri().getPath();
        if (request.getMethod().equals("POST")) {
            Object answer = uploadAnswers.poll();
            if (answer instanceof IOException e) {
                throw e;
            }
            if (answer instanceof Transport.Response r) {
                return r;
            }
            return json(200, "{\"file_id\":\"f-1\"}");
        }
        if (path.endsWith("/status")) {
            return json(200, "{\"status\":\"done\"}");
        }
        if (path.endsWith("/translation")) {
            String html = uploadedHtml(requests.get(0));
            String translated = html.replaceAll("(<div data-st-id=\"\\d+\">)", "$1" + translationPrefix);
            if (dropSecondSegment) {
                translated = translated.replaceAll("<div data-st-id=\"1\">[^\n]*</div>\n", "");
            }
            return new Transport.Response(200, Map.of("Content-Type", "text/html"), translated);
        }
        if (path.equals("/v1/features")) {
            return json(200, "{}");
        }
        return new Transport.Response(204, Map.of(), ""); // DELETE
    };

    private final SupertextService service = new SupertextService(fake, sleeps::add);

    private static Transport.Response json(int status, String body) {
        return new Transport.Response(status, Map.of("Content-Type", "application/json"), body);
    }

    private static String body(Transport.Request request) {
        return new String(request.getBody(), StandardCharsets.UTF_8);
    }

    private static String uploadedHtml(Transport.Request upload) {
        String body = body(upload);
        int start = body.indexOf("<!DOCTYPE html>");
        return body.substring(start, body.indexOf("\r\n--", start));
    }

    private static Map<String, Segment> texts() {
        Map<String, Segment> texts = new LinkedHashMap<>();
        texts.put("/home@title", Segment.text("Welcome & hello"));
        texts.put("/home/main/0@text", Segment.html("<p>Hello <b>world</b></p>"));
        return texts;
    }

    @Test
    void translatesTextsThroughTheSdk() throws Exception {
        Map<String, String> result = service.translateTexts(CONNECTION, texts(), "de-CH", "en", "more");

        assertEquals(Map.of("/home@title", "[de] Welcome & hello", "/home/main/0@text", "[de] <p>Hello <b>world</b></p>"), result);
        assertEquals(List.of("POST", "GET", "GET", "DELETE"), requests.stream().map(Transport.Request::getMethod).toList());
        Transport.Request upload = requests.get(0);
        assertEquals("https://api.example.test/v1/translate/ai/file", upload.getUri().toString());
        assertEquals("Supertext-Auth-Key secret-key", upload.getHeaders().get("Authorization"), "pasted prefix sent once");
        assertTrue(upload.getHeaders().get("User-Agent").endsWith(" Supertext-Magnolia/dev"), upload.getHeaders().get("User-Agent"));
        String form = body(upload);
        assertTrue(form.contains("name=\"target_lang\"\r\n\r\nde-CH\r\n"), form);
        assertTrue(form.contains("name=\"source_lang\"\r\n\r\nen\r\n"), form);
        assertTrue(form.contains("name=\"politeness\"\r\n\r\nmore\r\n"), form);
        assertTrue(form.contains("<div data-st-id=\"0\">Welcome &amp; hello</div>"), "plain text is escaped: " + form);
        assertTrue(form.contains("<div data-st-id=\"1\"><p>Hello <b>world</b></p></div>"), "rich text as one segment: " + form);
    }

    @Test
    void emptySourceAndDefaultPolitenessAreNotSent() throws Exception {
        service.translateTexts(CONNECTION, texts(), "de-CH", "", "default");
        String form = body(requests.get(0));
        assertFalse(form.contains("source_lang"), form);
        assertFalse(form.contains("politeness"), form);
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        SupertextException e = assertThrows(SupertextException.class,
                () -> service.translateTexts(new SupertextConnection("", "  "), texts(), "de-CH", "en", "default"));
        assertEquals("supertext-translation.error.noApiKey", e.key());
        assertTrue(e.isAuthenticationProblem());
        assertTrue(e.getMessage().contains(SupertextLinks.API_KEY_URL));
        assertEquals(List.of(), requests);
    }

    @Test
    void rejectedKeyIsAnAuthenticationProblemWithTheLinks() {
        uploadAnswers.add(json(401, "{\"message\":\"Invalid key\"}"));
        SupertextException e = assertThrows(SupertextException.class,
                () -> service.translateTexts(CONNECTION, texts(), "de-CH", "en", "default"));
        assertEquals("supertext-translation.error.authentication", e.key());
        assertTrue(e.isAuthenticationProblem());
        assertTrue(e.getMessage().contains(SupertextLinks.SIGNUP_URL) && e.getMessage().contains(SupertextLinks.API_KEY_URL));
        assertFalse(e.getMessage().contains("secret-key"));
    }

    @Test
    void invalidLanguagePairNamesTheCodes() {
        uploadAnswers.add(json(400, "{\"error_code\":\"INVALID_LANGUAGE_PAIR\",\"source_lang\":\"en\",\"target_lang\":\"xx\"}"));
        SupertextException e = assertThrows(SupertextException.class,
                () -> service.translateTexts(CONNECTION, texts(), "xx", "en", "default"));
        assertEquals("supertext-translation.error.invalidLanguagePair", e.key());
        assertArrayEquals(new Object[] {"en", "xx"}, e.args());
        assertFalse(e.isAuthenticationProblem());
    }

    @Test
    void rateLimitIsRetriedWithoutWaitingForReal() throws Exception {
        uploadAnswers.add(new Transport.Response(429, Map.of("Retry-After", "2"), "RATE_LIMIT_EXCEEDED"));
        uploadAnswers.add(new Transport.Response(429, Map.of(), "RATE_LIMIT_EXCEEDED"));
        Map<String, String> result = service.translateTexts(CONNECTION, texts(), "de-CH", "en", "default");
        assertEquals(2, result.size());
        assertEquals(Duration.ofSeconds(2), sleeps.get(0), "Retry-After");
        assertEquals(3, requests.stream().filter(r -> r.getMethod().equals("POST")).count());
    }

    @Test
    void unreachableShowsTheReason() {
        uploadAnswers.add(new ConnectException("Connection refused"));
        SupertextException e = assertThrows(SupertextException.class,
                () -> service.translateTexts(CONNECTION, texts(), "de-CH", "en", "default"));
        assertEquals("supertext-translation.error.unreachable", e.key());
        assertArrayEquals(new Object[] {"Connection refused"}, e.args());
        assertEquals("", e.detail(), "not repeated in brackets");
    }

    @Test
    void otherStatusesKeepTheirNumberAndAnswer() {
        uploadAnswers.add(new Transport.Response(418, Map.of(), "<p>I'm a teapot</p>"));
        SupertextException e = assertThrows(SupertextException.class,
                () -> service.translateTexts(CONNECTION, texts(), "de-CH", "en", "default"));
        assertEquals("supertext-translation.error.http", e.key());
        assertArrayEquals(new Object[] {"418"}, e.args());
        assertEquals("I'm a teapot", e.detail());
    }

    @Test
    void anIncompleteAnswerFailsTheLanguage() {
        dropSecondSegment = true;
        SupertextException e = assertThrows(SupertextException.class,
                () -> service.translateTexts(CONNECTION, texts(), "de-CH", "en", "default"));
        assertEquals("supertext-translation.error.incomplete", e.key());
        assertEquals("missing: /home/main/0@text", e.detail());
        assertEquals("DELETE", requests.get(requests.size() - 1).getMethod(), "the file is deleted anyway");
    }

    @Test
    void validatesTheKeyWithTheFreeFeaturesCall() throws Exception {
        service.validateApiKey(CONNECTION);
        assertEquals("https://api.example.test/v1/features", requests.get(0).getUri().toString());
    }

    @Test
    void endpointMustBeHttpsExceptLocally() throws Exception {
        SupertextException e = assertThrows(SupertextException.class, () -> SupertextService.checkEndpoint("http://example.com/v1/"));
        assertEquals("supertext-translation.error.endpointNotHttps", e.key());
        assertThrows(SupertextException.class, () -> SupertextService.checkEndpoint("https://exa mple.com/"));
        assertDoesNotThrow(() -> SupertextService.checkEndpoint("http://localhost:8765/v1"));
        assertDoesNotThrow(() -> SupertextService.checkEndpoint("http://host.docker.internal:8765/v1/"));
        assertDoesNotThrow(() -> SupertextService.checkEndpoint(""));
        SupertextException blocked = assertThrows(SupertextException.class,
                () -> service.translateTexts(new SupertextConnection("http://example.com/v1/", "key"), texts(), "de-CH", "en", "default"));
        assertEquals("supertext-translation.error.endpointNotHttps", blocked.key());
        assertEquals(List.of(), requests, "the key never goes over plain http");
    }

    @Test
    void pastedPrefixIsRemoved() {
        assertEquals("abc", SupertextService.normalizeApiKey("  supertext-auth-key   abc "));
        assertEquals("", SupertextService.normalizeApiKey(null));
    }

    /** Every SDK error has a message key, in the English bundle (MessageBundlesTest checks the other languages). */
    @Test
    void everySdkErrorHasAMessage() throws IOException {
        Properties en = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/supertext-translation/i18n/module-supertext-translation-messages_en.properties")) {
            assertNotNull(in);
            en.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        for (ErrorCode code : ErrorCode.values()) {
            SupertextException e = SupertextService.convert(new com.supertext.sdk.SupertextException(code, "English"), "en", "de-DE");
            assertTrue(en.containsKey(e.key()), code + " → " + e.key());
            assertEquals(code == ErrorCode.MISSING_API_KEY || code == ErrorCode.AUTHENTICATION_FAILURE, e.isAuthenticationProblem(), code.value());
            assertFalse(e.key().endsWith(".http") && code != ErrorCode.UNEXPECTED_STATUS, code + " has its own message");
        }
    }
}
