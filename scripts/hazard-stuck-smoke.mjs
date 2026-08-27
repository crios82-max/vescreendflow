#!/usr/bin/env node
/**
 * Hazard stuck smoke (VePlayer 0.65 · Fase 12).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(active, heldSec, warnSec = 45, alertSec = 90) {
  if (!active) return { band: 'idle', showWarn: false, label: '', active: false }
  const held = Math.max(0, heldSec)
  const warn = Math.max(15, Math.min(300, warnSec))
  const alert = Math.max(warn + 10, alertSec)
  let band = 'ok'
  if (held >= alert) band = 'alert'
  else if (held >= warn) band = 'warn'
  return {
    active: true,
    heldSec: held,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: band === 'ok' ? 'Hazard' : `Hazard · ${Math.trunc(held)}s`,
  }
}

function voicePhrase(st) {
  if (st.band === 'alert') {
    return `Atención. Luces de emergencia olvidadas. Llevas ${Math.trunc(st.heldSec)} segundos.`
  }
  if (st.band === 'warn') return 'Cuidado. Las luces de emergencia siguen encendidas.'
  return 'Luces de emergencia activas.'
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
  console.log('hazard-stuck-smoke →', BASE)
  assert(evaluate(false, 100).band === 'idle', 'idle')
  assert(evaluate(true, 20).band === 'ok', 'ok')
  assert(evaluate(true, 50).band === 'warn' && evaluate(true, 50).showWarn, 'warn')
  assert(
    evaluate(true, 100).band === 'alert' &&
      voicePhrase(evaluate(true, 100)).includes('emergencia'),
    'alert',
  )

  const deviceId = `hazard-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Hazard stuck smoke',
      app_version: '0.65.0',
      version_code: 67,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.65.0',
      version_code: 67,
      vehicle_signals: {
        turn: 'hazard',
        hazard_stuck_sec: 50,
        hazard_stuck_warn_sec: 45,
        hazard_stuck_alert_sec: 90,
        speed_kmh: 30,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('hazard_stuck_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.65.0',
      version_code: 67,
      vehicle_signals: {
        turn: 'hazard',
        hazard_stuck_sec: 100,
        hazard_stuck_warn_sec: 45,
        hazard_stuck_alert_sec: 90,
        speed_kmh: 30,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('hazard_stuck_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK hazard-stuck-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
