#!/usr/bin/env node
/** Fase 35 smoke — NOx corr B1S2/B2S1/B2S2, NOx conc S3/S4. */
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
  console.log('fase35-smoke →', BASE)
  runFaseFormulaChecks(35, assert)

  const deviceId = `fase35-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 35', app_version: '1.84.0', version_code: 186 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_corrected_b1s2_ppm: 900,
        speed_kmh: 30,
        nox_corr_b1s2_alert: 800,
        nox_corr_b1s2: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('nox_corr_b1s2_alert'), 'nox_corr_b1s2_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_corrected_b2s1_ppm: 900,
        speed_kmh: 30,
        nox_corr_b2s1_alert: 800,
        nox_corr_b2s1: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('nox_corr_b2s1_alert'), 'nox_corr_b2s1_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_corrected_b2s2_ppm: 900,
        speed_kmh: 30,
        nox_corr_b2s2_alert: 800,
        nox_corr_b2s2: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('nox_corr_b2s2_alert'), 'nox_corr_b2s2_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_conc_s3_ppm: 900,
        speed_kmh: 30,
        nox_conc_s3_alert: 800,
        nox_conc_s3: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('nox_conc_s3_alert'), 'nox_conc_s3_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        nox_conc_s4_ppm: 900,
        speed_kmh: 30,
        nox_conc_s4_alert: 800,
        nox_conc_s4: { nox_ppm: 900, band: 'alert', show_warn: true, speed_kmh: 30 },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('nox_conc_s4_alert'), 'nox_conc_s4_alert')

  console.log('OK fase35-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
