#!/usr/bin/env node
/** Fase 36 smoke — NOx corr S3/S4, cyl fuel, evap sys vapor, trans gear. */
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
  console.log('fase36-smoke →', BASE)
  runFaseFormulaChecks(36, assert)

  const deviceId = `fase36-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 36', app_version: '1.89.0', version_code: 191 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_corrected_s3_ppm: 900,
        speed_kmh: 30,
        nox_corr_s3_alert: 800,
        nox_corr_s3: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('nox_corr_s3_alert'), 'nox_corr_s3_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_corrected_s4_ppm: 900,
        speed_kmh: 30,
        nox_corr_s4_alert: 800,
        nox_corr_s4: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('nox_corr_s4_alert'), 'nox_corr_s4_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        cylinder_fuel_rate_mg: 56,
        speed_kmh: 30,
        cyl_fuel_alert_mg: 55,
        cyl_fuel: { mg_per_stroke: 56, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('cyl_fuel_alert'), 'cyl_fuel_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        evap_sys_vapor_pa: 9000,
        speed_kmh: 30,
        evap_sys_vapor_alert_pa: 8000,
        evap_sys_vapor: { pressure_pa: 9000, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('evap_sys_vapor_alert'), 'evap_sys_vapor_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        trans_gear_ratio: 3.6,
        speed_kmh: 30,
        trans_gear_alert_ratio: 3.5,
        trans_gear: { gear_ratio: 3.6, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('trans_gear_alert'), 'trans_gear_alert')

  console.log('OK fase36-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
