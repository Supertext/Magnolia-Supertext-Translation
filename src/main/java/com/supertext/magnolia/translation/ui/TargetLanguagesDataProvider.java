package com.supertext.magnolia.translation.ui;

import java.util.ArrayList;
import java.util.List;

import javax.jcr.Node;

import jakarta.inject.Inject;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.module.site.SiteManager;
import info.magnolia.ui.ValueContext;

import com.supertext.magnolia.translation.content.SiteLanguages;
import com.vaadin.data.provider.ListDataProvider;

/**
 * The options of the "Translate into" checkboxes: every language of the page's site except
 * the default (source) language. Registered as datasource {@code supertext-target-languages}
 * in the module descriptor.
 */
public class TargetLanguagesDataProvider extends ListDataProvider<String> {

    private static final long serialVersionUID = 1L;

    @Inject
    public TargetLanguagesDataProvider(ValueContext<Node> valueContext, SiteManager siteManager, I18nContentSupport i18nContentSupport) {
        super(ids(SiteLanguagesLookup.of(valueContext.getSingle().orElse(null), siteManager, i18nContentSupport)));
    }

    private static List<String> ids(SiteLanguages languages) {
        List<String> ids = new ArrayList<>();
        languages.targetLocales().forEach(locale -> ids.add(languages.id(locale)));
        return ids;
    }
}
