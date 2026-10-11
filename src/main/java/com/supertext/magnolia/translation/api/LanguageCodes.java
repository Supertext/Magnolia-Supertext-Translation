package com.supertext.magnolia.translation.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns language tags into the codes Supertext accepts.
 *
 * <p>Source: the bare language ({@code en-GB} → {@code en}). Target: a regional code
 * ({@code de_ch} → {@code de-CH}); a bare target gets a regional default ({@code de} →
 * {@code de-DE}), because Supertext rejects bare targets with {@code INVALID_LANGUAGE_PAIR}.
 * Unknown bare codes are sent unchanged.
 */
public final class LanguageCodes {

    /** Regional defaults for bare target languages (same table in every Supertext plugin). */
    public static final Map<String, String> DEFAULT_REGIONS = Map.ofEntries(
            Map.entry("ar", "ar-SA"), Map.entry("bg", "bg-BG"), Map.entry("cs", "cs-CZ"), Map.entry("da", "da-DK"),
            Map.entry("de", "de-DE"), Map.entry("el", "el-GR"), Map.entry("en", "en-US"), Map.entry("es", "es-ES"),
            Map.entry("et", "et-EE"), Map.entry("fi", "fi-FI"), Map.entry("fr", "fr-FR"), Map.entry("hr", "hr-HR"),
            Map.entry("hu", "hu-HU"), Map.entry("it", "it-IT"), Map.entry("ja", "ja-JP"), Map.entry("ko", "ko-KR"),
            Map.entry("lt", "lt-LT"), Map.entry("lv", "lv-LV"), Map.entry("nb", "nb-NO"), Map.entry("nl", "nl-NL"),
            Map.entry("pl", "pl-PL"), Map.entry("pt", "pt-PT"), Map.entry("ro", "ro-RO"), Map.entry("ru", "ru-RU"),
            Map.entry("sk", "sk-SK"), Map.entry("sl", "sl-SI"), Map.entry("sr", "sr-RS"), Map.entry("sv", "sv-SE"),
            Map.entry("th", "th-TH"), Map.entry("tr", "tr-TR"), Map.entry("uk", "uk-UA"), Map.entry("vi", "vi-VN"),
            Map.entry("zh", "zh-CN"));

    private LanguageCodes() {
    }

    /** {@code de-ch} or {@code de_CH} → {@code de-CH}, {@code zh_hant} → {@code zh-Hant}, {@code es-419} stays. */
    public static String format(String tag) {
        List<String> out = new ArrayList<>();
        String[] parts = (tag == null ? "" : tag.trim()).split("[-_]");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (i == 0) {
                out.add(part.toLowerCase(Locale.ROOT));
            } else if (part.length() == 2) {
                out.add(part.toUpperCase(Locale.ROOT));
            } else if (part.chars().allMatch(Character::isDigit)) {
                out.add(part);
            } else {
                out.add(part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return String.join("-", out);
    }

    /** Source language: the bare language of the override, else of the CMS tag ({@code en-GB} → {@code en}). */
    public static String source(String tag, String override) {
        String chosen = override != null && !override.isBlank() ? override : tag;
        if (chosen == null || chosen.isBlank()) {
            return "";
        }
        return chosen.trim().split("[-_]")[0].toLowerCase(Locale.ROOT);
    }

    public static String source(String tag) {
        return source(tag, null);
    }

    /** Target language: the override, else the CMS tag, as a regional code ({@code de} → {@code de-DE}). */
    public static String target(String tag, String override) {
        String code = format(override != null && !override.isBlank() ? override : tag);
        if (!code.isEmpty() && !code.contains("-")) {
            return DEFAULT_REGIONS.getOrDefault(code, code);
        }
        return code;
    }

    public static String target(String tag) {
        return target(tag, null);
    }
}
