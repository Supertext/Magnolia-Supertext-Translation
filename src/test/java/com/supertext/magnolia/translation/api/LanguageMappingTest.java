package com.supertext.magnolia.translation.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class LanguageMappingTest {

    @Test
    void parsesLinesCommasAndSemicolons() {
        LanguageMapping m = LanguageMapping.parse(" de = de-CH \nfr=fr-CH, it=it-CH; broken\n=x\nen=");
        assertEquals(Map.of("de", "de-CH", "fr", "fr-CH", "it", "it-CH"), m.entries());
        assertTrue(m.maps("DE"));
        assertFalse(m.maps("en"));
    }

    @Test
    void statusForTheSettingsScreen() {
        LanguageMapping m = LanguageMapping.parse("de=de-CH\nen=en-US");
        LanguageMapping.Status de = m.status("de", false);
        assertEquals("de-CH", de.code());
        assertTrue(de.mapped());
        assertEquals(Boolean.TRUE, de.politeness());

        LanguageMapping.Status frCh = m.status("fr_CH", false);
        assertEquals("fr-CH", frCh.code());
        assertFalse(frCh.mapped(), "exact match");

        assertEquals(Boolean.FALSE, m.status("en", false).politeness(), "English has no formal/informal");
        assertEquals("en", m.status("en", true).code(), "as source: bare");

        LanguageMapping.Status it = m.status("it", false);
        assertFalse(it.supported(), "no default region");
        assertNull(it.politeness());
        assertEquals("it", m.status("it", true).code(), "a bare source is fine");
    }
}
