#!/usr/bin/env node
/** Fase 46 smoke — HVESS ACR / SOH / MinSOC / MaxSOC / Dcap from 01B3/BE/BF/C1/C2. */
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
  console.log('fase46-smoke →', BASE)
  runFaseFormulaChecks(46, assert)

  const deviceId = `fase46-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 46', app_version: '2.39.0', version_code: 241 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_acr_kw: 150,
        hv_acr_alert_kw: 120,
        hv_acr: { kw: 150, band: 'alert', show_warn: true, label: 'HvAcr · 150.0kW' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('hv_acr_alert'), 'hv_acr_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hvess_soh_pct: 40,
        hvess_soh_alert_pct: 50,
        hvess_soh: { soh_pct: 40, band: 'alert', show_warn: true, label: 'HvSOH · 40%' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('hvess_soh_alert'), 'hvess_soh_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_min_soc_pct: 40,
        hv_min_soc_alert_pct: 35,
        hv_min_soc: { soc_pct: 40, band: 'alert', show_warn: true, label: 'HvMinSOC · 40%' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('hv_min_soc_alert'), 'hv_min_soc_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_max_soc_pct: 70,
        hv_max_soc_alert_pct: 75,
        hv_max_soc: { soc_pct: 70, band: 'alert', show_warn: true, label: 'HvMaxSOC · 70%' },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('hv_max_soc_alert'), 'hv_max_soc_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_dcap_kwh: 20,
        hv_dcap_alert_kwh: 25,
        hv_dcap: { kwh: 20, band: 'alert', show_warn: true, label: 'HvDcap · 20.0kWh' },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('hv_dcap_alert'), 'hv_dcap_alert')

  console.log('OK fase46-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
