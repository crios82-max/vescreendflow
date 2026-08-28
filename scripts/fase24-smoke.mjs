#!/usr/bin/env node
/** Fase 24 smoke — cat B1S8/B2S8, max avail torque, MAF IAT, aux input. */
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
  console.log('fase24-smoke →', BASE)
  runFaseFormulaChecks(24, assert)

  const deviceId = `fase24-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 24', app_version: '1.29.0', version_code: 131 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s8_temp_c: 900,
        cat_b1s8_alert_c: 850,
        catalyst_b1s8: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s8_alert'), 'cat_b1s8_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s8_temp_c: 900,
        cat_b2s8_alert_c: 850,
        catalyst_b2s8: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s8_alert'), 'cat_b2s8_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        max_avail_torque_pct: 10,
        speed_kmh: 30,
        max_avail_torque_alert_low: 20,
        max_avail_torque: { torque_pct: 10, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('max_avail_torque_alert'), 'max_avail_torque_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        maf_sensor_iat_c: 90,
        speed_kmh: 30,
        maf_iat_alert_c: 85,
        maf_iat: { temp_c: 90, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('maf_iat_alert'), 'maf_iat_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        aux_input_status: 15,
        speed_kmh: 30,
        aux_input_alert_mask: 0x0f,
        aux_input: { status_code: 15, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('aux_input_alert'), 'aux_input_alert')

  console.log('OK fase24-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
