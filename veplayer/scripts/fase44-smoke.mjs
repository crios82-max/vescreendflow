#!/usr/bin/env node
/** Fase 44 smoke — HEV balance/cell V/power/charge limit from 01B8–BA. */
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
  console.log('fase44-smoke →', BASE)
  runFaseFormulaChecks(44, assert)

  const deviceId = `fase44-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 44', app_version: '2.29.0', version_code: 231 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_bal_hours: 250,
        hv_bal_alert_h: 200,
        hv_bal: { hours: 250, band: 'alert', show_warn: true, label: 'HvBal · 250h' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('hv_bal_hours_alert'), 'hv_bal_hours_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_cell_min_voltage_v: 2.9,
        hv_cell_min_v_alert_v: 3.0,
        hv_cell_min_volt: { volts: 2.9, band: 'alert', show_warn: true, label: 'HvMinV · 2.90V' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('hv_cell_min_volt_alert'), 'hv_cell_min_volt_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_cell_max_voltage_v: 4.3,
        hv_cell_max_v_alert_v: 4.25,
        hv_cell_max_volt: { volts: 4.3, band: 'alert', show_warn: true, label: 'HvMaxV · 4.30V' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('hv_cell_max_volt_alert'), 'hv_cell_max_volt_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_pwr_avail_pct: 10,
        hv_pwr_alert_pct: 15,
        hv_pwr_avail: { pct: 10, band: 'alert', show_warn: true, label: 'HvPwr · 10%' },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('hv_pwr_avail_alert'), 'hv_pwr_avail_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_chg_limit_a: 10,
        speed_kmh: 30,
        hv_chg_speed_min_kmh: 15,
        hv_chg_alert_a: 15,
        hv_chg_limit: { current_a: 10, band: 'alert', show_warn: true, speed_kmh: 30, label: 'HvChg · 10A' },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('hv_chg_limit_alert'), 'hv_chg_limit_alert')

  console.log('OK fase44-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
