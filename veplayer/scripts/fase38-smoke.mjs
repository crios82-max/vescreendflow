#!/usr/bin/env node
/** Fase 38 smoke — fuel level A/B, EPCS time/count, NOx/PCD lamp. */
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
  console.log('fase38-smoke →', BASE)
  runFaseFormulaChecks(38, assert)

  const deviceId = `fase38-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 38', app_version: '1.99.0', version_code: 201 }),
  })

  const fuelAPct = (0x14 * 100) / 255
  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_level_input_a_pct: fuelAPct,
        speed_kmh: 30,
        fuel_level_a_alert_pct: 8,
        fuel_level_a: { level_pct: fuelAPct, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('fuel_level_a_alert'), 'fuel_level_a_alert')

  const fuelBPct = (0x0a * 100) / 255
  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_level_input_b_pct: fuelBPct,
        speed_kmh: 30,
        fuel_level_b_alert_pct: 8,
        fuel_level_b: { level_pct: fuelBPct, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('fuel_level_b_alert'), 'fuel_level_b_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        epcs_diag_time_sec: 200,
        speed_kmh: 30,
        epcs_time_alert_sec: 180,
        epcs_time: { time_sec: 200, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('epcs_time_alert'), 'epcs_time_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        epcs_diag_count: 144,
        speed_kmh: 30,
        epcs_count_alert: 80,
        epcs_count: { count: 144, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('epcs_count_alert'), 'epcs_count_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_pcd_lamp_on: 1,
        speed_kmh: 30,
        nox_pcd_lamp: { lamp_on: true, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('nox_pcd_lamp_alert'), 'nox_pcd_lamp_alert')

  console.log('OK fase38-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
