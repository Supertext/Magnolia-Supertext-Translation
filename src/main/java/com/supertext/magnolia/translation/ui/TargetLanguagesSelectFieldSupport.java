package com.supertext.magnolia.translation.ui;

import java.util.Locale;

import jakarta.inject.Inject;

import info.magnolia.ui.field.SelectFieldSupport;

import com.vaadin.data.Converter;
import com.vaadin.data.Result;
import com.vaadin.data.ValueContext;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.ui.ItemCaptionGenerator;

/**
 * Shows each target language as "German (Switzerland) · de_CH".
 */
public class TargetLanguagesSelectFieldSupport implements SelectFieldSupport<String> {

    private final ListDataProvider<String> dataProvider;

    @Inject
    public TargetLanguagesSelectFieldSupport(ListDataProvider<String> dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public DataProvider<String, ?> getDataProvider() {
        return dataProvider;
    }

    @Override
    public ItemCaptionGenerator<String> getItemCaptionGenerator() {
        return id -> id == null || id.isEmpty() ? "" : SiteLanguagesLookup.displayName(toLocale(id)) + " · " + id;
    }

    @Override
    public Converter<String, String> defaultConverter() {
        return new Converter<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Result<String> convertToModel(String value, ValueContext context) {
                return Result.ok(value);
            }

            @Override
            public String convertToPresentation(String value, ValueContext context) {
                return value;
            }
        };
    }

    static Locale toLocale(String id) {
        String[] parts = id.split("[-_]", 3);
        return switch (parts.length) {
            case 1 -> new Locale(parts[0]);
            case 2 -> new Locale(parts[0], parts[1]);
            default -> new Locale(parts[0], parts[1], parts[2]);
        };
    }
}
