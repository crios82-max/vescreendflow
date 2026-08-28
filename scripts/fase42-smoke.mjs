#!/usr/bin/env node
/** Fase 42 smoke — NOx warn/induce/EGR/malf from PID 0194 extended bytes. */
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
  console.log('fase42-smoke →', BASE)
  runFaseFormulaChecks(42, assert)

  const deviceId = `fase42-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 42', app_version: '2.19.0', version_code: 221 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_warning_active: 1,
        speed_kmh: 30,
        nox_warn_speed_min_kmh: 20,
        nox_warn: { active: true, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('nox_warn_alert'), 'nox_warn_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_induce_level1: 2,
        speed_kmh: 30,
        nox_ind_l1_speed_min_kmh: 20,
        nox_ind_l1: { status: 2, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('nox_ind_l1_alert'), 'nox_ind_l1_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_induce_level2: 2,
        speed_kmh: 30,
        nox_ind_l2_speed_min_kmh: 20,
        nox_ind_l2: { status: 2, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('nox_ind_l2_alert'), 'nox_ind_l2_alert')

  const egrH = 300
  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_egr_valve_counter_hours: egrH,
        nox_egr_alert_h: 100,
        nox_egr_counter: { egr_hours: egrH, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('nox_egr_counter_alert'), 'nox_egr_counter_alert')

  const malH = 300
  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_monitor_malfunction_hours: malH,
        nox_mal_alert_h: 100,
        nox_monitor_malf: { malf_hours: malH, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('nox_monitor_malf_alert'), 'nox_monitor_malf_alert')

  console.log('OK fase42-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
