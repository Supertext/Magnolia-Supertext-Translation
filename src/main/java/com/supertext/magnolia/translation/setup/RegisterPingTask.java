package com.supertext.magnolia.translation.setup;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.module.InstallContext;
import info.magnolia.module.delta.AbstractRepositoryTask;
import info.magnolia.module.delta.RegisterServletTask;
import info.magnolia.module.delta.TaskExecutionException;
import info.magnolia.module.model.ServletDefinition;

import com.supertext.magnolia.translation.reporting.PingServlet;
import com.supertext.magnolia.translation.reporting.VersionReports;

/**
 * Sets up the public ping route {@code /.supertext/ping}: registers {@link PingServlet} in the servlet filter and lets
 * the route past the filters that would ask for a login (URI security, admin setup), set a CSRF cookie or cache it. Adds only what is
 * missing, so it runs at every start and never changes an administrator's configuration.
 */
public class RegisterPingTask extends AbstractRepositoryTask {

    public static final String SERVLET_NAME = "SupertextPing";
    public static final String BYPASS_NAME = "supertextPing";
    static final String[] FILTERS = {"adminSetup", "csrfTokenSecurity", "uriSecurity", "cache"};

    public RegisterPingTask() {
        super("Supertext ping", "Registers the public ping route " + VersionReports.PING_PATH + " for the Supertext Commando Center.");
    }

    @Override
    protected void doExecute(InstallContext ctx) throws RepositoryException, TaskExecutionException {
        Session config = ctx.getConfigJCRSession();
        if (!config.nodeExists("/server/filters/servlets/" + SERVLET_NAME)) {
            ServletDefinition servlet = new ServletDefinition();
            servlet.setName(SERVLET_NAME);
            servlet.setClassName(PingServlet.class.getName());
            servlet.setComment("Supertext Commando Center ping (public, GET only)");
            servlet.addMapping(VersionReports.PING_PATH);
            new RegisterServletTask(servlet).execute(ctx);
        }
        for (String filter : FILTERS) {
            String path = "/server/filters/" + filter;
            if (!config.nodeExists(path)) {
                continue;
            }
            Node node = config.getNode(path);
            Node bypasses = node.hasNode("bypasses") ? node.getNode("bypasses") : node.addNode("bypasses", NodeTypes.ContentNode.NAME);
            if (!bypasses.hasNode(BYPASS_NAME)) {
                Node bypass = bypasses.addNode(BYPASS_NAME, NodeTypes.ContentNode.NAME);
                bypass.setProperty("class", "info.magnolia.voting.voters.URIStartsWithVoter");
                bypass.setProperty("pattern", VersionReports.PING_PATH);
            }
        }
    }
}
