#!/usr/bin/env node
/** Fase 30 smoke — cat B1S14/B2S14, O2 lambda, PM B1/B2. */
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
  console.log('fase30-smoke →', BASE)
  runFaseFormulaChecks(30, assert)

  const deviceId = `fase30-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 30', app_version: '1.59.0', version_code: 161 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s14_temp_c: 900,
        cat_b1s14_alert_c: 850,
        catalyst_b1s14: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s14_alert'), 'cat_b1s14_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s14_temp_c: 900,
        cat_b2s14_alert_c: 850,
        catalyst_b2s14: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s14_alert'), 'cat_b2s14_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_lambda_b1: 1.22,
        speed_kmh: 30,
        o2_lambda_alert: 1.15,
        o2_lambda: { lambda: 1.22, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('o2_lambda_alert'), 'o2_lambda_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        pm_sensor_b1_pct: 90,
        speed_kmh: 30,
        pm_b1_alert_pct: 85,
        pm_b1: { pm_pct: 90, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('pm_b1_alert'), 'pm_b1_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        pm_sensor_b2_pct: 90,
        speed_kmh: 30,
        pm_b2_alert_pct: 85,
        pm_b2: { pm_pct: 90, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('pm_b2_alert'), 'pm_b2_alert')

  console.log('OK fase30-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
