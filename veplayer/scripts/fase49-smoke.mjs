#!/usr/bin/env node
/** Fase 49 smoke — ESS charge lim/act + HV energy rate from 01D1/01D4. */
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
  console.log('fase49-smoke →', BASE)
  runFaseFormulaChecks(49, assert)

  const deviceId = `fase49-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 49', app_version: '2.45.0', version_code: 247 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        ess_chg_lim_kw: 5,
        ess_chg_lim_alert_kw: 8,
        ess_chg_lim: { kw: 5, band: 'alert', show_warn: true, label: 'EssLim · 5.0kW' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('ess_chg_lim_alert'), 'ess_chg_lim_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        ess_chg_act_kw: 150,
        ess_chg_act_alert_kw: 120,
        ess_chg_act: { kw: 150, band: 'alert', show_warn: true, label: 'EssAct · 150.0kW' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('ess_chg_act_alert'), 'ess_chg_act_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_ener_rate_whs: 90,
        hv_ener_rate_alert_whs: 70,
        hv_ener_rate: { whs: 90, band: 'alert', show_warn: true, label: 'HvEner · 90.0Wh/s' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('hv_ener_rate_alert'), 'hv_ener_rate_alert')

  console.log('OK fase49-smoke · 3 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
