#!/usr/bin/env node
/** Fase 45 smoke — HEV min temp / discharge limit / energy BB–BD from 01B7/BA/BB–BD. */
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
  console.log('fase45-smoke →', BASE)
  runFaseFormulaChecks(45, assert)

  const deviceId = `fase45-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 45', app_version: '2.34.0', version_code: 236 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_cell_min_temp_c: -8,
        hv_cell_min_t_alert_c: -5,
        hv_cell_min_temp: { temp_c: -8, band: 'alert', show_warn: true, label: 'HvMin · -8°' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('hv_cell_min_temp_alert'), 'hv_cell_min_temp_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_dis_limit_a: 10,
        speed_kmh: 30,
        hv_dis_speed_min_kmh: 15,
        hv_dis_alert_a: 25,
        hv_dis_limit: { current_a: 10, band: 'alert', show_warn: true, speed_kmh: 30, label: 'HvDis · 10A' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('hv_dis_limit_alert'), 'hv_dis_limit_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_enrg_in_kwh: 30000,
        hv_enrg_in_alert_kwh: 25000,
        hv_enrg_in: { kwh: 30000, band: 'alert', show_warn: true, label: 'HvIn · 30000kWh' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('hv_enrg_in_alert'), 'hv_enrg_in_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_enrg_out_kwh: 28000,
        hv_enrg_out_alert_kwh: 25000,
        hv_enrg_out: { kwh: 28000, band: 'alert', show_warn: true, label: 'HvOut · 28000kWh' },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('hv_enrg_out_alert'), 'hv_enrg_out_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_enrg_tput_wh: 6e7,
        hv_enrg_tput_alert_wh: 5e7,
        hv_enrg_tput: { wh: 6e7, band: 'alert', show_warn: true, label: 'HvTput · 60000kWh' },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('hv_enrg_tput_alert'), 'hv_enrg_tput_alert')

  console.log('OK fase45-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
