#!/usr/bin/env node
/** Fase 28 smoke — cat B1S12/B2S12, STFT/LTFT bank 2. */
import { runFaseFormulaChecks } from './obd-pid-registry.mjs'

const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function assert(c, m) {
  if (!c) throw new Error(m)
}

async function j(path, init = {}) {
  const r = await fetch(BASE + path, {
    ...init,
    headers: { 'content-type': 'application/json', ...(init.headers || {}) },
  })
  const text = await r.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { ok: r.ok, status: r.status, body }
}

async function main() {
  console.log('fase28-smoke →', BASE)
  runFaseFormulaChecks(28, assert)

  const deviceId = `fase28-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 28', app_version: '1.49.0', version_code: 151 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s12_temp_c: 900,
        cat_b1s12_alert_c: 850,
        catalyst_b1s12: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s12_alert'), 'cat_b1s12_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s12_temp_c: 900,
        cat_b2s12_alert_c: 850,
        catalyst_b2s12: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s12_alert'), 'cat_b2s12_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_trim_stft_b2_pct: 22,
        speed_kmh: 30,
        stft_b2_alert_pct: 20,
        stft_b2: { trim_pct: 22, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('stft_b2_alert'), 'stft_b2_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_trim_ltft_b2_pct: 22,
        speed_kmh: 30,
        ltft_b2_alert_pct: 20,
        ltft_b2: { trim_pct: 22, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('ltft_b2_alert'), 'ltft_b2_alert')

  console.log('OK fase28-smoke · 4 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
