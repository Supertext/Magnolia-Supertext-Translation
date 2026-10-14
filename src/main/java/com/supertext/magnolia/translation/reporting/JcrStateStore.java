package com.supertext.magnolia.translation.reporting;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import info.magnolia.context.MgnlContext;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.jcr.util.NodeUtil;
import info.magnolia.repository.RepositoryConstants;

/**
 * Keeps the version reporter's JSON state in {@code config:/modules/supertext-translation/state} (property
 * {@code reporter}), next to the settings but in its own node, so saving the settings never touches it. Read and
 * written with system rights: the state is updated on editors' requests too.
 */
public class JcrStateStore implements VersionReports.StateStore {

    public static final String STATE_PATH = "/modules/supertext-translation/state";
    public static final String PROPERTY = "reporter";

    @Override
    public String load() throws RepositoryException {
        return MgnlContext.doInSystemContext(() -> {
            Session session = MgnlContext.getJCRSession(RepositoryConstants.CONFIG);
            if (!session.nodeExists(STATE_PATH)) {
                return null;
            }
            Node node = session.getNode(STATE_PATH);
            return node.hasProperty(PROPERTY) ? node.getProperty(PROPERTY).getString() : null;
        });
    }

    @Override
    public void save(String json) throws RepositoryException {
        MgnlContext.doInSystemContext(() -> {
            Session session = MgnlContext.getJCRSession(RepositoryConstants.CONFIG);
            Node node = NodeUtil.createPath(session.getRootNode(), STATE_PATH.substring(1), NodeTypes.ContentNode.NAME);
            node.setProperty(PROPERTY, json);
            session.save();
            return null;
        });
    }
}
