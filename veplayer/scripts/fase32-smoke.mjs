#!/usr/bin/env node
/** Fase 32 smoke — EGT B1S6/B2S6, O2λ B1S4/B2S4, DEF fluid. */
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
  console.log('fase32-smoke →', BASE)
  runFaseFormulaChecks(32, assert)

  const deviceId = `fase32-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 32', app_version: '1.69.0', version_code: 171 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b1s6_temp_c: 900,
        egt_b1s6_alert_c: 850,
        egt_b1s6: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('egt_b1s6_alert'), 'egt_b1s6_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b2s6_temp_c: 900,
        egt_b2s6_alert_c: 850,
        egt_b2s6: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('egt_b2s6_alert'), 'egt_b2s6_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_lambda_b1s4: 1.22,
        speed_kmh: 30,
        o2_lmb_b1s4_alert: 1.15,
        o2_lmb_b1s4: { lambda: 1.22, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('o2_lmb_b1s4_alert'), 'o2_lmb_b1s4_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_lambda_b2s4: 1.22,
        speed_kmh: 30,
        o2_lmb_b2s4_alert: 1.15,
        o2_lmb_b2s4: { lambda: 1.22, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('o2_lmb_b2s4_alert'), 'o2_lmb_b2s4_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        def_fluid_pct: 10,
        def_alert_pct: 15,
        def_fluid: { def_pct: 10, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('def_alert'), 'def_alert')

  console.log('OK fase32-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
