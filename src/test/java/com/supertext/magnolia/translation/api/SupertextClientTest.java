package com.supertext.magnolia.translation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Runs the client against a local stand-in of the Supertext API. */
class SupertextClientTest {

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private final List<Long> sleeps = new CopyOnWriteArrayList<>();
    private volatile String submittedBody = "";
    private final AtomicInteger rateLimited = new AtomicInteger();
    private volatile int statusPolls;
    private volatile int forcedStatus;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", this::handle);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring("/v1/".length());
        calls.add(exchange.getRequestMethod() + " " + path);
        authHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
        if (forcedStatus != 0) {
            reply(exchange, forcedStatus, "{\"message\":\"<b>nope</b>\"}");
            return;
        }
        if (rateLimited.getAndDecrement() > 0) {
            exchange.getResponseHeaders().add("Retry-After", "1");
            reply(exchange, 429, "{\"code\":\"RATE_LIMIT_EXCEEDED\"}");
            return;
        }
        switch (exchange.getRequestMethod() + " " + path) {
            case "GET features" -> reply(exchange, 200, "{}");
            case "POST translate/ai/file" -> {
                submittedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                reply(exchange, 200, "{\"file_id\":\"f1\"}");
            }
            case "GET translate/ai/file/f1/status" -> reply(exchange, 200, statusPolls++ == 0 ? "{\"status\":\"processing\"}" : "{\"status\":\"done\"}");
            case "GET translate/ai/file/f1/translation" -> reply(exchange, 200, "<div data-st-id=\"0\">Hallo</div>");
            case "DELETE translate/ai/file/f1" -> reply(exchange, 200, "{}");
            default -> reply(exchange, 404, "");
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private SupertextClient client() {
        return new SupertextClient(HttpClient.newHttpClient(), sleeps::add);
    }

    private SupertextConnection connection(String key) {
        return new SupertextConnection("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", key, 100, 30);
    }

    @Test
    void translatesWithSubmitPollDownloadDelete() throws Exception {
        String result = client().translateDocument(connection("secret"), "<div data-st-id=\"0\">Hello</div>", "de-CH", "en_US", "more");

        assertEquals("<div data-st-id=\"0\">Hallo</div>", result);
        assertEquals(List.of("POST translate/ai/file", "GET translate/ai/file/f1/status", "GET translate/ai/file/f1/status",
                "GET translate/ai/file/f1/translation", "DELETE translate/ai/file/f1"), calls);
        assertTrue(submittedBody.contains("name=\"target_lang\"\r\n\r\nde-CH"));
        assertTrue(submittedBody.contains("name=\"source_lang\"\r\n\r\nen\r\n"), "source is sent as primary subtag");
        assertTrue(submittedBody.contains("name=\"politeness\"\r\n\r\nmore"));
        assertTrue(submittedBody.contains("filename=\"content.html\"\r\nContent-Type: text/html\r\n\r\n"), "exactly text/html, no charset");
    }

    @Test
    void sendsExactlyOneAuthPrefix() throws Exception {
        client().validateApiKey(connection("  Supertext-Auth-Key abc123 "));
        assertEquals(List.of("Supertext-Auth-Key abc123"), authHeaders);
    }

    @Test
    void retriesRateLimitWithRetryAfter() throws Exception {
        rateLimited.set(2);
        client().validateApiKey(connection("k"));
        assertEquals(3, calls.size());
        assertEquals(List.of(1000L, 1000L), sleeps);
    }

    @Test
    void givesUpAfterFourRateLimitRetries() {
        rateLimited.set(10);
        SupertextException e = assertThrows(SupertextException.class, () -> client().validateApiKey(connection("k")));
        assertEquals(1 + SupertextClient.RATE_LIMIT_RETRIES, calls.size());
        assertTrue(e.getMessage().startsWith("Too many requests"));
        assertEquals(SupertextClient.KEY_PREFIX + "rateLimit", e.key());
    }

    @Test
    void authenticationFailureMentionsWhereToGetAKey() {
        forcedStatus = 401;
        SupertextException e = assertThrows(SupertextException.class, () -> client().validateApiKey(connection("bad")));
        assertTrue(e.isAuthenticationProblem());
        assertTrue(e.getMessage().contains(SupertextLinks.SIGNUP_URL));
        assertTrue(e.getMessage().contains(SupertextLinks.API_KEY_URL));
        assertTrue(e.getMessage().contains("\"message\":\"nope\""), "detail without tags: " + e.getMessage());
        // The UI translates the key and appends the detail.
        assertEquals(SupertextClient.KEY_PREFIX + "authentication", e.key());
        assertTrue(e.detail().contains("\"message\":\"nope\""), e.detail());
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        SupertextException e = assertThrows(SupertextException.class, () -> client().validateApiKey(connection(" ")));
        assertTrue(e.isAuthenticationProblem());
        assertTrue(e.getMessage().contains(SupertextLinks.API_KEY_URL));
        assertEquals(List.of(), calls);
    }

    @Test
    void endpointMustBeHttpsExceptLocalhost() throws Exception {
        assertThrows(SupertextException.class, () -> SupertextClient.checkEndpoint("http://api.example.com/v1/"));
        SupertextClient.checkEndpoint("https://api.supertext.com/v1");
        SupertextClient.checkEndpoint("http://localhost:8765/v1/");
        assertEquals("https://api.supertext.com/v1/", SupertextClient.baseUri("").toString());
    }

    @Test
    void sourceLanguageIsPrimarySubtag() {
        assertEquals("de", SupertextClient.sourceLanguageCode("de_CH"));
        assertEquals("fr", SupertextClient.sourceLanguageCode("fr-CH"));
        assertEquals("", SupertextClient.sourceLanguageCode(null));
    }
}
