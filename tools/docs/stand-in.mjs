#!/usr/bin/env node
/**
 * Stand-in for the Supertext AI file translation API, for tests and docs screenshots.
 *
 * Texts listed in sample-<lang>.json (e.g. sample-fr.json, keyed by the English
 * source) come back translated. Anything else comes back unchanged, or prefixed
 * with "[<target_lang>] " when STAND_IN_PREFIX=1, which makes untranslated text
 * easy to spot in tests. STAND_IN_DUMP=<dir> writes each submitted document's
 * untranslated segments to <dir>/<target_lang>.json (to extend the samples).
 * Listens on :8765 (PORT), accepts any API key. Like the live API it accepts only the language codes
 * of GET features (targets regional only for de/en/fr/it/pt, bare for most others) and answers
 * POST /report like the Commando Center (point SUPERTEXT_REPORT_URL at it in tests).
 */
import http from 'node:http';
import { randomBytes } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';

const norm = (s) => s.replace(/&apos;|&#39;/g, "'").replace(/&nbsp;/g, ' ').replace(/<br\s*\/?>/g, '<br>').replace(/\s+/g, ' ').trim();
const samples = new Map();
const sampleFor = (lang) => {
  const primary = lang.toLowerCase().split(/[-_]/)[0];
  if (!samples.has(primary)) {
    const file = new URL(`sample-${primary}.json`, import.meta.url);
    const entries = existsSync(file) ? Object.entries(JSON.parse(readFileSync(file))) : [];
    samples.set(primary, new Map(entries.map(([en, tr]) => [norm(en), tr])));
  }
  return samples.get(primary);
};
const files = new Map();
// The live API's languages (GET features, October 2026; the SDK's spec/languages.json).
const TARGETS = new Set('bg cs da de-AT de-CH de-DE el en-GB en-US es fi fr-CH fr-FR gsw-u-sd-chbe gsw-u-sd-chzh hr hu it-CH it-IT ja ko nb nl pl pt-BR pt-PT rm ru sk sl sq sr-Cyrl sr-Latn sv tr zh-Hans zh-Hant'.split(' '));
const SOURCES = new Set([...TARGETS].map((t) => t.split('-')[0]));
const NO_POLITENESS = /^(en|gsw|rm|sq|sr)(-|$)/;
const reports = [];
const prefix = process.env.STAND_IN_PREFIX === '1';

http
  .createServer(async (req, res) => {
    const path = new URL(req.url, 'http://x').pathname.replace(/^\/v1/, '');
    const send = (status, body, type = 'application/json') => {
      res.writeHead(status, { 'Content-Type': type });
      res.end(type === 'application/json' ? JSON.stringify(body) : body);
    };
    if (req.method === 'POST' && path === '/report') {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      try {
        const report = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        reports.push(report);
        console.log(`REPORT ${report.event} ${report.site_url} ${report.plugin?.name} ${report.plugin?.version} key=${report.api_key_check?.status ?? '-'}`);
        return send(202, { ok: true });
      } catch {
        return send(400, { ok: false, error: 'not JSON' });
      }
    }
    if (req.method === 'GET' && path === '/reports') return send(200, reports);
    if (!/^Supertext-Auth-Key \S+$/.test(req.headers.authorization || '')) return send(401, { error: 'missing key' });
    if (path === '/features') return send(200, {});
    if (req.method === 'POST' && path === '/translate/ai/file') {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      let form;
      try {
        form = await new Request('http://x', { method: 'POST', headers: req.headers, body: Buffer.concat(chunks) }).formData();
      } catch (e) {
        console.log('POST with unreadable multipart body:', e.message);
        return send(400, { error: 'INVALID_MULTIPART' });
      }
      // Like the live API: only the codes of GET features (de-CH, es; not de or es-ES), sources bare (en).
      const target = String(form.get('target_lang') ?? '');
      const source = form.get('source_lang');
      if (!TARGETS.has(target) || (source !== null && !SOURCES.has(String(source)))) {
        console.log(`POST rejected: INVALID_LANGUAGE_PAIR source_lang=${source} target_lang=${target}`);
        return send(404, { error_code: 'INVALID_LANGUAGE_PAIR', source_lang: source ?? '', target_lang: target, detail: `Invalid language pair ${source ?? 'auto'} -> ${target}` });
      }
      if (form.get('politeness') !== null && NO_POLITENESS.test(target)) {
        console.log(`POST rejected: FEATURE_ERROR politeness for ${target}`);
        return send(400, { error_code: 'FEATURE_ERROR', feature: 'politeness', target_lang: target });
      }
      const id = randomBytes(6).toString('hex');
      files.set(id, { html: await form.get('file').text(), lang: String(form.get('target_lang') || '') });
      console.log(`POST target_lang=${form.get('target_lang')} source_lang=${form.get('source_lang')} politeness=${form.get('politeness') ?? '-'} -> ${id}`);
      return send(200, { file_id: id });
    }
    const match = path.match(/file\/([a-f0-9]+)(\/status|\/translation)?$/);
    if (!match || !files.has(match[1])) return send(404, {});
    if (req.method === 'DELETE') { files.delete(match[1]); return send(200, {}); }
    if (match[2] === '/status') return send(200, { status: 'done' });
    const { html, lang } = files.get(match[1]);
    const sample = sampleFor(lang);
    if (process.env.STAND_IN_DUMP) {
      const missing = {};
      for (const [, inner] of html.matchAll(/<div data-st-id="\d+">([\s\S]*?)<\/div>\n/g)) if (!sample.has(norm(inner))) missing[inner] = '';
      writeFileSync(`${process.env.STAND_IN_DUMP}/${lang}.json`, JSON.stringify(missing, null, 1));
    }
    const out = html.replace(/(<div data-st-id="\d+">)([\s\S]*?)(<\/div>\n)/g, (all, open, inner, close) => {
      const translated = sample.get(norm(inner));
      if (translated !== undefined) return open + translated + close;
      return prefix ? open + inner.replace(/^((?:\s*<[^>]+>)*)/, `$1[${lang}] `) + close : all;
    });
    send(200, out, 'text/html');
  })
  .listen(Number(process.env.PORT || 8765), () => console.log(`Stand-in API on http://127.0.0.1:${process.env.PORT || 8765}/v1/`));
