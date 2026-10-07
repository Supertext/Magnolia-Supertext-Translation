package com.supertext.magnolia.translation.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import info.magnolia.cms.i18n.I18nContentSupport;

/**
 * The languages of a site, as configured in its {@code i18n} section. Magnolia stores the
 * default language under the plain property name ({@code title}) and every other language
 * with the locale appended ({@code title_de_CH}), using {@link I18nContentSupport#getLanguageString}
 * for the suffix, exactly as the page dialogs do.
 */
public final class SiteLanguages {

    private final boolean enabled;
    private final Locale defaultLocale;
    private final List<Locale> locales;
    private final I18nContentSupport i18n;

    public SiteLanguages(I18nContentSupport i18n) {
        this.i18n = i18n;
        this.enabled = i18n != null && i18n.isEnabled();
        this.defaultLocale = i18n == null ? Locale.ENGLISH : i18n.getDefaultLocale();
        Collection<Locale> all = i18n == null ? List.of() : i18n.getLocales();
        this.locales = all == null ? List.of() : List.copyOf(all);
    }

    /** False when the site has no i18n configured (then there is nothing to translate into). */
    public boolean isEnabled() {
        return enabled && locales.size() > 1;
    }

    public Locale defaultLocale() {
        return defaultLocale;
    }

    /** All site languages except the default one, in the configured order. */
    public List<Locale> targetLocales() {
        List<Locale> targets = new ArrayList<>();
        for (Locale locale : locales) {
            if (!Objects.equals(locale, defaultLocale)) {
                targets.add(locale);
            }
        }
        return targets;
    }

    /** The id used in the dialog and as property suffix, e.g. {@code de_CH}. */
    public String id(Locale locale) {
        return i18n == null ? locale.toString() : i18n.getLanguageString(locale);
    }

    public Optional<Locale> byId(String id) {
        return locales.stream().filter(l -> id(l).equals(id) || l.toString().equals(id)).findFirst();
    }

    /** The property that holds the field's value in {@code locale}. */
    public String propertyName(String base, Locale locale) {
        return Objects.equals(locale, defaultLocale) ? base : base + "_" + id(locale);
    }
}
