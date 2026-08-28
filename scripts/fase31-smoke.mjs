#!/usr/bin/env node
/** Fase 31 smoke — EGT B1S5/B2S5, O2λ B1S3/B2S3, NOx reagent. */
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
  console.log('fase31-smoke →', BASE)
  runFaseFormulaChecks(31, assert)

  const deviceId = `fase31-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 31', app_version: '1.64.0', version_code: 166 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b1s5_temp_c: 900,
        egt_b1s5_alert_c: 850,
        egt_b1s5: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('egt_b1s5_alert'), 'egt_b1s5_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b2s5_temp_c: 900,
        egt_b2s5_alert_c: 850,
        egt_b2s5: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('egt_b2s5_alert'), 'egt_b2s5_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_lambda_b1s3: 1.22,
        speed_kmh: 30,
        o2_lmb_b1s3_alert: 1.15,
        o2_lmb_b1s3: { lambda: 1.22, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('o2_lmb_b1s3_alert'), 'o2_lmb_b1s3_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_lambda_b2s3: 1.22,
        speed_kmh: 30,
        o2_lmb_b2s3_alert: 1.15,
        o2_lmb_b2s3: { lambda: 1.22, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('o2_lmb_b2s3_alert'), 'o2_lmb_b2s3_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_reagent_qual_hours: 25,
        nox_req_alert_h: 20,
        nox_reagent: { qual_hours: 25, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('nox_req_alert'), 'nox_req_alert')

  console.log('OK fase31-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
