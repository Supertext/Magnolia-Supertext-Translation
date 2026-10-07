package com.supertext.magnolia.translation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SupertextSettingsTest {

    private static SupertextSettings settings(SupertextTranslationModule module, Map<String, String> env) {
        return new SupertextSettings(() -> module, env::get);
    }

    @Test
    void environmentOverridesStoredKeyAndEndpoint() {
        SupertextTranslationModule module = new SupertextTranslationModule();
        module.setApiKey("stored");
        module.setEndpoint("https://stored.example/v1/");
        SupertextSettings s = settings(module, Map.of("SUPERTEXT_API_KEY", "Supertext-Auth-Key fromEnv", "SUPERTEXT_API_ENDPOINT", "https://env.example/v1/"));

        assertEquals("fromEnv", s.connection().apiKey());
        assertEquals("https://env.example/v1/", s.connection().endpoint());
    }

    @Test
    void storedValuesAndDefaults() {
        SupertextTranslationModule module = new SupertextTranslationModule();
        module.setApiKey(" stored ");
        module.setPoliteness("weird");
        module.setPollTimeoutSeconds(0);
        SupertextSettings s = settings(module, Map.of());

        assertEquals("stored", s.connection().apiKey());
        assertEquals("https://api.supertext.com/v1/", s.connection().endpoint());
        assertEquals(300, s.connection().pollTimeoutSeconds());
        assertEquals("default", s.politeness());
        assertFalse(settings(new SupertextTranslationModule(), Map.of()).hasApiKey());
    }

    @Test
    void languageMappingMatchesLocaleTagOrLanguage() {
        SupertextTranslationModule module = new SupertextTranslationModule();
        module.setLanguageMapping("de=de-CH\nfr_CH = fr-FR");
        SupertextSettings s = settings(module, Map.of());

        assertEquals("de-CH", s.targetLanguageCode(Locale.GERMAN));
        assertEquals("de-CH", s.targetLanguageCode(new Locale("de", "AT")), "falls back to the language");
        assertEquals("fr-FR", s.targetLanguageCode(new Locale("fr", "CH")));
        assertEquals("it-CH", s.targetLanguageCode(new Locale("it", "CH")), "no entry: BCP-47 tag");
    }

    @Test
    void propertyListsSplitOnCommasAndLines() {
        SupertextTranslationModule module = new SupertextTranslationModule();
        module.setExcludedProperties("teaser, subtitle\n\n code ");
        assertEquals(Set.of("teaser", "subtitle", "code"), settings(module, Map.of()).excludedProperties());
    }
}
