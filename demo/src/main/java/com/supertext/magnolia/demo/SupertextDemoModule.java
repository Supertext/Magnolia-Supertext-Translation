package com.supertext.magnolia.demo;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.magnolia.cms.security.MgnlUserManager;
import info.magnolia.cms.security.Realm;
import info.magnolia.cms.security.RoleManager;
import info.magnolia.cms.security.SecuritySupport;
import info.magnolia.cms.security.User;
import info.magnolia.cms.security.setup.AdminSetupManager;
import info.magnolia.cms.security.UserManager;
import info.magnolia.context.MgnlContext;
import info.magnolia.jcr.util.NodeTypes;
import info.magnolia.module.ModuleLifecycle;
import info.magnolia.module.ModuleLifecycleContext;
import info.magnolia.objectfactory.Components;
import info.magnolia.repository.RepositoryConstants;

/**
 * Prepares the Supertext demo on every start, so it is usable right after deployment:
 * <ul>
 * <li>creates the {@code DEMO_ADMIN_*} (superuser) and {@code DEMO_EDITOR_*} (editor) accounts
 * when they don't exist yet; existing accounts are never changed;</li>
 * <li>once the demo administrator exists, completes Magnolia's first-run admin setup (the
 * {@code /.magnolia/adminsetup} screen that asks for the built-in superuser's password), so the
 * screen no longer appears and the built-in {@code superuser} account stays disabled;</li>
 * <li>creates the English sample page {@code /supertext-demo} when it is missing.</li>
 * </ul>
 * The site and its languages (EN, DE-CH, FR-CH, IT-CH) are defined in
 * {@code supertext-demo/sites/supertext.yaml}. Passwords are never logged.
 */
public class SupertextDemoModule implements ModuleLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SupertextDemoModule.class);

    static final String SAMPLE_PAGE = "supertext-demo";
    static final List<String> EDITOR_ROLES = List.of("admincentral-editor", "pages-app-editor", "dam-app-core-editor", "dam-app-jcr-editor",
            "imaging-editor", "resources-editor", "security-base");
    private static final Pattern VALID_USERNAME = Pattern.compile(MgnlUserManager.VALID_USERNAME_REGEX);

    private final Function<String, String> environment;

    public SupertextDemoModule() {
        this(System::getenv);
    }

    SupertextDemoModule(Function<String, String> environment) {
        this.environment = environment;
    }

    @Override
    public void start(ModuleLifecycleContext context) {
        MgnlContext.doInSystemContext(() -> {
            try {
                boolean admin = ensureAccount("DEMO_ADMIN", List.of("superuser"), List.of());
                ensureAccount("DEMO_EDITOR", EDITOR_ROLES, List.of("editors"));
                if (admin) {
                    completeAdminSetup();
                }
                ensureSamplePage();
            } catch (RuntimeException | RepositoryException e) {
                log.error("Supertext demo setup failed; the demo still starts.", e);
            }
            return null;
        });
    }

    @Override
    public void stop(ModuleLifecycleContext context) {
    }

    /**
     * Creates the account named by {@code <prefix>_EMAIL} / {@code <prefix>_PASSWORD} if missing.
     *
     * @return true when the account exists afterwards
     */
    boolean ensureAccount(String prefix, List<String> roles, List<String> groups) {
        String emailVariable = prefix + "_EMAIL";
        String passwordVariable = prefix + "_PASSWORD";
        String email = value(emailVariable);
        String password = value(passwordVariable);
        if (email.isEmpty()) {
            log.info("Supertext demo: {} is not set, no {} account created.", prefix + "_EMAIL", prefix.equals("DEMO_ADMIN") ? "administrator" : "editor");
            return false;
        }
        UserManager users = SecuritySupport.Factory.getInstance().getUserManager(Realm.REALM_ADMIN.getName());
        if (users.getUser(email) != null) {
            log.info("Supertext demo: the account from {} exists already (left unchanged).", emailVariable);
            return true;
        }
        String problem = check(email, password);
        if (problem != null) {
            log.warn("Supertext demo: account from {} / {} skipped: {}.", emailVariable, passwordVariable, problem);
            return false;
        }
        User user = users.createUser(email, password);
        users.setProperty(user, "email", email);
        users.setProperty(user, "title", prefix.equals("DEMO_ADMIN") ? "Demo administrator" : "Demo editor");
        RoleManager roleManager = SecuritySupport.Factory.getInstance().getRoleManager();
        for (String role : roles) {
            if (roleManager.getRole(role) != null) {
                user = users.addRole(user, role);
            }
        }
        for (String group : groups) {
            try {
                if (SecuritySupport.Factory.getInstance().getGroupManager().getGroup(group) != null) {
                    user = users.addGroup(user, group);
                }
            } catch (info.magnolia.cms.security.AccessDeniedException e) {
                log.warn("Supertext demo: could not add group {}: {}", group, e.getMessage());
            }
        }
        log.info("Supertext demo: created the account from {} with roles {}.", emailVariable, roles);
        return true;
    }

    /** Magnolia's own rules: a non-blank password, a user name without special characters. */
    static String check(String userName, String password) {
        if (password == null || password.isBlank()) {
            return "the password is empty";
        }
        if (!VALID_USERNAME.matcher(userName).matches()) {
            return "the e-mail contains characters Magnolia doesn't allow in user names";
        }
        return null;
    }

    private void completeAdminSetup() throws RepositoryException {
        AdminSetupManager setup = Components.getComponent(AdminSetupManager.class);
        if (setup.isAdminSetupRequired()) {
            setup.completeSetup();
            log.info("Supertext demo: completed Magnolia's admin setup with the DEMO_ADMIN_* account; the built-in superuser stays disabled.");
        }
        // The built-in account must not be usable on a public demo, whatever its state.
        Session session = MgnlContext.getJCRSession(RepositoryConstants.USERS);
        String path = "/system/superuser";
        if (session.nodeExists(path)) {
            Node node = session.getNode(path);
            if (!node.hasProperty("enabled") || node.getProperty("enabled").getBoolean()) {
                node.setProperty("enabled", "false");
                session.save();
                log.info("Supertext demo: disabled the built-in 'superuser' account.");
            }
        }
    }

    private void ensureSamplePage() throws RepositoryException {
        Session session = MgnlContext.getJCRSession(RepositoryConstants.WEBSITE);
        if (session.getRootNode().hasNode(SAMPLE_PAGE)) {
            return;
        }
        SamplePage.create(session.getRootNode(), SAMPLE_PAGE);
        session.save();
        log.info("Supertext demo: created the sample page /{}.", SAMPLE_PAGE);
    }

    private String value(String name) {
        String v = environment.apply(name);
        return v == null ? "" : v.trim();
    }

    /** The English sample content (the docs screenshots' stand-in API knows its translations). */
    static final class SamplePage {
        private SamplePage() {
        }

        static Node create(Node root, String name) throws RepositoryException {
            Node page = root.addNode(name, NodeTypes.Page.NAME);
            page.setProperty(NodeTypes.Renderable.TEMPLATE, "supertext-demo:pages/page");
            page.setProperty("title", "Translating with Supertext");
            page.setProperty("intro", "Your content, in every language your customers speak");
            Node main = page.addNode("main", NodeTypes.Area.NAME);

            Node first = main.addNode("0", NodeTypes.Component.NAME);
            first.setProperty(NodeTypes.Renderable.TEMPLATE, "supertext-demo:components/text");
            first.setProperty("text", "<h2>One click, every language</h2>\n"
                    + "<p>Supertext translates your content with AI that was trained on the work of <strong>professional translators</strong>. "
                    + "Formatting, links and lists stay where they are, so a translated page looks just like the original.</p>\n"
                    + "<ul>\n<li>Translate a page into German, French and Italian in one go.</li>\n"
                    + "<li>Review each translation as a draft before you publish it.</li>\n</ul>\n"
                    + "<p>Read more about <a href=\"https://www.supertext.com\">Supertext</a> or try it on this page.</p>");

            Node second = main.addNode("1", NodeTypes.Component.NAME);
            second.setProperty(NodeTypes.Renderable.TEMPLATE, "supertext-demo:components/text");
            second.setProperty("headline", "About this demo");
            second.setProperty("text", "<p>This site shows the <em>Supertext Translation</em> module for Magnolia. "
                    + "Editors translate a page with a single action and then review the result.</p>");

            Node quote = main.addNode("2", NodeTypes.Component.NAME);
            quote.setProperty(NodeTypes.Renderable.TEMPLATE, "supertext-demo:components/quote");
            quote.setProperty("quote", "Good translations make a website feel at home in every country.");
            quote.setProperty("author", "Supertext");
            return page;
        }
    }
}
