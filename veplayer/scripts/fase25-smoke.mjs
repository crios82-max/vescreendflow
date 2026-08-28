#!/usr/bin/env node
/** Fase 25 smoke — cat B1S9/B2S9, ECT2, IAT2, turbo inlet. */
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
  console.log('fase25-smoke →', BASE)
  runFaseFormulaChecks(25, assert)

  const deviceId = `fase25-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 25', app_version: '1.34.0', version_code: 136 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s9_temp_c: 900,
        cat_b1s9_alert_c: 850,
        catalyst_b1s9: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s9_alert'), 'cat_b1s9_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s9_temp_c: 900,
        cat_b2s9_alert_c: 850,
        catalyst_b2s9: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s9_alert'), 'cat_b2s9_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        coolant_ect2_c: 110,
        ect2_alert_c: 105,
        ect2: { coolant_c: 110, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('ect2_alert'), 'ect2_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        iat_sensor2_c: 70,
        speed_kmh: 30,
        iat2_alert_c: 65,
        iat2: { temp_c: 70, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('iat2_alert'), 'iat2_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        turbo_inlet_kpa: 240,
        speed_kmh: 30,
        turbo_inlet_alert_kpa: 230,
        turbo_inlet: { pressure_kpa: 240, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('turbo_inlet_alert'), 'turbo_inlet_alert')

  console.log('OK fase25-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
