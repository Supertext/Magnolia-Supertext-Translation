# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

### Added

- French and Italian interface (and German where it was missing): the dialog, messages and Supertext app follow the user's AdminCentral language. Supertext error messages (e.g. *Too many requests*, *Authentication failed*) and the API key hint are now translated too.

## 0.1.0 — 2026-10-07

### Added

- *Translate with Supertext* in the Pages app (page list and page editor): translate a page, its areas and components, and optionally its subpages, into the site's other languages; existing translations are kept unless *Overwrite existing translations* is ticked (with a warning in the dialog). A message sums up what was translated, kept or failed per language.
- Translates text and rich text fields marked `i18n: true` in the page and component dialogs, including composite fields stored on the node or as nested content, into Magnolia's language properties (`title_de_CH` …); the source is the site's default language. Pages are marked modified; nothing goes live until published.
- Supertext app (app launcher → Translation, superusers): API key with connection check, endpoint (https only, except localhost), form of address, language code mapping, excluded and additional properties, timeout, installed version with link to the release notes. `SUPERTEXT_API_KEY` / `SUPERTEXT_API_ENDPOINT` override the settings. The app and the "no API key" / "authentication failed" messages link to Supertext account signup and API key generation (Integrations → API, Admin role); same links in the installation guide, README and demo `.env.example`.
- English and German UI texts.
- Railway demo (Magnolia 6.4.10 CE, English → German, French, Italian (Switzerland), sample page) with demo accounts from `DEMO_*` variables; it completes Magnolia's first-run admin setup and keeps the built-in superuser disabled.
