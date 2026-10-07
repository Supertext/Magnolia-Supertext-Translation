package com.supertext.magnolia.translation.api;

/**
 * Where and how to reach Supertext (resolved from the module settings and the environment).
 *
 * @param endpoint            API base URL, e.g. {@code https://api.supertext.com/v1/}
 * @param apiKey              the key, with or without the {@code Supertext-Auth-Key } prefix
 * @param pollIntervalMillis  time between status checks
 * @param pollTimeoutSeconds  how long to wait for one document
 */
public record SupertextConnection(String endpoint, String apiKey, long pollIntervalMillis, int pollTimeoutSeconds) {

    public SupertextConnection(String endpoint, String apiKey) {
        this(endpoint, apiKey, 2000, 300);
    }
}
