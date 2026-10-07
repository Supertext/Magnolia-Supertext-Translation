#!/usr/bin/env node
/**
 * Takes the screenshots used in docs/USER_GUIDE.md and docs/INSTALLATION.md from the demo.
 *
 *   DEMO_URL=http://localhost:8080 \
 *   DEMO_EDITOR_EMAIL=… DEMO_EDITOR_PASSWORD=… DEMO_ADMIN_EMAIL=… DEMO_ADMIN_PASSWORD=… \
 *   node screenshots.mjs            # writes ../../docs/images/*.png
 *
 * The demo must call stand-in.mjs (SUPERTEXT_API_ENDPOINT=http://host.docker.internal:8765/v1/,
 * without STAND_IN_PREFIX) so the German result is a real translation of the sample page, and
 * run without SUPERTEXT_API_KEY: the script stores a dummy key in the Supertext app like an
 * administrator would (the stand-in accepts any key). 1× scale, cropped to the relevant part.
 * See docs/DEVELOPER.md -> Docs screenshots.
 */
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { dismissNotifications, fillTranslateDialog, openPages, openTranslateDialog, selectPage, signIn } from './magnolia.mjs';

const base = (process.env.DEMO_URL || 'http://localhost:8080').replace(/\/$/, '');
const need = ['DEMO_EDITOR_EMAIL', 'DEMO_EDITOR_PASSWORD', 'DEMO_ADMIN_EMAIL', 'DEMO_ADMIN_PASSWORD'].filter((v) => !process.env[v]);
if (need.length) {
  console.error(`Set ${need.join(', ')}.`);
  process.exit(2);
}
const images = new URL('../../docs/images/', import.meta.url).pathname;
mkdirSync(images, { recursive: true });
const shot = async (target, name, options = {}) => {
  await target.screenshot({ path: images + name, ...options });
  console.log('wrote docs/images/' + name);
};
const box = async (locator, pad = 12) => {
  const b = await locator.boundingBox();
  return { x: Math.max(0, b.x - pad), y: Math.max(0, b.y - pad), width: b.width + 2 * pad, height: b.height + 2 * pad };
};

const browser = await chromium.launch();
const viewport = { width: 1400, height: 1000 };

// Administrator: store a (dummy) API key, as described in the installation guide.
let context = await browser.newContext({ viewport, deviceScaleFactor: 1 });
let page = await context.newPage();
await signIn(page, base, process.env.DEMO_ADMIN_EMAIL, process.env.DEMO_ADMIN_PASSWORD);
await page.goto(`${base}/.magnolia/admincentral#app:supertext:settings`);
await page.waitForSelector('.supertext-settings', { timeout: 60000 });
await page.waitForTimeout(1500);
const keyField = page.locator('.supertext-settings input[type=password]');
if (await keyField.isEnabled() && !(await keyField.getAttribute('placeholder'))) {
  await keyField.fill('Supertext-Auth-Key docs-screenshots');
  await page.getByText('Save', { exact: true }).click();
  await page.locator('.v-Notification').first().waitFor();
}
await context.close();

// Editor: Pages app, dialog, result, retranslation.
context = await browser.newContext({ viewport, deviceScaleFactor: 1 });
page = await context.newPage();
await signIn(page, base, process.env.DEMO_EDITOR_EMAIL, process.env.DEMO_EDITOR_PASSWORD);
await openPages(page, base);
await selectPage(page, 'supertext-demo');
await page.mouse.move(0, 0);
await shot(page, 'pages-action.png', { clip: { x: 0, y: 66, width: 1400, height: 690 } });

let dialog = await openTranslateDialog(page);
await fillTranslateDialog(dialog, ['de_CH', 'fr_CH'], { overwrite: true });
await shot(page, 'translate-dialog.png', { clip: await box(dialog, 0) });
await dialog.locator('.v-select-optiongroup label', { hasText: '· fr_CH' }).click(); // German only
await dialog.getByRole('button', { name: 'Translate', exact: true }).click();
let note = page.locator('.v-Notification').first();
await note.waitFor({ timeout: 120000 });
await page.waitForTimeout(500);
await shot(page, 'translate-done.png', { clip: await box(note, 24) });
await dismissNotifications(page);

// Translating again without "Overwrite" keeps what is there.
dialog = await openTranslateDialog(page);
await fillTranslateDialog(dialog, ['de_CH']);
await dialog.getByRole('button', { name: 'Translate', exact: true }).click();
note = page.locator('.v-Notification').first();
await note.waitFor({ timeout: 120000 });
await page.waitForTimeout(500);
await shot(page, 'translate-kept.png', { clip: await box(note, 24) });
await dismissNotifications(page);

// The German page in the page editor (language selector at the bottom left).
await page.goto(`${base}/.magnolia/admincentral#app:pages-app:detail;/supertext-demo:edit`);
await page.waitForSelector('.language-selector', { timeout: 60000 });
await page.waitForTimeout(3000);
await page.locator('.language-selector').click();
await page.waitForTimeout(600);
await shot(page, 'languages.png', { clip: await box(page.locator('.v-filterselect-suggestpopup').first(), 40) });
await page.locator('.v-filterselect-suggestpopup').getByText(/German/).click();
await page.waitForTimeout(4000);
await page.mouse.move(0, 0);
await shot(page, 'translated-de.png', { clip: { x: 0, y: 66, width: 1400, height: 934 } });
await context.close();

// Administrator: the app launcher and the Supertext app (settings).
context = await browser.newContext({ viewport: { width: 1400, height: 1150 }, deviceScaleFactor: 1 });
page = await context.newPage();
await signIn(page, base, process.env.DEMO_ADMIN_EMAIL, process.env.DEMO_ADMIN_PASSWORD);
await dismissNotifications(page);
await page.goto(`${base}/.magnolia/admincentral#app-launcher`);
await page.waitForTimeout(2500);
await shot(page, 'app-launcher.png', { clip: await box(page.locator('.app-list.section', { hasText: 'Supertext' }).first(), 8) });
await page.goto(`${base}/.magnolia/admincentral#app:supertext:settings`);
await page.waitForSelector('.supertext-settings', { timeout: 60000 });
await page.waitForTimeout(2000);
await shot(page, 'settings.png', { clip: { x: 0, y: 66, width: 880, height: 1010 } });
await browser.close();
