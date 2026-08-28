#!/usr/bin/env node
/** Fase 26 smoke — cat B1S10/B2S10, EGR temp, diesel IAF, throttle actuator. */
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
  console.log('fase26-smoke →', BASE)
  runFaseFormulaChecks(26, assert)

  const deviceId = `fase26-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 26', app_version: '1.39.0', version_code: 141 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s10_temp_c: 900,
        cat_b1s10_alert_c: 850,
        catalyst_b1s10: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s10_alert'), 'cat_b1s10_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s10_temp_c: 900,
        cat_b2s10_alert_c: 850,
        catalyst_b2s10: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s10_alert'), 'cat_b2s10_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egr_temp_c: 500,
        speed_kmh: 30,
        egr_temp_alert_c: 450,
        egr_temp: { temp_c: 500, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('egr_temp_alert'), 'egr_temp_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        diesel_iaf_cmd_pct: 95,
        speed_kmh: 30,
        diesel_iaf_alert_pct: 88,
        diesel_iaf: { flow_pct: 95, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('diesel_iaf_alert'), 'diesel_iaf_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        thr_actuator_pct: 95,
        speed_kmh: 30,
        thr_act_alert_pct: 92,
        thr_act: { actuator_pct: 95, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('thr_act_alert'), 'thr_act_alert')

  console.log('OK fase26-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
