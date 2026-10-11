package com.supertext.magnolia.translation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LanguageCodesTest {

    @Test
    void bareSourceStaysBare() {
        assertEquals("en", LanguageCodes.source("en"));
    }

    @Test
    void regionalSourceBecomesBare() {
        assertEquals("en", LanguageCodes.source("en-US"));
        assertEquals("en", LanguageCodes.source("en_GB"));
        assertEquals("de", LanguageCodes.source("DE-CH"));
        assertEquals("", LanguageCodes.source(""));
    }

    @Test
    void bareTargetGetsDefaultRegion() {
        assertEquals("de-DE", LanguageCodes.target("de"));
        assertEquals("en-US", LanguageCodes.target("EN"));
        assertEquals("sl-SI", LanguageCodes.target("sl"));
    }

    @Test
    void regionalTargetIsKeptAndNormalized() {
        assertEquals("de-CH", LanguageCodes.target("de_ch"));
        assertEquals("fr-CH", LanguageCodes.target("fr-CH"));
        assertEquals("zh-Hant", LanguageCodes.target("zh_hant"));
        assertEquals("es-419", LanguageCodes.target("es-419"));
    }

    @Test
    void overrideWins() {
        assertEquals("de-AT", LanguageCodes.target("de-DE", "de_at"));
        assertEquals("de-DE", LanguageCodes.target("de-CH", "de"));
        assertEquals("pt", LanguageCodes.source("en-GB", "pt-BR"));
    }

    @Test
    void unknownBareCodeIsUnchanged() {
        assertEquals("rm", LanguageCodes.target("rm"));
    }
}
