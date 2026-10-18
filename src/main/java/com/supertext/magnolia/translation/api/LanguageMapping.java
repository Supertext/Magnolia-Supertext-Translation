package com.supertext.magnolia.translation.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.supertext.magnolia.translation.internal.sdk.v1.Languages;

/**
 * The <em>Language codes</em> setting: one {@code magnoliaLanguage=supertextCode} per line (or separated by
 * commas or semicolons), e.g. {@code de=de-CH}. One mapping serves both directions: as target {@code de} is sent
 * as {@code de-CH}, as source (the site's main language) as {@code de}.
 *
 * <p>The SDK's rules ({@link Languages}): the mapping wins, else a Magnolia language that equals a Supertext
 * code (case and {@code _}/{@code -} ignored: {@code de_CH} → {@code de-CH}, {@code es}), else the SDK's default
 * target where one is obvious ({@code de} → {@code de-DE}, {@code es_ES} → {@code es}; SDK 0.4.0), else the language
 * is unsupported and nothing is sent ({@code en_IE}, {@code de_LI}). No Magnolia classes, so it is unit-tested on its own.</p>
 */
public final class LanguageMapping {

    private final Map<String, String> entries;

    private LanguageMapping(Map<String, String> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    /** Parses the setting; lines without {@code =} or with an empty side are ignored. Later lines win. */
    public static LanguageMapping parse(String value) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String line : (value == null ? "" : value).split("[\\n,;]")) {
            String[] pair = line.split("=", 2);
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                mapping.put(pair[0].trim(), pair[1].trim());
            }
        }
        return new LanguageMapping(mapping);
    }

    /** Magnolia language → Supertext code, as entered (the SDK compares keys like codes). */
    public Map<String, String> entries() {
        return entries;
    }

    /** Whether the mapping has an entry for the language (case and {@code _}/{@code -} ignored). */
    public boolean maps(String language) {
        String key = key(language);
        return entries.keySet().stream().anyMatch(k -> key(k).equals(key));
    }

    /** The target code to send, or throws {@code unsupportedLanguage}. */
    public String target(String language) throws SupertextException {
        return SupertextService.targetCode(language, entries);
    }

    /** The source code to send, or throws {@code unsupportedLanguage}. */
    public String source(String language) throws SupertextException {
        return SupertextService.sourceCode(language, entries);
    }

    /**
     * How a Magnolia language is sent, for the settings screen.
     *
     * @param language   the Magnolia language, e.g. {@code de_CH}
     * @param code       the Supertext code it is sent as, or null when it is unsupported
     * @param mapped     true when the code comes from the mapping, false for an exact match or a default
     * @param politeness whether the target offers formal/informal ({@code null} for a source or an unknown code)
     * @param fromDefault true when the code is the SDK's default target for the language ({@code de} → {@code de-DE})
     */
    public record Status(String language, String code, boolean mapped, Boolean politeness, boolean fromDefault) {
        public boolean supported() {
            return code != null;
        }
    }

    /** How {@code language} is sent as a target ({@code asSource} = the site's main language, sent as source). */
    public Status status(String language, boolean asSource) {
        boolean mapped = maps(language);
        try {
            if (asSource) {
                return new Status(language, source(language), mapped, null, false);
            }
            String code = target(language);
            boolean fromDefault = !mapped && Languages.matchTarget(language) == null;
            return new Status(language, code, mapped, Languages.supportsPoliteness(code), fromDefault);
        } catch (SupertextException e) {
            return new Status(language, null, mapped, null, false);
        }
    }

    private static String key(String code) {
        return code == null ? "" : code.strip().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
