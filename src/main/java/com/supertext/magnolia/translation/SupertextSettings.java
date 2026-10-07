package com.supertext.magnolia.translation;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.supertext.magnolia.translation.ui.settings.SettingsStore;

import com.supertext.magnolia.translation.api.SupertextClient;
import com.supertext.magnolia.translation.api.SupertextConnection;

/**
 * The settings in effect: the module configuration (edited in the Supertext app), with the API key and endpoint
 * overridden by the {@code SUPERTEXT_API_KEY} / {@code SUPERTEXT_API_ENDPOINT} environment
 * variables (or the same names as Java system properties) when those are set.
 */
@Singleton
public class SupertextSettings {

    public static final String API_KEY_VARIABLE = "SUPERTEXT_API_KEY";
    public static final String ENDPOINT_VARIABLE = "SUPERTEXT_API_ENDPOINT";

    private final Supplier<SupertextTranslationModule> module;
    private final Function<String, String> environment;

    /**
     * Used by Magnolia: reads {@code config:/modules/supertext-translation/config} on every use,
     * so settings saved in the Supertext app apply right away (Magnolia doesn't refresh the
     * module bean when that node is created at runtime).
     */
    @Inject
    public SupertextSettings() {
        this(SettingsStore::loadModule, SupertextSettings::systemValue);
    }

    public SupertextSettings(Supplier<SupertextTranslationModule> module, Function<String, String> environment) {
        this.module = module;
        this.environment = environment;
    }

    public SupertextTranslationModule module() {
        SupertextTranslationModule m = module.get();
        return m == null ? new SupertextTranslationModule() : m;
    }

    /** The key from the environment, if any (then the stored key is not used). */
    public String apiKeyFromEnvironment() {
        return trim(environment.apply(API_KEY_VARIABLE));
    }

    public String endpointFromEnvironment() {
        return trim(environment.apply(ENDPOINT_VARIABLE));
    }

    public SupertextConnection connection() {
        SupertextTranslationModule m = module();
        String apiKey = firstNonEmpty(apiKeyFromEnvironment(), m.getApiKey());
        String endpoint = firstNonEmpty(endpointFromEnvironment(), m.getEndpoint(), SupertextClient.DEFAULT_ENDPOINT);
        int timeout = m.getPollTimeoutSeconds() > 0 ? m.getPollTimeoutSeconds() : 300;
        return new SupertextConnection(endpoint, SupertextClient.normalizeApiKey(apiKey), 2000, timeout);
    }

    public boolean hasApiKey() {
        return !connection().apiKey().isEmpty();
    }

    public String politeness() {
        String p = trim(module().getPoliteness());
        return p.equals("more") || p.equals("less") ? p : "default";
    }

    /**
     * The code sent to Supertext for a Magnolia locale: the language mapping if it has an
     * entry (matched on {@code de_CH}, {@code de-CH} or {@code de}), else the BCP-47 tag.
     */
    public String targetLanguageCode(Locale locale) {
        Map<String, String> mapping = parseMapping(module().getLanguageMapping());
        for (String key : new String[] {locale.toString(), locale.toLanguageTag(), locale.getLanguage()}) {
            String code = mapping.get(key.toLowerCase(Locale.ROOT));
            if (code != null && !code.isEmpty()) {
                return code;
            }
        }
        return locale.toLanguageTag();
    }

    public Set<String> excludedProperties() {
        return split(module().getExcludedProperties());
    }

    public Set<String> additionalProperties() {
        return split(module().getAdditionalProperties());
    }

    static Map<String, String> parseMapping(String value) {
        Map<String, String> mapping = new java.util.HashMap<>();
        for (String line : (value == null ? "" : value).split("[\\n,;]")) {
            String[] pair = line.split("=", 2);
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                mapping.put(pair[0].trim().toLowerCase(Locale.ROOT), pair[1].trim());
            }
        }
        return mapping;
    }

    static Set<String> split(String value) {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream((value == null ? "" : value).split("[\\n,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return result;
    }

    private static String systemValue(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? System.getProperty(name) : value;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
