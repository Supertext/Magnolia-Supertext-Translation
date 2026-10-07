package com.supertext.magnolia.translation.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Supertext AI file translation (https://api.supertext.com/v1/), the same protocol as the
 * WordPress, TYPO3, Orchard Core and the other Supertext integrations: submit one HTML
 * document, poll its status, download the translation, delete the file.
 *
 * <p>Plain JDK HTTP client, no Magnolia dependencies, so it is unit-tested on its own.</p>
 */
public class SupertextClient {

    public static final String DEFAULT_ENDPOINT = "https://api.supertext.com/v1/";

    /** Documents above this size are split into several requests. */
    public static final int MAX_DOCUMENT_CHARACTERS = 900_000;

    /** Retries after HTTP 429 (requests per second are limited per key). */
    static final int RATE_LIMIT_RETRIES = 4;

    private static final Pattern AUTH_PREFIX = Pattern.compile("^\\s*Supertext-Auth-Key\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Waits between retries and polls; replaced in tests. */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final HttpClient http;
    private final Sleeper sleeper;

    public SupertextClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build(), Thread::sleep);
    }

    public SupertextClient(HttpClient http, Sleeper sleeper) {
        this.http = http;
        this.sleeper = sleeper;
    }

    /**
     * Translates one HTML document.
     *
     * @param targetLanguage BCP-47 code, e.g. {@code de-CH}
     * @param sourceLanguage locale of the source ({@code en_US} or {@code en-US}); sent as its primary subtag, empty for auto-detection
     * @param politeness     {@code default}, {@code more} or {@code less}
     */
    public String translateDocument(SupertextConnection connection, String html, String targetLanguage, String sourceLanguage, String politeness)
            throws SupertextException, InterruptedException {
        String fileId = submit(connection, html, targetLanguage, sourceLanguage, politeness);
        try {
            waitUntilDone(connection, fileId);
            return download(connection, fileId);
        } finally {
            deleteQuietly(connection, fileId);
        }
    }

    /** Cost-free check of the API key. */
    public void validateApiKey(SupertextConnection connection) throws SupertextException, InterruptedException {
        send(connection, "GET", "features", null, null);
    }

    /** Removes a pasted "Supertext-Auth-Key " prefix (Supertext shows the key with it). */
    public static String normalizeApiKey(String apiKey) {
        return apiKey == null ? "" : AUTH_PREFIX.matcher(apiKey).replaceFirst("").trim();
    }

    /** Supertext expects the source as a primary subtag ("de", not "de-CH"). */
    public static String sourceLanguageCode(String locale) {
        if (locale == null || locale.isBlank()) {
            return "";
        }
        return locale.trim().split("[-_]")[0].toLowerCase(Locale.ROOT);
    }

    private String submit(SupertextConnection connection, String html, String targetLanguage, String sourceLanguage, String politeness)
            throws SupertextException, InterruptedException {
        String boundary = "----SupertextMagnolia" + UUID.randomUUID().toString().replace("-", "");
        Multipart form = new Multipart(boundary);
        form.field("target_lang", targetLanguage);
        String source = sourceLanguageCode(sourceLanguage);
        if (!source.isEmpty()) {
            form.field("source_lang", source);
        }
        if ("more".equals(politeness) || "less".equals(politeness)) {
            form.field("politeness", politeness);
        }
        // The part's Content-Type must be exactly "text/html" (no charset), otherwise 415.
        form.file("file", "content.html", "text/html", html.getBytes(StandardCharsets.UTF_8));
        byte[] body = form.finish();

        HttpResponse<String> response = send(connection, "POST", "translate/ai/file", body, "multipart/form-data; boundary=" + boundary);
        String fileId = readJson(response.body()).map(json -> json.path("file_id").asText("")).orElse("");
        if (fileId.isEmpty()) {
            throw new SupertextException("Supertext did not return a file id.");
        }
        return fileId;
    }

    private void waitUntilDone(SupertextConnection connection, String fileId) throws SupertextException, InterruptedException {
        long deadline = System.currentTimeMillis() + Math.max(10, connection.pollTimeoutSeconds()) * 1000L;
        long interval = Math.max(100, connection.pollIntervalMillis());
        do {
            HttpResponse<String> response = send(connection, "GET", "translate/ai/file/" + encode(fileId) + "/status", null, null);
            String status = readJson(response.body()).map(json -> json.path("status").asText("")).orElse("");
            switch (status) {
                case "done":
                    return;
                case "error":
                    throw new SupertextException("Supertext failed to translate the document.");
                case "limit_exceeded":
                    throw new SupertextException("Your Supertext translation limit is exceeded.");
                case "deleted":
                    throw new SupertextException("The Supertext file was deleted before it could be downloaded.");
                default:
                    sleeper.sleep(interval);
            }
        } while (System.currentTimeMillis() < deadline);
        throw new SupertextException("Timed out waiting for the Supertext translation.");
    }

    private String download(SupertextConnection connection, String fileId) throws SupertextException, InterruptedException {
        HttpResponse<String> response = send(connection, "GET", "translate/ai/file/" + encode(fileId) + "/translation", null, null);
        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new SupertextException("The translated document was empty.");
        }
        return body;
    }

    private void deleteQuietly(SupertextConnection connection, String fileId) {
        try {
            send(connection, "DELETE", "translate/ai/file/" + encode(fileId), null, null);
        } catch (SupertextException e) {
            // Files expire after 24 h anyway.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpResponse<String> send(SupertextConnection connection, String method, String path, byte[] body, String contentType)
            throws SupertextException, InterruptedException {
        String apiKey = normalizeApiKey(connection.apiKey());
        if (apiKey.isEmpty()) {
            throw new SupertextException("No Supertext API key configured (Translation → Supertext, or the SUPERTEXT_API_KEY environment variable). "
                    + SupertextLinks.PLAIN_TEXT_HINT, true, null);
        }
        URI uri = baseUri(connection.endpoint()).resolve(path);

        HttpResponse<String> response;
        for (int attempt = 0; ; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    // Exactly one prefix, header name "Authorization" ("Authentication" gets 403).
                    .header("Authorization", "Supertext-Auth-Key " + apiKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Supertext-Magnolia/" + version());
            if (body != null) {
                request.header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new SupertextException("Could not reach Supertext: " + e.getMessage(), e);
            }
            if (response.statusCode() != 429 || attempt >= RATE_LIMIT_RETRIES) {
                break;
            }
            // Rate limited: wait (Retry-After, else 1, 2, 4, 8 s with jitter) and retry.
            long delay = retryAfterMillis(response).orElse((long) (1000 * Math.pow(2, attempt)) + ThreadLocalRandom.current().nextLong(250));
            sleeper.sleep(Math.min(delay, 30_000));
        }

        int code = response.statusCode();
        if (code >= 200 && code < 300) {
            return response;
        }
        String message;
        boolean authentication = false;
        if (code == 401 || code == 403) {
            message = "Authentication failed. Please check the Supertext API key. " + SupertextLinks.PLAIN_TEXT_HINT;
            authentication = true;
        } else if (code == 404) {
            message = "The requested Supertext resource was not found.";
        } else if (code == 413) {
            message = "The content is too large for Supertext to translate in one go.";
        } else if (code == 429) {
            message = "Too many requests to Supertext. Please try again shortly.";
        } else if (code >= 500) {
            message = "The Supertext service is currently unavailable.";
        } else {
            message = "Supertext answered with HTTP " + code + ".";
        }
        String detail = TAGS.matcher(response.body() == null ? "" : response.body()).replaceAll("").trim();
        if (!detail.isEmpty()) {
            message += " (" + (detail.length() > 200 ? detail.substring(0, 200) : detail) + ")";
        }
        throw new SupertextException(message, authentication, null);
    }

    /** Throws when the endpoint is not https (http is allowed for localhost only). */
    public static void checkEndpoint(String endpoint) throws SupertextException {
        baseUri(endpoint);
    }

    static URI baseUri(String endpoint) throws SupertextException {
        String value = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint.trim();
        if (!value.endsWith("/")) {
            value += "/";
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost();
            boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1")
                    || host.endsWith(".localhost") || host.equals("host.docker.internal");
            if (!scheme.equals("https") && !(scheme.equals("http") && local)) {
                throw new SupertextException("The Supertext endpoint must use https (http is only allowed for localhost): " + value);
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new SupertextException("The Supertext endpoint is not a valid URL: " + value, e);
        }
    }

    private static Optional<Long> retryAfterMillis(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                return Optional.of(Math.max(0, Long.parseLong(value.trim())) * 1000);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static Optional<JsonNode> readJson(String body) {
        try {
            return body == null || body.isBlank() ? Optional.empty() : Optional.ofNullable(JSON.readTree(body));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** The module version (from the jar manifest), for the User-Agent and the settings screen. */
    public static String version() {
        String version = SupertextClient.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    /** Minimal multipart/form-data writer with quoted part names, as browsers send them. */
    private static final class Multipart {
        private final String boundary;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Multipart(String boundary) {
            this.boundary = boundary;
        }

        void field(String name, String value) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
            write(value);
            write("\r\n");
        }

        void file(String name, String fileName, String type, byte[] content) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: " + type + "\r\n\r\n");
            out.writeBytes(content);
            write("\r\n");
        }

        byte[] finish() {
            write("--" + boundary + "--\r\n");
            return out.toByteArray();
        }

        private void write(String text) {
            out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
