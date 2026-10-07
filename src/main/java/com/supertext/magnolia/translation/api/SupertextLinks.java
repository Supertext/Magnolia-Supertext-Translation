package com.supertext.magnolia.translation.api;

/**
 * Where administrators get a Supertext account and an API key. Shown wherever the key is
 * entered or mentioned (settings screen, "no API key" and "authentication failed" messages).
 */
public final class SupertextLinks {

    public static final String SIGNUP_URL = "https://www.supertext.com/person/en/account/signin";
    public static final String API_KEY_URL = "https://www.supertext.com/en/integrations/api";

    /** Plain-text version, for notifications and log messages. */
    public static final String PLAIN_TEXT_HINT = "No Supertext account yet? Create one at " + SIGNUP_URL
            + " . Generate your API key at supertext.com → Integrations → API (" + API_KEY_URL
            + ", requires the Admin role).";

    /** HTML version: links open in a new tab. */
    public static final String HTML_HINT = "No Supertext account yet? "
            + "<a href=\"" + SIGNUP_URL + "\" target=\"_blank\" rel=\"noopener\">Create one at supertext.com</a>. "
            + "Generate your API key at "
            + "<a href=\"" + API_KEY_URL + "\" target=\"_blank\" rel=\"noopener\">supertext.com → Integrations → API</a> "
            + "(requires the Admin role).";

    private SupertextLinks() {
    }
}
