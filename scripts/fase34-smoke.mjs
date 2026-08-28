#!/usr/bin/env node
/** Fase 34 smoke — O2 conc B1S4/B2S3/B2S4, DEF dose, NOx corr B1S1. */
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
  console.log('fase34-smoke →', BASE)
  runFaseFormulaChecks(34, assert)

  const deviceId = `fase34-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 34', app_version: '1.79.0', version_code: 181 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_conc_b1s4_pct: 20,
        speed_kmh: 30,
        o2_conc_b1s4_alert: 18,
        o2_conc_b1s4: { conc_pct: 20, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('o2_conc_b1s4_alert'), 'o2_conc_b1s4_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_conc_b2s3_pct: 20,
        speed_kmh: 30,
        o2_conc_b2s3_alert: 18,
        o2_conc_b2s3: { conc_pct: 20, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('o2_conc_b2s3_alert'), 'o2_conc_b2s3_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_conc_b2s4_pct: 20,
        speed_kmh: 30,
        o2_conc_b2s4_alert: 18,
        o2_conc_b2s4: { conc_pct: 20, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('o2_conc_b2s4_alert'), 'o2_conc_b2s4_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        def_dosing_cmd_pct: 95,
        speed_kmh: 30,
        def_dose_alert_pct: 90,
        def_dose: { dose_pct: 95, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('def_dose_alert'), 'def_dose_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_corrected_b1s1_ppm: 900,
        speed_kmh: 30,
        nox_corr_b1s1_alert: 800,
        nox_corr_b1s1: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('nox_corr_b1s1_alert'), 'nox_corr_b1s1_alert')

  console.log('OK fase34-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
