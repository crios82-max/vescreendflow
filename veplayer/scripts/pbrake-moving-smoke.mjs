#!/usr/bin/env node
/**
 * Parking-brake while moving smoke (VePlayer 0.61 · Fase 11).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(parkingBrake, speedKmh, warnKmh = 5, alertKmh = 15) {
  const speed = Math.max(0, speedKmh)
  if (!parkingBrake) return { band: 'ok', showWarn: false, label: '', parkingBrake: false, speedKmh: speed }
  const warn = Math.max(1, Math.min(40, warnKmh))
  const alert = Math.max(warn + 1, alertKmh)
  if (speed < warn) {
    return { band: 'idle', showWarn: false, label: 'Freno estacionamiento', parkingBrake: true, speedKmh: speed }
  }
  const band = speed >= alert ? 'alert' : 'warn'
  return {
    band,
    showWarn: true,
    label: `Freno · ${Math.trunc(speed)} km/h`,
    parkingBrake: true,
    speedKmh: speed,
  }
}

function voicePhrase(st) {
  if (st.band === 'alert') {
    return 'Atención. Freno de estacionamiento activado en movimiento. Suelta el freno.'
  }
  if (st.band === 'warn') {
    return 'Cuidado. Estás conduciendo con el freno de estacionamiento.'
  }
  return 'Freno de estacionamiento activado.'
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
  console.log('pbrake-moving-smoke →', BASE)
  assert(evaluate(false, 40).band === 'ok', 'ok off')
  assert(evaluate(true, 2).band === 'idle', 'idle stopped')
  assert(evaluate(true, 8).band === 'warn' && evaluate(true, 8).showWarn, 'warn')
  assert(
    evaluate(true, 20).band === 'alert' && voicePhrase(evaluate(true, 20)).includes('movimiento'),
    'alert',
  )

  const deviceId = `pbrake-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'P-brake smoke',
      app_version: '0.61.0',
      version_code: 63,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.61.0',
      version_code: 63,
      vehicle_signals: {
        parking_brake: true,
        speed_kmh: 8,
        speed_mps: 8 / 3.6,
        ignition: 'on',
        pbrake_warn_kmh: 5,
        pbrake_alert_kmh: 15,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('pbrake_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.61.0',
      version_code: 63,
      vehicle_signals: {
        parking_brake: true,
        speed_kmh: 22,
        speed_mps: 22 / 3.6,
        ignition: 'on',
        pbrake_warn_kmh: 5,
        pbrake_alert_kmh: 15,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('pbrake_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK pbrake-moving-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
