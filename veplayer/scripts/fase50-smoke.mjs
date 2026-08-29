#!/usr/bin/env node
/** Fase 50 smoke — HV curr rate + EM RPM/TQ from 01DA/CC/CD. */
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
  console.log('fase50-smoke →', BASE)
  runFaseFormulaChecks(50, assert)

  const deviceId = `fase50-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 50', app_version: '2.47.0', version_code: 249 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hv_curr_rate_ahs: 40,
        hv_curr_rate_alert_ahs: 30,
        hv_curr_rate: { ahs: 40, band: 'alert', show_warn: true, label: 'HvCurr · 40.00Ah/s' },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('hv_curr_rate_alert'), 'hv_curr_rate_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        em_rpm_a: 18000,
        em_rpm_alert: 16000,
        em_rpm: { rpm: 18000, band: 'alert', show_warn: true, label: 'EmRpm · 18000' },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('em_rpm_alert'), 'em_rpm_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        em_tq_a_nm: 450,
        em_tq_alert_nm: 400,
        em_tq: { nm: 450, band: 'alert', show_warn: true, label: 'EmTq · 450Nm' },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('em_tq_alert'), 'em_tq_alert')

  console.log('OK fase50-smoke · 3 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
