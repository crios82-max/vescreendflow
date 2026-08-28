#!/usr/bin/env node
/** Fase 19 smoke — catalyst temps B1S2/B2S2/B1S3/B2S3/B1S4. */
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
  console.log('fase19-smoke →', BASE)
  runFaseFormulaChecks(19, assert)

  const deviceId = `fase19-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 19', app_version: '1.04.0', version_code: 106 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s2_temp_c: 900,
        cat_b1s2_alert_c: 850,
        catalyst_b1s2: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s2_alert'), 'cat_b1s2_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s2_temp_c: 900,
        cat_b2s2_alert_c: 850,
        catalyst_b2s2: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s2_alert'), 'cat_b2s2_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s3_temp_c: 900,
        cat_b1s3_alert_c: 850,
        catalyst_b1s3: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('cat_b1s3_alert'), 'cat_b1s3_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s3_temp_c: 900,
        cat_b2s3_alert_c: 850,
        catalyst_b2s3: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('cat_b2s3_alert'), 'cat_b2s3_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s4_temp_c: 900,
        cat_b1s4_alert_c: 850,
        catalyst_b1s4: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('cat_b1s4_alert'), 'cat_b1s4_alert')

  console.log('OK fase19-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
