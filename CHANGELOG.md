# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Changed

- The module now talks to Supertext through the [Supertext Java SDK](https://github.com/Supertext/Supertext-API-SDK-and-Guide) 0.2.1, bundled in the module's jar: still one jar to install, no new libraries. Same protocol, retries and settings as before.
- **Language codes: no more guessing.** Only the codes Supertext offers are sent: a *Language codes* line wins, else a locale that already is a Supertext code (`de_CH` → `de-CH`, `es`), else the language is reported as unsupported and nothing is sent for it. A bare `de`, `fr`, `it`, `en` or `pt` no longer gets a default region: add a line such as `de=de-CH`. One line serves both directions (`de=de-CH` sends `de` as the source). Check the new language table in the Supertext app after updating.
- The form of address (formal/informal) is only sent for languages that offer it; English, Romansh, Albanian, Serbian and Swiss German are translated without it instead of failing.
- *Check connection* now checks the saved API key and shows the same result as after saving.
- If Supertext's answer is missing some texts, that language now fails with *Supertext’s answer was incomplete* and nothing is saved for it, instead of saving the rest and reporting *… texts came back unusable*. A page is no longer left half translated.
- The demo runs Magnolia 6.4.11 Community Edition (was 6.4.10); the module is tested with 6.4.11 too and still compiles against 6.4.10, the oldest version it supports.

### Fixed

- A target language Supertext doesn't know is now reported as an unsupported language pair (was: *The requested Supertext resource was not found*).

### Added

- Clearer message when Supertext rejects a language code: *Supertext does not accept this language pair (source …, target …)*, with a pointer to *Language codes*.
- New message *The language … has no Supertext language code, so nothing was sent*, naming the language and where to map it, and *Supertext doesn't offer the option … for …*, in all four languages.
- The Supertext app lists every site language with the code it is sent as (from *Language codes* or an exact match), whether it offers formal/informal, and marks languages that need a line.
- Automatic API key check after every save of the settings (and when the key in `SUPERTEXT_API_KEY` changes, or Supertext rejects it during a translation), shown in the Supertext app with the time, and with the account and API key links when the key is missing or rejected.
- **Send version information to Supertext** (on by default; `SUPERTEXT_VERSION_REPORTS=0` turns it off): sends the site URL and the versions of the module, Magnolia and Java, plus the result of the API key check, to Supertext when the settings are saved and after an update. No content, no API key and no personal data. See the installation guide.
- Public ping route `/.supertext/ping`, so Supertext can see that the installation is reachable (registered automatically at startup; answers 404 while version information is off).
- French and Italian interface (and German where it was missing): the dialog, messages and Supertext app follow the user's AdminCentral language. Supertext error messages (e.g. *Too many requests*, *Authentication failed*) and the API key hint are now translated too.

## 0.1.0 — 2026-10-07

### Added

- *Translate with Supertext* in the Pages app (page list and page editor): translate a page, its areas and components, and optionally its subpages, into the site's other languages; existing translations are kept unless *Overwrite existing translations* is ticked (with a warning in the dialog). A message sums up what was translated, kept or failed per language.
- Translates text and rich text fields marked `i18n: true` in the page and component dialogs, including composite fields stored on the node or as nested content, into Magnolia's language properties (`title_de_CH` …); the source is the site's default language. Pages are marked modified; nothing goes live until published.
- Supertext app (app launcher → Translation, superusers): API key with connection check, endpoint (https only, except localhost), form of address, language code mapping, excluded and additional properties, timeout, installed version with link to the release notes. `SUPERTEXT_API_KEY` / `SUPERTEXT_API_ENDPOINT` override the settings. The app and the "no API key" / "authentication failed" messages link to Supertext account signup and API key generation (Integrations → API, Admin role); same links in the installation guide, README and demo `.env.example`.
- English and German UI texts.
- Railway demo (Magnolia 6.4.10 CE, English → German, French, Italian (Switzerland), sample page) with demo accounts from `DEMO_*` variables; it completes Magnolia's first-run admin setup and keeps the built-in superuser disabled.
