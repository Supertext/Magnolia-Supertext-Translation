package com.supertext.magnolia.translation.ui.settings;

import jakarta.inject.Inject;

import info.magnolia.ui.api.app.SubAppContext;
import info.magnolia.ui.framework.app.BaseSubApp;

/**
 * The only sub-app of the Supertext app (app launcher → Translation): module settings.
 */
public class SupertextSettingsSubApp extends BaseSubApp<SupertextSettingsView> {

    @Inject
    public SupertextSettingsSubApp(SubAppContext subAppContext, SupertextSettingsView view) {
        super(subAppContext, view);
    }

    @Override
    public String getCaption() {
        return "Supertext";
    }
}
