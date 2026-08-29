#!/usr/bin/env node
/** Fase 43 smoke — HEV block 01B2–B7 (SOH, temp, current, voltage, cell max). */
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
  console.log('fase43-smoke →', BASE)
  runFaseFormulaChecks(43, assert)

  const deviceId = `fase43-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 43', app_version: '2.24.0', version_code: 226 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_batt_soh_pct: 40,
        hv_soh_alert_pct: 50,
        hv_batt_soh: { soh_pct: 40, band: 'alert', show_warn: true, label: 'HySOH · 40%' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('hv_batt_soh_alert'), 'hv_batt_soh_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hvess_temp_c: 60,
        hvess_temp_alert_c: 55,
        hvess_temp: { temp_c: 60, band: 'alert', show_warn: true, label: 'HvTemp · 60°' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('hvess_temp_alert'), 'hvess_temp_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hvess_current_a: 200,
        speed_kmh: 30,
        hvess_cur_speed_min_kmh: 10,
        hvess_cur_alert_a: 180,
        hvess_current: { current_a: 200, band: 'alert', show_warn: true, speed_kmh: 30, label: 'HvCur · 200A' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('hvess_current_alert'), 'hvess_current_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hvess_voltage_v: 250,
        hvess_volt_alert_v: 260,
        hvess_voltage: { volts: 250, band: 'alert', show_warn: true, label: 'HvV6 · 250V' },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('hvess_voltage_alert'), 'hvess_voltage_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_cell_max_temp_c: 50,
        hv_cell_max_alert_c: 48,
        hv_cell_max: { temp_c: 50, band: 'alert', show_warn: true, label: 'HvMax · 50°' },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('hv_cell_max_alert'), 'hv_cell_max_alert')

  console.log('OK fase43-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
