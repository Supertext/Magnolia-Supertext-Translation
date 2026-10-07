/**
 * Browser helpers for Magnolia's AdminCentral (Vaadin), shared by e2e.mjs and screenshots.mjs.
 */

export async function signIn(page, base, user, password) {
  await page.goto(`${base}/.magnolia/admincentral`);
  await page.fill('#username', user);
  await page.fill('#password', password);
  await page.click('button[aria-label=login]');
  // Vaadin keeps a push connection open, so wait for the UI rather than for network idle.
  await page.waitForSelector('.v-app', { timeout: 60000 });
  await page.waitForTimeout(2500);
}

export async function openPages(page, base, path = '') {
  await page.goto(`${base}/.magnolia/admincentral#app:pages-app:browser;${path}:treeview:`);
  await page.waitForSelector('.v-actionbar', { timeout: 60000 });
  await page.waitForTimeout(2500);
}

/** Selects a top-level page in the Pages app by its node name. */
export async function selectPage(page, name) {
  const row = page.locator('tr', { hasText: `/${name}` }).first();
  await row.waitFor({ timeout: 30000 });
  const selected = await row.evaluate((r) => r.className.includes('selected'));
  if (!selected) {
    await row.click();
  }
  await page.waitForTimeout(1500);
}

export async function openTranslateDialog(page) {
  await page.locator('.v-actionbar').getByText('Translate with Supertext', { exact: true }).first().click();
  const dialog = page.locator('.v-window').filter({ hasText: 'Translate into' }).first();
  await dialog.waitFor({ timeout: 30000 });
  await page.waitForTimeout(800);
  return dialog;
}

/** Ticks the given languages (ids such as de_CH) and options in the open dialog. */
export async function fillTranslateDialog(dialog, languages, { overwrite = false, subpages = false } = {}) {
  for (const id of languages) {
    await dialog.locator('.v-select-optiongroup label', { hasText: `· ${id}` }).click();
  }
  if (overwrite) await dialog.getByText('Overwrite existing translations', { exact: true }).click();
  if (subpages) await dialog.getByText('Also translate all subpages', { exact: true }).click();
}

/** Runs the dialog and returns the notification text. */
export async function translate(page, languages, options = {}) {
  const dialog = await openTranslateDialog(page);
  await fillTranslateDialog(dialog, languages, options);
  await dialog.getByRole('button', { name: 'Translate', exact: true }).click();
  const note = page.locator('.v-Notification').first();
  await note.waitFor({ timeout: 120000 });
  return note.innerText();
}

/** Closes Vaadin notifications (they stay until clicked). */
export async function dismissNotifications(page) {
  for (const note of await page.locator('.v-Notification').all()) {
    await note.click().catch(() => {});
  }
  await page.waitForTimeout(500);
}
