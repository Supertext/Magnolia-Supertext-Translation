package com.supertext.magnolia.translation.ui.settings;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import info.magnolia.context.MgnlContext;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.repository.RepositoryConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.supertext.magnolia.translation.SupertextTranslationModule;

/**
 * Reads and writes {@code config:/modules/supertext-translation/config}, the settings edited
 * in the Supertext app. {@link #write} uses the current user's session, so only users allowed
 * to change the configuration can save; {@link #loadModule} reads with system rights.
 */
public class SettingsStore {

    private static final Logger log = LoggerFactory.getLogger(SettingsStore.class);

    public static final String CONFIG_PATH = "/modules/supertext-translation/config";

    public static final String API_KEY = "apiKey";
    public static final String ENDPOINT = "endpoint";
    public static final String POLITENESS = "politeness";
    public static final String POLL_TIMEOUT = "pollTimeoutSeconds";
    public static final String LANGUAGE_MAPPING = "languageMapping";
    public static final String EXCLUDED = "excludedProperties";
    public static final String ADDITIONAL = "additionalProperties";

    /** The stored settings, read with system rights (editors can't read the config workspace). */
    public static SupertextTranslationModule loadModule() {
        SupertextTranslationModule module = new SupertextTranslationModule();
        try {
            Map<String, String> values = MgnlContext.doInSystemContext(() -> {
                try {
                    return new SettingsStore().read();
                } catch (RepositoryException e) {
                    throw new IllegalStateException(e);
                }
            });
            module.setApiKey(values.getOrDefault(API_KEY, ""));
            module.setEndpoint(values.getOrDefault(ENDPOINT, ""));
            module.setPoliteness(values.getOrDefault(POLITENESS, "default"));
            String timeout = values.getOrDefault(POLL_TIMEOUT, "");
            if (!timeout.isBlank()) {
                module.setPollTimeoutSeconds(Integer.parseInt(timeout.trim()));
            }
            module.setLanguageMapping(values.getOrDefault(LANGUAGE_MAPPING, ""));
            module.setExcludedProperties(values.getOrDefault(EXCLUDED, ""));
            module.setAdditionalProperties(values.getOrDefault(ADDITIONAL, ""));
        } catch (RuntimeException e) {
            log.warn("Supertext: could not read {}: {}", CONFIG_PATH, e.getMessage());
        }
        return module;
    }

    public Map<String, String> read() throws RepositoryException {
        Map<String, String> values = new LinkedHashMap<>();
        Session session = MgnlContext.getJCRSession(RepositoryConstants.CONFIG);
        if (session.nodeExists(CONFIG_PATH)) {
            Node node = session.getNode(CONFIG_PATH);
            for (String name : new String[] {API_KEY, ENDPOINT, POLITENESS, POLL_TIMEOUT, LANGUAGE_MAPPING, EXCLUDED, ADDITIONAL}) {
                values.put(name, node.hasProperty(name) ? node.getProperty(name).getString() : "");
            }
        }
        return values;
    }

    /** Writes the given values; a null value leaves the stored one unchanged. */
    public void write(Map<String, String> values) throws RepositoryException {
        Session session = MgnlContext.getJCRSession(RepositoryConstants.CONFIG);
        Node node = NodeUtil.createPath(session.getRootNode(), CONFIG_PATH.substring(1), NodeTypes.ContentNode.NAME);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (POLL_TIMEOUT.equals(entry.getKey())) {
                node.setProperty(entry.getKey(), Long.parseLong(entry.getValue()));
            } else {
                node.setProperty(entry.getKey(), entry.getValue());
            }
        }
        session.save();
    }
}
