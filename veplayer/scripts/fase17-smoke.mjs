#!/usr/bin/env node
/** Fase 17 smoke — lambda, evap purge, ethanol, vapor, rail abs. */
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
  console.log('fase17-smoke →', BASE)
  runFaseFormulaChecks(17, assert)

  const deviceId = `fase17-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 17', app_version: '0.94.0', version_code: 96 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        equiv_ratio: 0.75,
        speed_kmh: 45,
        rpm: 2000,
        equiv_alert_low: 0.8,
        equiv_ratio_state: { ratio: 0.75, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('equiv_alert'), 'equiv_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        evap_purge_pct: 80,
        speed_kmh: 45,
        evap_purge_alert_pct: 75,
        evap_purge: { purge_pct: 80, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('evap_purge_alert'), 'evap_purge_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        ethanol_pct: 88,
        speed_kmh: 45,
        ethanol_alert_pct: 85,
        ethanol: { ethanol_pct: 88, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('ethanol_alert'), 'ethanol_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        evap_vapor_pa: 9000,
        speed_kmh: 45,
        evap_vapor_alert_pa: 8000,
        evap_vapor: { pressure_pa: 9000, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('evap_vapor_alert'), 'evap_vapor_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_rail_abs_kpa: 5500,
        speed_kmh: 45,
        rail_abs_alert_kpa: 6000,
        fuel_rail_abs: { pressure_kpa: 5500, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('rail_abs_alert'), 'rail_abs_alert')

  console.log('OK fase17-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
