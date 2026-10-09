package com.supertext.magnolia.translation.api;

/**
 * A Supertext call failed. The message is meant for editors and administrators.
 *
 * <p>{@link #getMessage()} is English (logs, tests). The UI shows the message in the editor's
 * language: {@link #key()} is the message key in the module's i18n bundle
 * ({@code supertext-translation.error.*}), {@link #args()} its arguments and {@link #detail()}
 * the text Supertext sent back, if any (shown in brackets after the message). This package
 * stays free of Magnolia classes, so the translation itself happens in the UI.</p>
 */
public class SupertextException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean authenticationProblem;
    private final String key;
    private final transient Object[] args;
    private final String detail;

    public SupertextException(String message) {
        this(message, false, null);
    }

    public SupertextException(String message, Throwable cause) {
        this(message, false, cause);
    }

    public SupertextException(String message, boolean authenticationProblem, Throwable cause) {
        this(null, new Object[0], "", message, authenticationProblem, cause);
    }

    SupertextException(String key, Object[] args, String detail, String message, boolean authenticationProblem, Throwable cause) {
        super(message, cause);
        this.key = key;
        this.args = args == null ? new Object[0] : args.clone();
        this.detail = detail == null ? "" : detail;
        this.authenticationProblem = authenticationProblem;
    }

    /** True when the API key is missing or was rejected (the UI then shows where to get one). */
    public boolean isAuthenticationProblem() {
        return authenticationProblem;
    }

    /** Message key in the module's i18n bundle, or null when only the English message exists. */
    public String key() {
        return key;
    }

    /** Arguments of the message ({0}, {1}, …). */
    public Object[] args() {
        return args.clone();
    }

    /** What Supertext answered (tags removed, shortened), or "". */
    public String detail() {
        return detail;
    }
}
