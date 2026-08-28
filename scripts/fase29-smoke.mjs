#!/usr/bin/env node
/** Fase 29 smoke — cat B1S13/B2S13, DPF trigger, throttle G, engine friction. */
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
  console.log('fase29-smoke →', BASE)
  runFaseFormulaChecks(29, assert)

  const deviceId = `fase29-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 29', app_version: '1.54.0', version_code: 156 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s13_temp_c: 900,
        cat_b1s13_alert_c: 850,
        catalyst_b1s13: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s13_alert'), 'cat_b1s13_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s13_temp_c: 900,
        cat_b2s13_alert_c: 850,
        catalyst_b2s13: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s13_alert'), 'cat_b2s13_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        dpf_trigger_pct: 90,
        speed_kmh: 30,
        dpf_trigger_alert_pct: 85,
        dpf_aftertreatment: { trigger_pct: 90, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('dpf_trigger_alert'), 'dpf_trigger_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        throttle_g_pct: 95,
        speed_kmh: 30,
        thr_g_alert_pct: 90,
        throttle_g: { throttle_pct: 95, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('thr_g_alert'), 'thr_g_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        engine_friction_pct: 55,
        speed_kmh: 30,
        eng_friction_alert_pct: 50,
        eng_friction: { friction_pct: 55, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('eng_friction_alert'), 'eng_friction_alert')

  console.log('OK fase29-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
