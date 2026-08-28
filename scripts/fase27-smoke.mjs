#!/usr/bin/env node
/** Fase 27 smoke — cat B1S11/B2S11, EGR actual, inject/fuel pressure control. */
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
  console.log('fase27-smoke →', BASE)
  runFaseFormulaChecks(27, assert)

  const deviceId = `fase27-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 27', app_version: '1.44.0', version_code: 146 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s11_temp_c: 900,
        cat_b1s11_alert_c: 850,
        catalyst_b1s11: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s11_alert'), 'cat_b1s11_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s11_temp_c: 900,
        cat_b2s11_alert_c: 850,
        catalyst_b2s11: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s11_alert'), 'cat_b2s11_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        actual_egr_pct: 95,
        speed_kmh: 30,
        egr_actual_alert_pct: 70,
        egr_actual: { egr_pct: 95, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('egr_actual_alert'), 'egr_actual_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        inject_ctrl_kpa: 13000,
        speed_kmh: 30,
        inject_ctrl: { pressure_kpa: 13000, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('inject_ctrl_alert'), 'inject_ctrl_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_ctrl_kpa: 10000,
        speed_kmh: 30,
        fuel_ctrl: { pressure_kpa: 10000, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('fuel_ctrl_alert'), 'fuel_ctrl_alert')

  console.log('OK fase27-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
