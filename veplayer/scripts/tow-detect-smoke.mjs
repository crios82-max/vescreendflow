#!/usr/bin/env node
/**
 * Unauthorized movement / tow smoke (VePlayer 0.49 · Fase 9).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(
  ignitionOn,
  parkingBrake,
  speedKmh,
  movingForSec,
  speedMin = 3,
  warnSec = 3,
  alertSec = 8,
) {
  const secured = !ignitionOn || parkingBrake
  if (!secured) return { band: 'ok', showWarn: false, label: '' }
  if (speedKmh < speedMin) return { band: 'idle', showWarn: false, label: '', movingForSec: 0 }
  let band = 'moving'
  if (movingForSec >= alertSec) band = 'alert'
  else if (movingForSec >= warnSec) band = 'warn'
  return {
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `Remolque · ${Math.trunc(speedKmh)} km/h`,
    movingForSec,
  }
}

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
  console.log('tow-detect-smoke →', BASE)
  assert(evaluate(true, false, 40, 20).band === 'ok', 'ign on ok')
  assert(evaluate(false, false, 0, 0).band === 'idle', 'idle')
  assert(evaluate(false, true, 10, 2).band === 'moving', 'moving')
  assert(evaluate(false, true, 10, 4).band === 'warn', 'warn')
  assert(evaluate(false, false, 12, 10).band === 'alert', 'alert')

  const deviceId = `tow-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Tow smoke',
      app_version: '0.49.0',
      version_code: 51,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.49.0',
      version_code: 51,
      speed_mps: 10 / 3.6,
      vehicle_signals: {
        ignition: 'off',
        parking_brake: true,
        speed_mps: 10 / 3.6,
        tow_moving_sec: 4,
        tow_warn_sec: 3,
        tow_alert_sec: 8,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('tow_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.49.0',
      version_code: 51,
      speed_mps: 15 / 3.6,
      vehicle_signals: {
        ignition: 'off',
        parking_brake: false,
        speed_mps: 15 / 3.6,
        tow_moving_sec: 12,
        tow_warn_sec: 3,
        tow_alert_sec: 8,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('tow_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK tow-detect-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
