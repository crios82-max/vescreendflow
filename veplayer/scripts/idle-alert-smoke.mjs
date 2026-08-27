#!/usr/bin/env node
/**
 * Idle alert math + SenseFlow smoke (VePlayer 0.30).
 */

function evaluate(speedKmh, ignitionOn, idleForSec, warnSec = 120, alertSec = 300, speedMaxKmh = 1.5) {
  const speed = Math.max(0, speedKmh)
  if (!ignitionOn) return { speedKmh: speed, ignitionOn: false, idleForSec: 0, band: 'off', showWarn: false }
  if (speed > speedMaxKmh) {
    return { speedKmh: speed, ignitionOn: true, idleForSec: 0, band: 'moving', showWarn: false }
  }
  const idle = Math.max(0, idleForSec)
  let band = 'idle'
  if (idle >= alertSec) band = 'alert'
  else if (idle >= warnSec) band = 'warn'
  return {
    speedKmh: speed,
    ignitionOn: true,
    idleForSec: idle,
    band,
    showWarn: band === 'warn' || band === 'alert',
  }
}

function voicePhrase(state) {
  const mins = Math.floor(state.idleForSec / 60)
  const secs = Math.floor(state.idleForSec % 60)
  const dur = mins > 0 ? `${mins} minutos` : `${secs} segundos`
  if (state.band === 'alert') {
    return `Motor en ralentí prolongado. Llevas ${dur} detenido con el motor encendido.`
  }
  if (state.band === 'warn') return `Vehículo en ralentí. Llevas ${dur} detenido.`
  return 'Vehículo detenido.'
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

assert(evaluate(40, true, 0).band === 'moving', 'moving')
assert(evaluate(0, false, 200).band === 'off', 'off')
assert(evaluate(0, true, 30).band === 'idle', 'idle')
assert(evaluate(0, true, 150).band === 'warn' && evaluate(0, true, 150).showWarn, 'warn')
assert(evaluate(0, true, 320).band === 'alert', 'alert')
assert(voicePhrase(evaluate(0, true, 320)).includes('prolongado'), 'phrase')

const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

async function j(path, init = {}, token) {
  const headers = { 'content-type': 'application/json', ...(init.headers || {}) }
  if (token) headers['x-fleet-token'] = token
  const r = await fetch(BASE + path, { ...init, headers })
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
  console.log('idle-alert-smoke →', BASE)
  const deviceId = `idle-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Idle smoke',
      app_version: '0.30.0',
      version_code: 32,
    }),
  })
  assert(reg.ok, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      speed_mps: 0,
      vehicle_signals: { speed_mps: 0, ignition: 'on', idle_sec: 150 },
    }),
  })
  assert(hbWarn.ok, 'hb warn')
  assert((hbWarn.body.alerts_raised || []).includes('idle_warn'), `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      speed_mps: 0,
      vehicle_signals: { speed_mps: 0, ignition: 'on', idle_sec: 360 },
    }),
  })
  assert((hbAlert.body.alerts_raised || []).includes('idle_alert'), `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`)

  const cmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'set_idle_warn',
        payload: { warn_sec: 90, alert_sec: 180 },
      }),
    },
    'fleet-dispatcher-demo',
  )
  assert(cmd.ok, `cmd ${JSON.stringify(cmd.body)}`)

  console.log('OK idle-alert ·', voicePhrase(evaluate(0, true, 320)))
  console.log('OK idle-alert-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
