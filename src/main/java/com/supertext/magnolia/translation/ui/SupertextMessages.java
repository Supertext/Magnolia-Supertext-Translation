package com.supertext.magnolia.translation.ui;

import com.supertext.magnolia.translation.api.SupertextException;
import com.supertext.magnolia.translation.api.SupertextLinks;
import com.supertext.magnolia.translation.internal.pluginhealth.KeyCheck;
import com.supertext.magnolia.translation.internal.sdk.v1.ApiKeyCheck;

/**
 * Supertext errors and the API key hint in the editor's interface language. Wraps Magnolia's
 * {@code SimpleTranslator} ({@code new SupertextMessages(i18n::translate)}), so it is unit-tested
 * without Magnolia.
 */
public final class SupertextMessages {

    /** Message keys of the API key hint; {0} = sign-up URL, {1} = API key URL. */
    public static final String HINT = "supertext-translation.apiKey.hint";
    public static final String HINT_HTML = "supertext-translation.apiKey.hintHtml";

    /** Magnolia's {@code SimpleTranslator#translate(String, Object...)}. */
    @FunctionalInterface
    public interface Translator {
        String translate(String key, Object... args);
    }

    private final Translator i18n;

    public SupertextMessages(Translator i18n) {
        this.i18n = i18n;
    }

    /** The error in the editor's language (English when the key is unknown), with Supertext's answer in brackets. */
    public String of(SupertextException e) {
        if (e.key() == null) {
            return e.getMessage();
        }
        String text = i18n.translate(e.key(), e.args());
        if (text == null || text.isBlank() || text.equals(e.key())) {
            return e.getMessage();
        }
        return e.detail().isEmpty() ? text : text + " (" + e.detail() + ")";
    }

    /** Prefix of the API key check's messages ({@code ok}, {@code invalid}, {@code missing}, {@code unreachable}, {@code error}). */
    public static final String KEY_CHECK = "supertext-translation.keyCheck.";

    /**
     * The automatic API key check's result as HTML (the Commando Center's five messages): links to supertext.com
     * open in a new tab; {@code error} names the HTTP status or Supertext's error code.
     */
    public String keyCheckHtml(KeyCheck check) {
        if (check == null) {
            return "";
        }
        String status = check.status();
        String detail = check.httpStatus() != null ? "HTTP " + check.httpStatus() : check.errorCode() != null ? check.errorCode() : "?";
        if (check.errorCode() != null && check.httpStatus() != null) {
            detail += ", " + check.errorCode();
        }
        String text = i18n.translate(KEY_CHECK + status, SupertextLinks.SIGNUP_URL, SupertextLinks.API_KEY_URL, escape(detail));
        return text == null || text.equals(KEY_CHECK + status) ? escape(status) : text;
    }

    /** Whether the key check's result is good news. */
    public static boolean ok(KeyCheck check) {
        return check != null && ApiKeyCheck.Status.OK.value().equals(check.status());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** "No Supertext account yet? …" as plain text with both URLs, for notifications. */
    public String hint() {
        return translate(HINT, SupertextLinks.PLAIN_TEXT_HINT);
    }

    /** "No Supertext account yet? …" with links that open in a new tab. */
    public String htmlHint() {
        return translate(HINT_HTML, SupertextLinks.HTML_HINT);
    }

    private String translate(String key, String fallback) {
        String text = i18n.translate(key, SupertextLinks.SIGNUP_URL, SupertextLinks.API_KEY_URL);
        return text == null || text.isBlank() || text.equals(key) ? fallback : text;
    }
}
