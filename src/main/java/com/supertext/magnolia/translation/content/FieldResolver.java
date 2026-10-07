package com.supertext.magnolia.translation.content;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Decides which properties of a node are translated.
 */
@FunctionalInterface
public interface FieldResolver {

    List<TranslatableField> fieldsOf(Node node) throws RepositoryException;
}
