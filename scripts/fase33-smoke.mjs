#!/usr/bin/env node
/** Fase 33 smoke — EGT B1S7/B2S7/B1S8/B2S8, O2 conc B1S3. */
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
  console.log('fase33-smoke →', BASE)
  runFaseFormulaChecks(33, assert)

  const deviceId = `fase33-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 33', app_version: '1.74.0', version_code: 176 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b1s7_temp_c: 900,
        egt_b1s7_alert_c: 850,
        egt_b1s7: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('egt_b1s7_alert'), 'egt_b1s7_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b2s7_temp_c: 900,
        egt_b2s7_alert_c: 850,
        egt_b2s7: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('egt_b2s7_alert'), 'egt_b2s7_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b1s8_temp_c: 900,
        egt_b1s8_alert_c: 850,
        egt_b1s8: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('egt_b1s8_alert'), 'egt_b1s8_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egt_b2s8_temp_c: 900,
        egt_b2s8_alert_c: 850,
        egt_b2s8: { egt_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('egt_b2s8_alert'), 'egt_b2s8_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_conc_b1s3_pct: 20,
        speed_kmh: 30,
        o2_conc_b1s3_alert: 18,
        o2_conc_b1s3: { conc_pct: 20, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('o2_conc_b1s3_alert'), 'o2_conc_b1s3_alert')

  console.log('OK fase33-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
