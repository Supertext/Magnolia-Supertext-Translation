package com.supertext.magnolia.translation;

/**
 * Module settings as stored in {@code config:/modules/supertext-translation/config} (edited in
 * the <em>Supertext</em> app, app launcher → Translation). Also the Magnolia module class. Read
 * the settings in effect through {@link SupertextSettings}, which loads this node on every use
 * and applies the {@code SUPERTEXT_API_KEY} / {@code SUPERTEXT_API_ENDPOINT} environment variables.
 */
public class SupertextTranslationModule {

    private String apiKey = "";
    private String endpoint = "";
    private String politeness = "default";
    private int pollTimeoutSeconds = 300;
    private String languageMapping = "";
    private String excludedProperties = "";
    private String additionalProperties = "";

    /** API key, with or without the {@code Supertext-Auth-Key } prefix. */
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /** API base URL; empty means https://api.supertext.com/v1/. */
    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /** Form of address: {@code default}, {@code more} (formal) or {@code less} (informal). */
    public String getPoliteness() {
        return politeness;
    }

    public void setPoliteness(String politeness) {
        this.politeness = politeness;
    }

    /** How long to wait for Supertext per document. */
    public int getPollTimeoutSeconds() {
        return pollTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(int pollTimeoutSeconds) {
        this.pollTimeoutSeconds = pollTimeoutSeconds;
    }

    /** One {@code magnoliaLocale=supertextCode} per line, e.g. {@code de=de-CH}. */
    public String getLanguageMapping() {
        return languageMapping;
    }

    public void setLanguageMapping(String languageMapping) {
        this.languageMapping = languageMapping;
    }

    /** Property names never translated (comma or line separated). */
    public String getExcludedProperties() {
        return excludedProperties;
    }

    public void setExcludedProperties(String excludedProperties) {
        this.excludedProperties = excludedProperties;
    }

    /**
     * Extra property names to translate even when no dialog marks them {@code i18n: true}
     * (e.g. content created by import scripts). Comma or line separated.
     */
    public String getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(String additionalProperties) {
        this.additionalProperties = additionalProperties;
    }
}
