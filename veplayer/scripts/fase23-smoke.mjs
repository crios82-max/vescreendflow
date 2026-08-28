#!/usr/bin/env node
/** Fase 23 smoke — cat B1S7/B2S7, fuel type, max lambda, max MAF. */
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
  console.log('fase23-smoke →', BASE)
  runFaseFormulaChecks(23, assert)

  const deviceId = `fase23-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 23', app_version: '1.24.0', version_code: 126 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s7_temp_c: 900,
        cat_b1s7_alert_c: 850,
        catalyst_b1s7: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s7_alert'), 'cat_b1s7_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s7_temp_c: 900,
        cat_b2s7_alert_c: 850,
        catalyst_b2s7: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s7_alert'), 'cat_b2s7_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_type_code: 4,
        speed_kmh: 30,
        fuel_type_expected: 1,
        fuel_type: { type_code: 4, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('fuel_type_alert'), 'fuel_type_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        max_equiv_ratio: 0.75,
        max_equiv_alert_low: 0.82,
        max_equiv: { ratio: 0.75, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('max_equiv_alert'), 'max_equiv_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        max_maf_gps: 10,
        max_maf_alert_low_gps: 15,
        max_maf: { maf_gps: 10, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('max_maf_alert'), 'max_maf_alert')

  console.log('OK fase23-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
