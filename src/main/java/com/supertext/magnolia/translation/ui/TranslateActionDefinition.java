package com.supertext.magnolia.translation.ui;

import info.magnolia.ui.api.action.ConfiguredActionDefinition;

/**
 * Commit action of the {@code supertext-translation:translate} dialog.
 */
public class TranslateActionDefinition extends ConfiguredActionDefinition {

    public TranslateActionDefinition() {
        setImplementationClass(TranslateAction.class);
    }
}
