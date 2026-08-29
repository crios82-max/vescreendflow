import { chromium } from 'playwright';
import path from 'path';
import fs from 'fs';

const OUT = '/opt/cursor/artifacts';
const PASS = 'movify123';

async function dismissNoise(page) {
  // Deny geolocation via context permissions instead when possible
  for (const text of ['No thanks', 'Never', 'Never allow', 'Allow this time']) {
    const btn = page.getByRole('button', { name: text });
    if (await btn.count().catch(() => 0)) {
      try { await btn.first().click({ timeout: 500 }); } catch { /* ignore */ }
    }
  }
}

async function pickSpanish(page) {
  const es = page.getByRole('button', { name: 'ES', exact: true });
  if (await es.count()) {
    await es.first().click();
    await page.waitForTimeout(400);
  }
}

async function shot(page, name) {
  const file = path.join(OUT, name);
  await page.screenshot({ path: file, fullPage: false });
  console.log('saved', file, fs.statSync(file).size);
}

async function login(page, email) {
  await page.getByLabel(/email/i).fill(email);
  await page.getByLabel(/contraseña|password/i).fill(PASS);
  await page.getByRole('button', { name: /entrar|sign in|iniciar/i }).click();
  await page.waitForTimeout(1500);
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    locale: 'es-VE',
    geolocation: { latitude: 10.4806, longitude: -66.9036 },
    permissions: [],
  });
  const page = await context.newPage();

  // --- ENTRY: passenger login ES ---
  await page.goto('http://localhost:5174/login', { waitUntil: 'networkidle' });
  await pickSpanish(page);
  await shot(page, 'pres_entry_passenger_es.png');

  // register
  await page.getByRole('link', { name: /crear cuenta|create account/i }).click();
  await page.waitForTimeout(600);
  await pickSpanish(page);
  await shot(page, 'pres_entry_passenger_register_es.png');

  // passenger home
  await page.goto('http://localhost:5174/login', { waitUntil: 'networkidle' });
  await pickSpanish(page);
  await login(page, 'pasajero@movify.demo');
  await page.waitForTimeout(1000);
  await dismissNoise(page);
  await shot(page, 'pres_internal_passenger.png');

  // fill places for confirm sheet
  const inputs = page.locator('input.place-input');
  const count = await inputs.count();
  if (count >= 2) {
    await inputs.nth(0).fill('Plaza Venezuela, Caracas');
    await inputs.nth(0).blur();
    await page.waitForTimeout(300);
    await inputs.nth(1).fill('Centro Comercial Sambil');
    await inputs.nth(1).blur();
    await page.waitForTimeout(800);
    await shot(page, 'pres_internal_passenger_booking.png');
  }

  // --- DRIVER ---
  await page.goto('http://localhost:5175/login', { waitUntil: 'networkidle' });
  await pickSpanish(page);
  await shot(page, 'pres_entry_driver_es.png');
  await login(page, 'conductor@movify.demo');
  await page.waitForTimeout(1200);
  await dismissNoise(page);
  await shot(page, 'pres_internal_driver.png');
  const online = page.getByRole('button', { name: /ir online|go online/i });
  if (await online.count()) {
    await online.first().click();
    await page.waitForTimeout(1000);
    await shot(page, 'pres_internal_driver_online.png');
  }

  // --- ADMIN ---
  await page.goto('http://localhost:5176/login', { waitUntil: 'networkidle' });
  await pickSpanish(page);
  await shot(page, 'pres_entry_admin_es.png');
  await login(page, 'admin@movify.demo');
  await page.waitForTimeout(1500);
  await dismissNoise(page);
  await shot(page, 'pres_internal_admin.png');
  const usersTab = page.getByRole('button', { name: /usuarios|users/i });
  if (await usersTab.count()) {
    await usersTab.first().click();
    await page.waitForTimeout(600);
    await shot(page, 'pres_internal_admin_users.png');
  }
  const sosTab = page.getByRole('button', { name: /sos/i });
  if (await sosTab.count()) {
    await sosTab.first().click();
    await page.waitForTimeout(500);
    await shot(page, 'pres_internal_admin_sos.png');
  }

  await browser.close();
  console.log('DONE');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
