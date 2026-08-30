#!/usr/bin/env node
/** Fase 52 smoke — HevMode/HevBattCurr 019A + VSet 01AA. */
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
  console.log('fase52-smoke →', BASE)
  runFaseFormulaChecks(52, assert)

  const deviceId = `fase52-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 52', app_version: '2.51.0', version_code: 253 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hev_mode_code: 2,
        hev_mode: { code: 2, mode: 'CIM', band: 'warn', show_warn: true, label: 'HevMode · CIM' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('hev_mode_warn'), 'hev_mode_warn')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hev_batt_current_a: 300,
        hev_batt_curr_alert_a: 250,
        hev_batt_curr: { amps: 300, band: 'alert', show_warn: true, label: 'HevA · 300A' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('hev_batt_curr_alert'), 'hev_batt_curr_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        v_set_kmh: 40,
        v_set_alert_kmh: 60,
        v_set: { kmh: 40, band: 'alert', show_warn: true, label: 'VSet · 40km/h' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('v_set_alert'), 'v_set_alert')

  console.log('OK fase52-smoke · 3 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
