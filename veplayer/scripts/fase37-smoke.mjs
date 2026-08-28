#!/usr/bin/env node
/** Fase 37 smoke — OBD odo, ABS disable, fuel press A/B, reflash dist. */
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
  console.log('fase37-smoke →', BASE)
  runFaseFormulaChecks(37, assert)

  const deviceId = `fase37-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 37', app_version: '1.94.0', version_code: 196 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        obd_odometer_km: 165000,
        speed_kmh: 30,
        obd_odometer_alert_km: 160000,
        obd_odometer: { odometer_km: 165000, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('obd_odometer_alert'), 'obd_odometer_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        abs_disabled: 1,
        speed_kmh: 30,
        abs_disable: { disabled: true, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('abs_disable_alert'), 'abs_disable_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_press_a_kpa: 5000,
        speed_kmh: 30,
        fuel_press_a_alert_kpa: 4800,
        fuel_press_a: { pressure_kpa: 5000, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('fuel_press_a_alert'), 'fuel_press_a_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_press_b_kpa: 5000,
        speed_kmh: 30,
        fuel_press_b_alert_kpa: 4800,
        fuel_press_b: { pressure_kpa: 5000, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('fuel_press_b_alert'), 'fuel_press_b_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        reflash_dist_km: 11000,
        speed_kmh: 30,
        reflash_dist_alert_km: 10000,
        reflash_dist: { distance_km: 11000, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('reflash_dist_alert'), 'reflash_dist_alert')

  console.log('OK fase37-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
