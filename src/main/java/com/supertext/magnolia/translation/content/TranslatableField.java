package com.supertext.magnolia.translation.content;

/**
 * A property of a page, area or component that holds translatable text.
 *
 * @param name      base property name (the default-language value is stored under it)
 * @param html      true for rich text (sent as HTML), false for plain text
 * @param childNode relative path of the child node that holds the property (composite fields
 *                  stored as nested content), empty for the node itself
 */
public record TranslatableField(String name, boolean html, String childNode) {

    public TranslatableField(String name, boolean html) {
        this(name, html, "");
    }

    public TranslatableField {
        childNode = childNode == null ? "" : childNode;
    }
}
