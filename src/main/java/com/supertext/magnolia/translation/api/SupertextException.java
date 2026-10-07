package com.supertext.magnolia.translation.api;

/**
 * A Supertext call failed. The message is meant for editors and administrators.
 */
public class SupertextException extends Exception {

    private static final long serialVersionUID = 1L;

    private final boolean authenticationProblem;

    public SupertextException(String message) {
        this(message, false, null);
    }

    public SupertextException(String message, Throwable cause) {
        this(message, false, cause);
    }

    public SupertextException(String message, boolean authenticationProblem, Throwable cause) {
        super(message, cause);
        this.authenticationProblem = authenticationProblem;
    }

    /** True when the API key is missing or was rejected (the UI then shows where to get one). */
    public boolean isAuthenticationProblem() {
        return authenticationProblem;
    }
}
