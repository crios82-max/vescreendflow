#!/usr/bin/env node
/** Fase 51 smoke — FC volt/fuel rate + PS trips from 01D5/01D6. */
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
  console.log('fase51-smoke →', BASE)
  runFaseFormulaChecks(51, assert)

  const deviceId = `fase51-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 51', app_version: '2.49.0', version_code: 251 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fc_volt_v: 120,
        fc_volt_alert_v: 150,
        fc_volt: { volts: 120, band: 'alert', show_warn: true, label: 'FcV · 120V' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('fc_volt_alert'), 'fc_volt_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fc_fuel_rate_gps: 5,
        fc_fuel_rate_alert_gps: 4,
        fc_fuel_rate: { gps: 5, band: 'alert', show_warn: true, label: 'FcFuel · 5.00g/s' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('fc_fuel_rate_alert'), 'fc_fuel_rate_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        ps_trips: 2000,
        ps_trips_alert: 1500,
        ps_trips_state: { trips: 2000, band: 'alert', show_warn: true, label: 'PsTrips · 2000' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('ps_trips_alert'), 'ps_trips_alert')

  console.log('OK fase51-smoke · 3 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
