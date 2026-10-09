# Developer guide — Supertext Translation for Magnolia

How the module is built, how to work on it, and how the demo is deployed.

## Architecture

```
Pages app "Translate with Supertext" (page list and page editor; openDialogAction, populate: false)
   └─ dialog supertext-translation:translate
        targetLanguages (checkBoxGroupField, datasource supertext-target-languages), overwrite, includeSubpages
        └─ commit: TranslateAction (editor's own JCR session)
             SiteLanguagesLookup → SiteLanguages (site i18n: default locale, targets, property names)
             PageTranslator, per target language, one after the other:
                collect page + areas/components (+ subpages) ─ DialogFieldResolver ─ HtmlDocument ─ SupertextClient
                write name_<locale>, NodeTypes.LastModified.update, session.save()

App launcher → Translation → Supertext (superuser)
   └─ SupertextSettingsSubApp / SupertextSettingsView ── SettingsStore ── config:/modules/supertext-translation/config
```

| Part | Role |
| --- | --- |
| `META-INF/magnolia/supertext-translation.xml` | Module `supertext-translation` (version from the `pom.xml`), depends on `core`, `ui-framework-jcr`, `pages-app`, `site`. Registers `SupertextSettings` and `SupertextClient` (singletons) and the datasource `supertext-target-languages` (component id `datasource-supertext-target-languages`: a `SelectFieldSupport` and a `ListDataProvider`, the same pattern as the Pages app's template chooser). |
| `supertext-translation/decorations/pages-app/apps/pages-app.yaml` | Adds the action `translateWithSupertext` to the browser (`crud` section, `edit` group) and to the page editor (`pageActions` section, `edit` group). Decorated lists merge by item `name`, so the item is appended to Magnolia's own groups. Availability: write permission, `mgnl:page`, not marked for deletion. |
| `supertext-translation/dialogs/translate.yaml` | The dialog. Not bound to the page (`populate: false`): `TranslateAction` reads the values with `EditorView.getPropertyValue`. |
| `supertext-translation/apps/supertext.yaml`, `decorations/admincentral/config.yaml` | The Supertext app (`ConfiguredAppDescriptor`, `BaseApp`, one sub-app), `superuser` only, in the launcher group `translation` (Magnolia 6.4's layout has no *Tools* group). |
| `api/SupertextClient` | HTTP protocol (below) on the JDK's `HttpClient`: multipart upload with quoted part names, polling, download, delete; 429 retries; prefix-tolerant key; https-only endpoint (http for localhost). No Magnolia dependencies, unit-tested against a local `HttpServer`. |
| `api/HtmlDocument` | Packs segments into one HTML document (`<div data-st-id="N">`) and back (jsoup, shipped with magnolia-core). Plain text is HTML-encoded (line breaks as `<br>`); rich text goes as it is, **one segment per field**. Magnolia's internal links (`${link:{…}}`) are in `href` attributes, which Supertext keeps. |
| `content/SiteLanguages`, `ui/SiteLanguagesLookup` | The page's site (`SiteManager.getAssignedSite(page).getI18n()`, falling back to the global `I18nContentSupport`). The default locale is the source; property names follow Magnolia's dialogs: the default locale is the bare name, others get `"_" + I18nContentSupport.getLanguageString(locale)` (`title_de_CH`). |
| `content/DialogFieldResolver` | Which properties to translate: the node's `mgnl:template` → `TemplateDefinition.getDialog()` → `DialogDefinitionRegistry` → a Magnolia 6 `FormDialogDefinition`; every `TextFieldDefinition` (plain) and `RichTextFieldDefinition` (HTML) with `i18n: true`. Composite fields: sub-fields on the same node (`CurrentItemProviderDefinition`) or in a child node (`NestedContentProvider`/`JcrChildNodeProviderDefinition`, `TranslatableField.childNode`); an `i18n` composite with child nodes (one child node per locale) is skipped. Plus the *Also translate* property names. |
| `content/PageTranslator` | Plain JCR. Collects the page, its non-page descendants (areas, components, nested nodes) and, if asked, subpages; skips `jcr:*` and `mgnl:deleted`. Per target: keeps non-empty translations unless `overwrite`, chunks documents at 900 000 characters, writes the results, marks changed nodes and their page modified, saves. A Supertext error for one language rolls that language back (`session.refresh(false)`) and goes on; a missing/rejected key aborts everything. Unit-tested with Magnolia's `MockNode`. |
| `SupertextSettings`, `ui/settings/SettingsStore` | Settings in effect, read from `config:/modules/supertext-translation/config` **on every use** (system context; Magnolia doesn't refresh the module bean when that node is created at runtime), with `SUPERTEXT_API_KEY` / `SUPERTEXT_API_ENDPOINT` (environment or system property) winning. `SupertextTranslationModule` is the module class and the settings bean. |
| `ui/TranslateAction` | Validates the form, resolves the languages, checks for a key (dialog stays open on key problems), runs `PageTranslator`, closes the dialog, refreshes the list (`DatasourceObservation.Manual.trigger`) and shows a Vaadin `Notification` with the summary. Messages come from the i18n bundle (see *Interface strings*) via `SimpleTranslator`; Supertext errors are shown through `ui/SupertextMessages`. |
| `ui/settings/SupertextSettingsView` | Plain Vaadin 8 form: key (never shown back; empty keeps it), endpoint, form of address, language mapping, excluded/additional properties, timeout; *Check connection* calls `GET features`; version from the jar manifest (`Implementation-Version`) linked to its GitHub release. |

## Supertext API protocol

AI file translation API v1, shared with all Supertext plugins:

1. `POST translate/ai/file` — multipart: `file` (`content.html`, part `Content-Type: text/html` exactly), `target_lang` (`de-CH`), `source_lang` (primary subtag, `en`), optional `politeness` (`more`/`less`) → `{file_id}`
2. `GET translate/ai/file/{id}/status` every 2 s until `done` (`error`, `limit_exceeded`, `deleted` fail; timeout from the settings, default 300 s)
3. `GET translate/ai/file/{id}/translation` → translated HTML
4. `DELETE translate/ai/file/{id}` (files also expire after 24 h)

Header `Authorization: Supertext-Auth-Key <key>` (a pasted prefix is stripped, exactly one is sent). HTTP 429 is retried up to 4 times (`Retry-After`, else 1/2/4/8 s with jitter). Languages are translated one after the other to stay under the per-second limit. The Supertext app calls `GET features` (free) to check the key.

## Local development

Java 21, Maven 3.9, Docker, Node 20+ (stand-in API, end-to-end check, screenshots). Magnolia's artifacts come from `https://nexus.magnolia-cms.com/content/groups/public` (declared in the poms), some Vaadin add-ons from `https://maven.vaadin.com/vaadin-addons`.

```bash
mvn install                                   # module + unit tests, into ~/.m2
mvn -f demo/pom.xml package                   # demo webapp: demo/target/ROOT.war
docker build -f demo/Dockerfile -t supertext-magnolia-demo .   # what Railway builds

cd tools/docs && npm install && STAND_IN_PREFIX=1 node stand-in.mjs &   # untranslated text comes back as "[de-CH] …"
docker run --rm -p 8080:8080 -v magnoliademo:/data --add-host host.docker.internal:host-gateway \
  -e DEMO_ADMIN_EMAIL=admin@example.com -e DEMO_ADMIN_PASSWORD=… \
  -e DEMO_EDITOR_EMAIL=editor@example.com -e DEMO_EDITOR_PASSWORD=… \
  -e SUPERTEXT_API_KEY=test -e SUPERTEXT_API_ENDPOINT=http://host.docker.internal:8765/v1/ \
  supertext-magnolia-demo
```

The first start installs Magnolia into the volume (about a minute); sign in at <http://localhost:8080/.magnolia/admincentral> with the e-mail address as user name. Remove the volume for a fresh start. After changing the module, rebuild the image (or the war and the image).

Magnolia's default log config sets `com` to `WARN`; the demo's `log4j2.xml` adds `com.supertext` at `INFO`.

### Interface strings

All texts the module shows live in Magnolia's message bundle `src/main/resources/supertext-translation/i18n/module-supertext-translation-messages_{en,de,fr,it}.properties` (UTF-8, as Magnolia 6 reads them; no `\u` escapes needed). YAML definitions reference the keys (`label: supertext-translation.dialog.label`), Java code uses `SimpleTranslator`. Every new or changed string goes into all four files in the same commit: formal address (Sie, vous, Lei), Magnolia's own terms in each language (page/Seite/page/pagina, component/Komponente/composant/componente, publish/veröffentlichen/publier/pubblicare), never translate "Supertext", placeholders or URLs, and French gets a non-breaking space before `? ! : ;` and inside `« »`.

- `SimpleTranslator` runs `MessageFormat` when there are arguments, so a message with `{0}` must not contain a straight apostrophe (`'` starts a quoted section): French and Italian use `’`.
- `api/` stays free of Magnolia classes: a `SupertextException` carries an English message (logs) plus a key under `supertext-translation.error.*`, its arguments and Supertext's answer (`detail`); `ui/SupertextMessages` turns it into the user's language and builds the API key hint (`supertext-translation.apiKey.hint` / `hintHtml`, `{0}` = sign-up URL, `{1}` = API key URL).
- `MessageBundlesTest` checks that all four files have the same keys and placeholders, that every key used in the Java code and YAML exists, the apostrophe rule, the French spacing and both links in the hint.

## Tests

```bash
mvn test        # HtmlDocument, SupertextClient (local HTTP server: protocol, auth header, 429, errors), settings, DialogFieldResolver, PageTranslator (MockNode), message bundles (MessageBundlesTest)
```

End to end against a running demo and the stand-in: `cd tools/docs && DEMO_URL=http://localhost:8080 DEMO_EDITOR_EMAIL=… DEMO_EDITOR_PASSWORD=… npm run e2e` signs in as the editor, translates the sample page into German in the Pages app and checks `/de_CH/supertext-demo.html`.

CI (`.github/workflows/ci.yml`): builds and tests the module, builds the demo image from `demo/Dockerfile`, starts it with test accounts and the stand-in, checks that both accounts were created and that no password appears in the log (the log is captured into a variable first; never `docker logs … | grep -q` under `pipefail`), and runs `e2e.mjs`.

Before a release, also check by hand (fresh demo, stand-in with `STAND_IN_PREFIX=1`): translate the sample page into all three languages from the page list and from the page editor (every text carries the marker, the quote's author and the link URL don't), translate German again without and with *Overwrite*, try *Also translate all subpages* with a subpage, and remove the key to see the *No Supertext API key* message.

## Docs screenshots

`docs/images/` is generated by `tools/docs/screenshots.mjs` from a **fresh** demo whose module talks to the stand-in **without** `STAND_IN_PREFIX`. The stand-in returns real German for the sample content (`tools/docs/sample-de.json`), so the guides never show placeholder text.

Start the demo with `SUPERTEXT_API_ENDPOINT` pointing to the stand-in but **without** `SUPERTEXT_API_KEY`: the script stores a dummy key in the Supertext app like an administrator (the stand-in accepts any key). The settings screenshot then shows *The endpoint comes from the SUPERTEXT_API_ENDPOINT environment variable* and the live endpoint as placeholder, never the local URL.

```bash
cd tools/docs && npm install && npx playwright install chromium && node stand-in.mjs &
# start the demo fresh (see Local development, minus SUPERTEXT_API_KEY), then:
DEMO_URL=http://localhost:8080 DEMO_ADMIN_EMAIL=… DEMO_ADMIN_PASSWORD=… DEMO_EDITOR_EMAIL=… DEMO_EDITOR_PASSWORD=… npm run screenshots
```

New texts in the sample: run the stand-in with `STAND_IN_DUMP=<dir>` and translate what it writes to `<dir>/de-CH.json` into `sample-de.json`. Avoid the *Definitions* app in scripts: in Magnolia 6.4.10 it sends a message without a type, which breaks AdminCentral's banner and leaves error notifications behind.

## Demo (Railway)

The public demo is a container built from `demo/Dockerfile`: Magnolia 6.4.10 Community Edition on Tomcat 11 / Java 21, a small demo module (site *supertext* with English, German, French and Italian (Switzerland), a page template with *Text* and *Quote* components, the sample page `/supertext-demo`) and this module. It runs on Railway in the `supertext-cms-demos` project, service `magnolia`, region EU West (Amsterdam): <https://magnolia-production-2b73.up.railway.app/> (AdminCentral at `/.magnolia/admincentral`, sign in with the e-mail address).

**Deploys:** Railway watches `main` and rebuilds on every push.

| File | Purpose |
| --- | --- |
| `demo/Dockerfile` | Builds the module (`mvn install`), then `demo/pom.xml` (war, with the module version passed as `supertext.version`), and runs the exploded `ROOT` webapp on `tomcat:11.0-jdk21-temurin` with a `RemoteIpValve` (Railway terminates TLS). |
| `demo/pom.xml` | `magnolia-community-webapp` (war overlay + pom) with Magnolia's bundle BOM imported (without it, Maven picks older Magnolia jars and Magnolia refuses to start), plus this module. |
| `demo/entrypoint.sh` | Creates the folders on `/data`, applies `PORT`, sets the heap (`MAGNOLIA_HEAP`, default 1536 MB), starts Tomcat. |
| `demo/src/main/webapp/WEB-INF/config/default/magnolia.properties` | Replaces the empty webapp's: repository (H2 + Jackrabbit), keys, cache and logs on `/data`; `magnolia.update.auto=true` (no install wizard); no samples. |
| `demo/src/main/webapp/WEB-INF/config/default/log4j2.xml` | Magnolia's default plus `com.supertext` at `INFO`. |
| `demo/src/main/java/…/SupertextDemoModule.java` | Module `supertext-demo`; on every start: creates the demo accounts if missing, completes Magnolia's first-run admin setup, keeps the built-in `superuser` disabled, creates the sample page if missing. |
| `demo/src/main/resources/supertext-demo/` | Site definition (`sites/supertext.yaml`, with the languages), templates, dialogs (fields `i18n: true`). |
| `demo/.env.example` | All variables. |

**State:** the JCR repository, keys and logs live on a Railway volume at `/data`. To reset the demo, delete the files on the volume and redeploy.

**Accounts** (created on every start if missing; existing ones are never changed; the e-mail is the user name):

| Variables | Account |
| --- | --- |
| `DEMO_ADMIN_EMAIL`, `DEMO_ADMIN_PASSWORD` | `superuser` role. Once it exists, the demo completes Magnolia's first-run admin setup (the `/.magnolia/adminsetup` screen that asks for the built-in superuser's password), so the screen no longer appears, and keeps the built-in `superuser` account disabled. Without these variables Magnolia shows that screen. |
| `DEMO_EDITOR_EMAIL`, `DEMO_EDITOR_PASSWORD` | Roles `admincentral-editor`, `pages-app-editor`, `dam-app-core-editor`, `dam-app-jcr-editor`, `imaging-editor`, `resources-editor`, `security-base` and the `editors` group: edits, translates and publishes pages. Magnolia has no per-language permissions, so the editor can work in every language. Magnolia Community Edition has no ready-made editor role, so this is the closest combination of its standard roles. |

Magnolia's own rules apply: the password must not be empty, and the user name (the e-mail) must not contain characters Magnolia forbids in user names (`MgnlUserManager.VALID_USERNAME_REGEX`). Otherwise the account is skipped with a warning naming the variable; the demo still starts. Passwords are never logged.

**Service variables:** `DEMO_*` (above), `SUPERTEXT_API_KEY`, optional `SUPERTEXT_API_ENDPOINT`, `PORT=8080` (the domain's target port), `MAGNOLIA_HEAP`, `RAILWAY_DOCKERFILE_PATH=demo/Dockerfile`. On Railway the `DEMO_*` variables reference the umbraco service's (`${{umbraco.DEMO_ADMIN_EMAIL}}` …) and `SUPERTEXT_API_KEY` the orchardcore service's, so the demos share one set.

## Dependency updates

Dependabot (`.github/dependabot.yml`) opens weekly pull requests: Maven minor and patch updates grouped into one, GitHub Actions in another, each major update on its own. Merge one when CI is green and it doesn't change what the module supports.

Everything Magnolia provides at runtime is ignored on purpose and updated by hand: the Magnolia artifacts (`info.magnolia*`: `magnolia.version` and the other Magnolia properties are the oldest Magnolia the module supports, see `docs/INSTALLATION.md` → Requirements), and Vaadin, Jackson and jsoup (`provided` scope, so they must be the versions that Magnolia release ships; a newer `jackson-databind` next to Magnolia's older `jackson-annotations` fails with `NoClassDefFoundError`). When you raise `magnolia.version`, also raise it in `demo/pom.xml` and set Vaadin, Jackson and jsoup to what `mvn dependency:tree -Dverbose -Dincludes=com.vaadin,com.fasterxml.jackson.core,org.jsoup` shows Magnolia bringing.

## Code quality and security checks

- **Checks** workflow (`.github/workflows/checks.yml`): [actionlint](https://github.com/rhysd/actionlint) and [zizmor](https://docs.zizmor.sh/) lint the workflows on every push and pull request. Dependency review fails a pull request that adds a package with a known vulnerability (moderate or worse). Actions are pinned to commit SHAs; Dependabot keeps the pins up to date. To run the workflow lint locally: `pip install actionlint-py zizmor`, then `actionlint` and `zizmor .github/workflows` in the repo root.
- **Links** workflow (`.github/workflows/links.yml`): [lychee](https://lychee.cli.rs/) checks the links in all Markdown files weekly and whenever docs change on `main`. Broken links open (or update) the issue "Broken links in the docs". Links that can't work from CI (local URLs, placeholders, pages behind a login) are excluded in `.lycheeignore`.
- Java and the JavaScript tools are analysed by CodeQL (see below), so this repo runs no separate static analyser.
- GitHub settings (set by Remy's setup script, not stored in the repo): **secret scanning with push protection** (a push containing a known token format is rejected; findings under *Security → Secret scanning*) and **CodeQL default setup** (findings under *Security → Code scanning* and as comments on pull requests; PHP isn't covered by CodeQL, which is why the PHP plugins run PHPStan).

Before starting work in this repo, look at its open findings: code scanning alerts, secret scanning alerts, Dependabot PRs and the "Broken links in the docs" issue.

## Releasing

Releases are published by `.github/workflows/release.yml` when the version is officially bumped; nobody tags or creates releases by hand.

1. Move the *Unreleased* entries in `CHANGELOG.md` under a new `## X.Y.Z — YYYY-MM-DD` section, and keep an empty *Unreleased* above it.
2. Set the same version in:
   - `pom.xml`: `<version>`, the module version (Magnolia reads it from `META-INF/magnolia/supertext-translation.xml`, filled in at build time; the Supertext app shows the jar's `Implementation-Version`);
   - `demo/pom.xml`: `<version>` and `<supertext.version>`.
3. Push to `main`. The workflow checks that the version files match `CHANGELOG.md`, builds the module with Maven, tags `vX.Y.Z` and creates the GitHub release with the CHANGELOG section as notes (0.x versions as pre-releases) and `magnolia-supertext-translation-X.Y.Z.jar` attached. A push that adds no new version does nothing, and a version that is already released is skipped. After fixing a failed run, start it again with *Run workflow* on the *Release* workflow.

The module is not on Maven Central; the installation guide explains how to install the release jar.

## Known limitations / roadmap

- Translation runs inside the dialog's request (up to the timeout per document and language). Planned: a background job with a Pulse message for large page trees.
- The source is always the site's default language.
- Legacy Magnolia 5 (compatibility) dialogs are not read; list their fields under *Also translate*.
- An `i18n` composite field stored as nested content (one child node per language) is not translated; multi-value fields (`jcrMultiField`, `multiValueField`) neither.
- Content apps other than Pages (Stories, content types) are not supported yet.
- The stored API key is plain text in the config workspace (superuser-only by default); prefer `SUPERTEXT_API_KEY` in production.
- No sync of later source changes; translate again (with *Overwrite*) to refresh a language.
- Human (professional) translation orders are not supported yet.
- Tested on Magnolia 6.4.10 CE with the stand-in API; not yet against the live API.
