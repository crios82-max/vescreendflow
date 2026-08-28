#!/usr/bin/env node
/** Fase 22 smoke — cat B1S6/B2S6, throttle B/C, MIL time on. */
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
  console.log('fase22-smoke →', BASE)
  runFaseFormulaChecks(22, assert)

  const deviceId = `fase22-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 22', app_version: '1.19.0', version_code: 121 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s6_temp_c: 900,
        cat_b1s6_alert_c: 850,
        catalyst_b1s6: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s6_alert'), 'cat_b1s6_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s6_temp_c: 900,
        cat_b2s6_alert_c: 850,
        catalyst_b2s6: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s6_alert'), 'cat_b2s6_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        throttle_b_pct: 95,
        speed_kmh: 45,
        thr_b_alert_pct: 90,
        throttle_b: { throttle_pct: 95, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('thr_b_alert'), 'thr_b_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        throttle_c_pct: 95,
        speed_kmh: 45,
        thr_c_alert_pct: 90,
        throttle_c: { throttle_pct: 95, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('thr_c_alert'), 'thr_c_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        mil: true,
        mil_time_min: 90,
        mil_time_alert_min: 60,
        mil_time: { minutes: 90, mil_on: true, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('mil_time_alert'), 'mil_time_alert')

  console.log('OK fase22-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
