#!/usr/bin/env node
/** Fase 48 smoke — BCAP ready / ESS reserve from 01D8/01D0. */
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
  console.log('fase48-smoke →', BASE)
  runFaseFormulaChecks(48, assert)

  const deviceId = `fase48-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 48', app_version: '2.43.0', version_code: 245 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        bcap_ready: 0,
        bcap_ready_state: { ready: false, band: 'warn', show_warn: true, label: 'Bcap · NotReady' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('bcap_ready_warn'), 'bcap_ready_warn')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        ess_rsrv_rem_kwh: 2,
        ess_rsrv_alert_kwh: 3,
        ess_rsrv: { kwh: 2, band: 'alert', show_warn: true, label: 'EssRsrv · 2.0kWh' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('ess_rsrv_alert'), 'ess_rsrv_alert')

  console.log('OK fase48-smoke · 2 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
