package com.supertext.magnolia.translation.ui;

import java.util.Locale;

import javax.jcr.Node;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.context.MgnlContext;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;

import com.supertext.magnolia.translation.content.SiteLanguages;

/**
 * Finds the languages of the site a page belongs to: the site's {@code i18n} configuration,
 * or the global {@code /server/i18n/content} configuration when the site has none.
 */
public final class SiteLanguagesLookup {

    private SiteLanguagesLookup() {
    }

    public static SiteLanguages of(Node page, SiteManager siteManager, I18nContentSupport fallback) {
        I18nContentSupport i18n = null;
        try {
            Site site = page == null ? siteManager.getDefaultSite() : siteManager.getAssignedSite(page);
            i18n = site == null ? null : site.getI18n();
        } catch (RuntimeException e) {
            // No site module configuration: use the global one.
        }
        return new SiteLanguages(i18n != null ? i18n : fallback);
    }

    /** "German (Switzerland)" in the editor's UI language. */
    public static String displayName(Locale locale) {
        Locale ui = Locale.ENGLISH;
        try {
            Locale current = MgnlContext.getLocale();
            if (current != null) {
                ui = current;
            }
        } catch (RuntimeException e) {
            // No Magnolia context (tests).
        }
        String name = locale.getDisplayName(ui);
        if (name.isEmpty()) {
            return locale.toString();
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
