package com.supertext.magnolia.translation.ui.settings;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.jcr.RepositoryException;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.magnolia.cms.i18n.I18nContentSupport;
import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.module.site.Site;
import info.magnolia.module.site.SiteManager;
import info.magnolia.ui.api.view.View;

import com.supertext.magnolia.translation.SupertextSettings;
import com.supertext.magnolia.translation.api.LanguageMapping;
import com.supertext.magnolia.translation.api.SupertextService;
import com.supertext.magnolia.translation.api.SupertextException;
import com.supertext.magnolia.translation.internal.pluginhealth.KeyCheck;
import com.supertext.magnolia.translation.internal.pluginhealth.ReporterResult;
import com.supertext.magnolia.translation.reporting.ReportingService;
import com.supertext.magnolia.translation.ui.SiteLanguagesLookup;
import com.supertext.magnolia.translation.ui.SupertextMessages;
import com.vaadin.server.Sizeable;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.PasswordField;
import com.vaadin.ui.RadioButtonGroup;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

/**
 * Translation → Supertext: API key (with a connection check), endpoint, form of address, language
 * code mapping (with the site languages and how each is sent), excluded and additional properties,
 * timeout, version reports. Shows the installed version, the latest automatic API key check and
 * where to get a Supertext account and API key.
 */
public class SupertextSettingsView implements View {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SupertextSettingsView.class);
    private static final String RELEASES = "https://github.com/Supertext/Magnolia-Supertext-Translation/releases";

    private final transient SupertextSettings settings;
    private final transient SupertextService supertext;
    private final transient SimpleTranslator i18n;
    private final transient SupertextMessages messages;
    private final transient SettingsStore store;
    private final transient ReportingService reporting;
    private final transient SiteManager siteManager;
    private final transient I18nContentSupport i18nContentSupport;

    private final Panel root = new Panel();
    private final PasswordField apiKey = new PasswordField();
    private final TextField endpoint = new TextField();
    private final RadioButtonGroup<String> politeness = new RadioButtonGroup<>();
    private final TextField timeout = new TextField();
    private final TextArea languageMapping = new TextArea();
    private final TextArea excluded = new TextArea();
    private final TextArea additional = new TextArea();
    private final CheckBox versionReports = new CheckBox();
    private final Label languages = new Label("", ContentMode.HTML);
    private final Label status = new Label("", ContentMode.HTML);
    private boolean keyStored;

    @Inject
    public SupertextSettingsView(SupertextSettings settings, SupertextService supertext, SimpleTranslator i18n, ReportingService reporting,
                                 SiteManager siteManager, I18nContentSupport i18nContentSupport) {
        this.settings = settings;
        this.supertext = supertext;
        this.i18n = i18n;
        this.messages = new SupertextMessages(i18n::translate);
        this.store = new SettingsStore();
        this.reporting = reporting;
        this.siteManager = siteManager;
        this.i18nContentSupport = i18nContentSupport;
        build();
        load();
        showCheck(reporting.lastCheck());
        // A back-end request that runs the module's code: re-checks a changed key, reports an upgrade (in the background).
        reporting.onRequest();
    }

    @Override
    public Component asVaadinComponent() {
        return root;
    }

    private void build() {
        VerticalLayout content = new VerticalLayout();
        content.setMargin(true);
        content.setSpacing(true);
        content.addStyleName("supertext-settings");

        String version = SupertextService.version();
        String versionLink = version.equals("dev") ? RELEASES : RELEASES + "/tag/v" + version;
        content.addComponent(html("<h2 style=\"margin:0\">" + t("supertext-translation.settings.title") + "</h2>"
                + "<div>" + t("supertext-translation.settings.version", escape(version))
                + " · <a href=\"" + versionLink + "\" target=\"_blank\" rel=\"noopener\">" + t("supertext-translation.settings.releaseNotes") + "</a></div>"));

        FormLayout form = new FormLayout();
        form.setWidth(760, Sizeable.Unit.PIXELS);

        apiKey.setCaption(t("supertext-translation.settings.apiKey"));
        apiKey.setWidth(100, Sizeable.Unit.PERCENTAGE);
        form.addComponent(apiKey);
        form.addComponent(help(messages.htmlHint()));
        if (!settings.apiKeyFromEnvironment().isEmpty()) {
            apiKey.setEnabled(false);
            form.addComponent(help(t("supertext-translation.settings.apiKeyFromEnvironment", SupertextSettings.API_KEY_VARIABLE)));
        }

        endpoint.setCaption(t("supertext-translation.settings.endpoint"));
        endpoint.setPlaceholder(SupertextService.DEFAULT_ENDPOINT);
        endpoint.setWidth(100, Sizeable.Unit.PERCENTAGE);
        form.addComponent(endpoint);
        if (!settings.endpointFromEnvironment().isEmpty()) {
            endpoint.setEnabled(false);
            form.addComponent(help(t("supertext-translation.settings.endpointFromEnvironment", SupertextSettings.ENDPOINT_VARIABLE)));
        }

        politeness.setCaption(t("supertext-translation.settings.politeness"));
        politeness.setItems(List.of("default", "more", "less"));
        politeness.setItemCaptionGenerator(value -> t("supertext-translation.settings.politeness." + value));
        form.addComponent(politeness);
        form.addComponent(help(escape(t("supertext-translation.settings.politeness.help"))));

        languageMapping.setCaption(t("supertext-translation.settings.languageMapping"));
        languageMapping.setRows(3);
        languageMapping.setPlaceholder("de=de-CH");
        languageMapping.setWidth(100, Sizeable.Unit.PERCENTAGE);
        form.addComponent(languageMapping);
        form.addComponent(help(t("supertext-translation.settings.languageMapping.help")));
        languages.setWidth(100, Sizeable.Unit.PERCENTAGE);
        form.addComponent(languages);

        excluded.setCaption(t("supertext-translation.settings.excluded"));
        excluded.setRows(2);
        excluded.setWidth(100, Sizeable.Unit.PERCENTAGE);
        form.addComponent(excluded);

        additional.setCaption(t("supertext-translation.settings.additional"));
        additional.setRows(2);
        additional.setWidth(100, Sizeable.Unit.PERCENTAGE);
        form.addComponent(additional);
        form.addComponent(help(t("supertext-translation.settings.fields.help")));

        timeout.setCaption(t("supertext-translation.settings.timeout"));
        timeout.setWidth(120, Sizeable.Unit.PIXELS);
        form.addComponent(timeout);

        versionReports.setCaption(t("supertext-translation.settings.versionReports"));
        form.addComponent(versionReports);
        form.addComponent(help(escape(t("supertext-translation.settings.versionReports.help"))));
        if (settings.versionReportsFromEnvironment()) {
            versionReports.setEnabled(false);
            form.addComponent(help(escape(t("supertext-translation.settings.versionReports.fromEnvironment", "SUPERTEXT_VERSION_REPORTS"))));
        }
        String ping = reporting.pingUrl();
        if (ping != null && settings.versionReportsEnabled()) {
            Label pingLabel = help(escape(t("supertext-translation.settings.versionReports.ping", ping)));
            pingLabel.addStyleName("supertext-ping");
            form.addComponent(pingLabel);
        }

        Button save = new Button(t("supertext-translation.settings.save"), e -> save());
        save.addStyleName("commit");
        Button check = new Button(t("supertext-translation.settings.check"), e -> check());
        HorizontalLayout buttons = new HorizontalLayout(save, check);
        buttons.setSpacing(true);
        form.addComponent(buttons);
        form.addComponent(status);

        content.addComponent(form);
        CssLayout wrapper = new CssLayout(content);
        wrapper.setWidth(100, Sizeable.Unit.PERCENTAGE);
        root.setContent(wrapper);
        com.vaadin.server.Page page = com.vaadin.server.Page.getCurrent();
        if (page != null) {
            page.getStyles().add(".supertext-settings { padding: 16px 32px 48px !important; }"
                    + " .supertext-settings h2 { font-weight: 400; }"
                    + " .supertext-languages { border-collapse: collapse; margin: 4px 0 8px; }"
                    + " .supertext-languages th, .supertext-languages td { text-align: left; padding: 3px 12px 3px 0; border-bottom: 1px solid #ddd; vertical-align: top; }"
                    + " .supertext-settings input, .supertext-settings input::placeholder, .supertext-settings textarea::placeholder { font-family: inherit; }");
        }
        root.setSizeFull();
        root.addStyleName("borderless");
    }

    private void load() {
        try {
            Map<String, String> values = store.read();
            keyStored = !values.getOrDefault(SettingsStore.API_KEY, "").isBlank();
            apiKey.setValue("");
            apiKey.setPlaceholder(keyStored ? t("supertext-translation.settings.apiKeyStored") : "");
            endpoint.setValue(values.getOrDefault(SettingsStore.ENDPOINT, ""));
            String p = values.getOrDefault(SettingsStore.POLITENESS, "default");
            politeness.setValue(List.of("default", "more", "less").contains(p) ? p : "default");
            String seconds = values.getOrDefault(SettingsStore.POLL_TIMEOUT, "");
            timeout.setValue(seconds.isBlank() ? "300" : seconds);
            languageMapping.setValue(values.getOrDefault(SettingsStore.LANGUAGE_MAPPING, ""));
            excluded.setValue(values.getOrDefault(SettingsStore.EXCLUDED, ""));
            additional.setValue(values.getOrDefault(SettingsStore.ADDITIONAL, ""));
            versionReports.setValue(settings.versionReportsFromEnvironment() ? settings.versionReportsEnabled()
                    : !Boolean.FALSE.equals(parseBoolean(values.getOrDefault(SettingsStore.VERSION_REPORTS, ""))));
            languages.setValue(languageTable(LanguageMapping.parse(languageMapping.getValue())));
        } catch (RepositoryException e) {
            log.warn("Supertext: could not read the settings", e);
            status.setValue(escape(t("supertext-translation.settings.readFailed")));
        }
    }

    private void save() {
        int seconds;
        try {
            seconds = Integer.parseInt(timeout.getValue().trim());
            if (seconds < 10 || seconds > 3600) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            Notification.show(t("supertext-translation.settings.timeoutInvalid"), Notification.Type.WARNING_MESSAGE);
            return;
        }
        String endpointValue = endpoint.getValue().trim();
        if (!endpointValue.isEmpty()) {
            try {
                SupertextService.checkEndpoint(endpointValue);
            } catch (SupertextException e) {
                Notification.show(messages.of(e), Notification.Type.WARNING_MESSAGE);
                return;
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        String key = SupertextService.normalizeApiKey(apiKey.getValue());
        values.put(SettingsStore.API_KEY, key.isEmpty() ? null : key);
        values.put(SettingsStore.ENDPOINT, endpointValue);
        values.put(SettingsStore.POLITENESS, politeness.getValue() == null ? "default" : politeness.getValue());
        values.put(SettingsStore.POLL_TIMEOUT, String.valueOf(seconds));
        values.put(SettingsStore.LANGUAGE_MAPPING, languageMapping.getValue());
        values.put(SettingsStore.EXCLUDED, excluded.getValue());
        values.put(SettingsStore.ADDITIONAL, additional.getValue());
        values.put(SettingsStore.VERSION_REPORTS, settings.versionReportsFromEnvironment() ? null : String.valueOf(Boolean.TRUE.equals(versionReports.getValue())));
        try {
            store.write(values);
            load();
            Notification.show(t("supertext-translation.settings.saved"), Notification.Type.HUMANIZED_MESSAGE);
        } catch (RepositoryException | RuntimeException e) {
            log.warn("Supertext: could not save the settings", e);
            Notification.show(t("supertext-translation.settings.saveFailed"), e.getMessage(), Notification.Type.ERROR_MESSAGE);
            return;
        }
        // The automatic API key check, and the version report if it is on. Never blocks or undoes the save.
        ReporterResult result = reporting.afterSettingsSaved();
        showCheck(result == null ? null : result.getCheck());
    }

    /** Checks the saved key (or the one from the environment) and shows the result; reports a change. */
    private void check() {
        String typed = SupertextService.normalizeApiKey(apiKey.getValue());
        if (!typed.isEmpty() && settings.apiKeyFromEnvironment().isEmpty()) {
            Notification.show(t("supertext-translation.settings.checkSaveFirst"), Notification.Type.WARNING_MESSAGE);
            return;
        }
        ReporterResult result = reporting.testConnection();
        showCheck(result == null ? null : result.getCheck());
    }

    private void showCheck(KeyCheck check) {
        if (check == null) {
            status.setValue("");
            return;
        }
        String color = SupertextMessages.ok(check) ? "#2e7d32" : "#c62828";
        String mark = SupertextMessages.ok(check) ? "✓ " : "✗ ";
        status.setValue("<span style=\"color:" + color + "\">" + mark + messages.keyCheckHtml(check) + "</span>"
                + "<div class=\"description\">" + escape(t("supertext-translation.keyCheck.checkedAt", check.checkedAt())) + "</div>");
    }

    /** The site languages and how each is sent to Supertext (or that it needs a line in "Language codes"). */
    String languageTable(LanguageMapping mapping) {
        Set<String> sources = new LinkedHashSet<>();
        Set<String> targets = new LinkedHashSet<>();
        Set<String> all = new LinkedHashSet<>();
        for (I18nContentSupport i18nSupport : siteLanguageConfigurations()) {
            if (i18nSupport == null || !i18nSupport.isEnabled()) {
                continue;
            }
            Locale main = i18nSupport.getDefaultLocale();
            if (main != null) {
                sources.add(main.toString());
                all.add(main.toString());
            }
            Collection<Locale> locales = i18nSupport.getLocales();
            if (locales != null) {
                for (Locale l : locales) {
                    all.add(l.toString());
                    if (!l.equals(main)) {
                        targets.add(l.toString());
                    }
                }
            }
        }
        if (all.isEmpty()) {
            return "<div class=\"description\">" + escape(t("supertext-translation.settings.languages.none")) + "</div>";
        }
        StringBuilder html = new StringBuilder("<table class=\"supertext-languages\"><tr>")
                .append("<th>").append(escape(t("supertext-translation.settings.languages.language"))).append("</th>")
                .append("<th>").append(escape(t("supertext-translation.settings.languages.code"))).append("</th>")
                .append("<th>").append(escape(t("supertext-translation.settings.languages.politeness"))).append("</th></tr>");
        for (String language : all) {
            LanguageMapping.Status target = mapping.status(language, false);
            boolean isTarget = targets.contains(language);
            String code;
            if (!isTarget) {
                code = "";
            } else if (target.supported()) {
                code = "<code>" + escape(target.code()) + "</code> · " + escape(t(target.mapped()
                        ? "supertext-translation.settings.languages.mapped" : "supertext-translation.settings.languages.exact"));
            } else {
                code = "<span style=\"color:#c62828\">✗ " + escape(t("supertext-translation.settings.languages.unsupported", language)) + "</span>";
            }
            if (sources.contains(language)) {
                LanguageMapping.Status source = mapping.status(language, true);
                code += (code.isEmpty() ? "" : "<br>") + (source.supported()
                        ? escape(t("supertext-translation.settings.languages.source", source.code()))
                        : "<span style=\"color:#c62828\">✗ " + escape(t("supertext-translation.settings.languages.sourceUnsupported", language)) + "</span>");
            }
            String politeness = !isTarget ? "–" : Boolean.TRUE.equals(target.politeness()) ? t("supertext-translation.settings.languages.yes")
                    : target.supported() ? t("supertext-translation.settings.languages.no") : "–";
            html.append("<tr><td>").append(escape(SiteLanguagesLookup.displayName(Locale.forLanguageTag(language.replace('_', '-')))))
                    .append(" <code>").append(escape(language)).append("</code></td><td>").append(code)
                    .append("</td><td>").append(escape(politeness)).append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    /** Every site's i18n configuration, or Magnolia's global one when there are no sites. */
    private List<I18nContentSupport> siteLanguageConfigurations() {
        List<I18nContentSupport> result = new java.util.ArrayList<>();
        try {
            Collection<Site> sites = siteManager.getSites();
            if (sites != null) {
                for (Site site : sites) {
                    if (site != null && site.getI18n() != null) {
                        result.add(site.getI18n());
                    }
                }
            }
        } catch (RuntimeException e) {
            log.debug("Supertext: could not read the sites: {}", e.toString());
        }
        if (result.isEmpty() && i18nContentSupport != null) {
            result.add(i18nContentSupport);
        }
        return result;
    }

    private static Boolean parseBoolean(String value) {
        String v = value == null ? "" : value.trim();
        return v.equalsIgnoreCase("true") ? Boolean.TRUE : v.equalsIgnoreCase("false") ? Boolean.FALSE : null;
    }

    private String t(String key, Object... args) {
        return i18n.translate(key, args);
    }

    private static Label help(String html) {
        Label label = new Label(html, ContentMode.HTML);
        label.setWidth(100, Sizeable.Unit.PERCENTAGE);
        label.addStyleName("description");
        return label;
    }

    private static Label html(String html) {
        Label label = new Label(html, ContentMode.HTML);
        label.setWidth(100, Sizeable.Unit.PERCENTAGE);
        return label;
    }

    static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
