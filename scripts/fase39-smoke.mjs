#!/usr/bin/env node
/** Fase 39 smoke — particulate inducement, DPF removal, reagent fail, PCM malf (01C6). */
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
  console.log('fase39-smoke →', BASE)
  runFaseFormulaChecks(39, assert)

  const deviceId = `fase39-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 39', app_version: '2.04.0', version_code: 206 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        particulate_induce_status: 1,
        speed_kmh: 30,
        particulate_induce_warn_status: 1,
        particulate_induce_warn: { status: 1, band: 'warn', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('particulate_induce_warn'), 'particulate_induce_warn')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        particulate_induce_status: 2,
        speed_kmh: 30,
        particulate_induce_alert_status: 2,
        particulate_induce_alert: { status: 2, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('particulate_induce_alert'), 'particulate_induce_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        dpf_removal_counter: 250,
        speed_kmh: 30,
        dpf_removal_alert_count: 200,
        dpf_removal: { count: 250, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('dpf_removal_alert'), 'dpf_removal_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        reagent_injection_fail_counter: 90,
        speed_kmh: 30,
        reagent_fail_alert_count: 80,
        reagent_fail: { count: 90, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('reagent_fail_alert'), 'reagent_fail_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        particulate_monitor_malfunction_counter: 90,
        speed_kmh: 30,
        particulate_malf_alert_count: 80,
        particulate_malf: { count: 90, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('particulate_malf_alert'), 'particulate_malf_alert')

  console.log('OK fase39-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
