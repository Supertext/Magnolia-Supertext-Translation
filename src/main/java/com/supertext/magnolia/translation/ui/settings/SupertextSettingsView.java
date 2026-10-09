package com.supertext.magnolia.translation.ui.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.magnolia.i18nsystem.SimpleTranslator;
import info.magnolia.ui.api.view.View;

import com.supertext.magnolia.translation.SupertextSettings;
import com.supertext.magnolia.translation.api.SupertextClient;
import com.supertext.magnolia.translation.api.SupertextConnection;
import com.supertext.magnolia.translation.api.SupertextException;
import com.supertext.magnolia.translation.ui.SupertextMessages;
import com.vaadin.server.Sizeable;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.ui.Button;
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
 * code mapping, excluded and additional properties, timeout. Shows the installed version and
 * where to get a Supertext account and API key.
 */
public class SupertextSettingsView implements View {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(SupertextSettingsView.class);
    private static final String RELEASES = "https://github.com/Supertext/Magnolia-Supertext-Translation/releases";

    private final transient SupertextSettings settings;
    private final transient SupertextClient client;
    private final transient SimpleTranslator i18n;
    private final transient SupertextMessages messages;
    private final transient SettingsStore store;

    private final Panel root = new Panel();
    private final PasswordField apiKey = new PasswordField();
    private final TextField endpoint = new TextField();
    private final RadioButtonGroup<String> politeness = new RadioButtonGroup<>();
    private final TextField timeout = new TextField();
    private final TextArea languageMapping = new TextArea();
    private final TextArea excluded = new TextArea();
    private final TextArea additional = new TextArea();
    private final Label status = new Label("", ContentMode.HTML);
    private boolean keyStored;

    @Inject
    public SupertextSettingsView(SupertextSettings settings, SupertextClient client, SimpleTranslator i18n) {
        this.settings = settings;
        this.client = client;
        this.i18n = i18n;
        this.messages = new SupertextMessages(i18n::translate);
        this.store = new SettingsStore();
        build();
        load();
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

        String version = SupertextClient.version();
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
        endpoint.setPlaceholder(SupertextClient.DEFAULT_ENDPOINT);
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

        languageMapping.setCaption(t("supertext-translation.settings.languageMapping"));
        languageMapping.setRows(3);
        languageMapping.setPlaceholder("de=de-CH");
        languageMapping.setWidth(100, Sizeable.Unit.PERCENTAGE);
        form.addComponent(languageMapping);
        form.addComponent(help(t("supertext-translation.settings.languageMapping.help")));

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
                SupertextClient.checkEndpoint(endpointValue);
            } catch (SupertextException e) {
                Notification.show(messages.of(e), Notification.Type.WARNING_MESSAGE);
                return;
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        String key = SupertextClient.normalizeApiKey(apiKey.getValue());
        values.put(SettingsStore.API_KEY, key.isEmpty() ? null : key);
        values.put(SettingsStore.ENDPOINT, endpointValue);
        values.put(SettingsStore.POLITENESS, politeness.getValue() == null ? "default" : politeness.getValue());
        values.put(SettingsStore.POLL_TIMEOUT, String.valueOf(seconds));
        values.put(SettingsStore.LANGUAGE_MAPPING, languageMapping.getValue());
        values.put(SettingsStore.EXCLUDED, excluded.getValue());
        values.put(SettingsStore.ADDITIONAL, additional.getValue());
        try {
            store.write(values);
            load();
            Notification.show(t("supertext-translation.settings.saved"), Notification.Type.HUMANIZED_MESSAGE);
        } catch (RepositoryException | RuntimeException e) {
            log.warn("Supertext: could not save the settings", e);
            Notification.show(t("supertext-translation.settings.saveFailed"), e.getMessage(), Notification.Type.ERROR_MESSAGE);
        }
    }

    /** Checks the key typed in the field, or the one in effect when the field is empty. */
    private void check() {
        SupertextConnection current = settings.connection();
        String typed = SupertextClient.normalizeApiKey(apiKey.getValue());
        String typedEndpoint = endpoint.getValue().trim();
        SupertextConnection connection = new SupertextConnection(
                settings.endpointFromEnvironment().isEmpty() && !typedEndpoint.isEmpty() ? typedEndpoint : current.endpoint(),
                typed.isEmpty() || !settings.apiKeyFromEnvironment().isEmpty() ? current.apiKey() : typed);
        try {
            client.validateApiKey(connection);
            status.setValue("<span style=\"color:#2e7d32\">✓ " + escape(t("supertext-translation.settings.checkOk", connection.endpoint())) + "</span>");
        } catch (SupertextException e) {
            String hint = e.isAuthenticationProblem() ? "<br>" + messages.htmlHint() : "";
            status.setValue("<span style=\"color:#c62828\">✗ " + escape(messages.of(e)) + "</span>" + hint);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
