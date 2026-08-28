#!/usr/bin/env node
/** Fase 41 smoke — WWH MI counters, fuel sys control, hybrid batt voltage (90/91/92/93/9A). */
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
  console.log('fase41-smoke →', BASE)
  runFaseFormulaChecks(41, assert)

  const deviceId = `fase41-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 41', app_version: '2.14.0', version_code: 216 }),
  })

  const wwhCmiH = 144
  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        wwh_obd_continuous_mi_hours: wwhCmiH,
        wwh_cont_mi_alert_h: 48,
        wwh_continuous_mi: { mi_hours: wwhCmiH, band: 'alert', show_warn: true, label: 'WwhCMI · 144h' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('wwh_continuous_mi_alert'), 'wwh_continuous_mi_alert')

  const wwhB1H = 300
  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        wwh_obd_ecu_b1_hours: wwhB1H,
        wwh_ecu_b1_alert_h: 200,
        wwh_ecu_b1: { b1_hours: wwhB1H, band: 'alert', show_warn: true, label: 'WwhB1 · 300h' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('wwh_ecu_b1_alert'), 'wwh_ecu_b1_alert')

  const fscCount = 0
  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_sys_ctl_closed_count: fscCount,
        speed_kmh: 30,
        fuel_sys_ctl_alert_min: 2,
        fuel_sys_ctl_speed_min_kmh: 20,
        fuel_sys_ctl: { closed_count: fscCount, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('fuel_sys_ctl_alert'), 'fuel_sys_ctl_alert')

  const wwhCumH = 300
  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        wwh_obd_cumulative_mi_hours: wwhCumH,
        wwh_cum_mi_alert_h: 200,
        wwh_cumulative_mi: { mi_hours: wwhCumH, band: 'alert', show_warn: true, label: 'WwhCum · 300h' },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('wwh_cumulative_mi_alert'), 'wwh_cumulative_mi_alert')

  const hevV = 160
  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hybrid_ev_batt_voltage_v: hevV,
        hev_volt_alert_v: 260,
        hybrid_ev_batt: { volts: hevV, band: 'alert', show_warn: true, label: 'HevV · 160V' },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('hybrid_ev_batt_alert'), 'hybrid_ev_batt_alert')

  console.log('OK fase41-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
