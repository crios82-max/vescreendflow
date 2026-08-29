#!/usr/bin/env node
/** Fase 47 smoke — SOCE / calculated ESS cap from 01D2/01D9. */
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
  console.log('fase47-smoke →', BASE)
  runFaseFormulaChecks(47, assert)

  const deviceId = `fase47-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 47', app_version: '2.41.0', version_code: 243 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_soce_pct: 40,
        hv_soce_alert_pct: 50,
        hv_soce: { soce_pct: 40, band: 'alert', show_warn: true, label: 'SOCE · 40%' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('hv_soce_alert'), 'hv_soce_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        ess_cap_kwh: 20,
        ess_cap_alert_kwh: 25,
        ess_cap: { kwh: 20, band: 'alert', show_warn: true, label: 'EssCap · 20.0kWh' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('ess_cap_alert'), 'ess_cap_alert')

  console.log('OK fase47-smoke · 2 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
