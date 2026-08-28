#!/usr/bin/env node
/** Fase 40 smoke — engine fuel rate g/s, exhaust flow, fuel sys use 1–3 (9D/9E/9F). */
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
  console.log('fase40-smoke →', BASE)
  runFaseFormulaChecks(40, assert)

  const deviceId = `fase40-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 40', app_version: '2.09.0', version_code: 211 }),
  })

  const fuelGps = 0x0640 / 200
  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        engine_fuel_rate_gps_rate: fuelGps,
        speed_kmh: 30,
        engine_fuel_rate_gps_alert: 5,
        engine_fuel_rate_gps: { rate_gps: fuelGps, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('engine_fuel_rate_gps_alert'), 'engine_fuel_rate_gps_alert')

  const exhFlow = 0x03e8 / 20
  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        engine_exhaust_flow_kgh: exhFlow,
        speed_kmh: 30,
        exhaust_flow_alert_kgh: 50,
        exhaust_flow: { flow_kgh: exhFlow, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('exhaust_flow_alert'), 'exhaust_flow_alert')

  const fsu1 = (0xe0 * 100) / 255
  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_sys_use_pct1: fsu1,
        speed_kmh: 30,
        fuel_sys_use1_alert_pct: 85,
        fuel_sys_use1: { use_pct: fsu1, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('fuel_sys_use1_alert'), 'fuel_sys_use1_alert')

  const fsu2 = (0xe0 * 100) / 255
  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_sys_use_pct2: fsu2,
        speed_kmh: 30,
        fuel_sys_use2_alert_pct: 85,
        fuel_sys_use2: { use_pct: fsu2, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('fuel_sys_use2_alert'), 'fuel_sys_use2_alert')

  const fsu3 = (0xe0 * 100) / 255
  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_sys_use_pct3: fsu3,
        speed_kmh: 30,
        fuel_sys_use3_alert_pct: 85,
        fuel_sys_use3: { use_pct: fsu3, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('fuel_sys_use3_alert'), 'fuel_sys_use3_alert')

  console.log('OK fase40-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
