#!/usr/bin/env node
/**
 * Turn signal stuck smoke (VePlayer 0.62 · Fase 11).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(side, heldSec, warnSec = 30, alertSec = 60) {
  const s = String(side || '').toLowerCase().trim()
  if (s !== 'left' && s !== 'right') return { band: 'idle', showWarn: false, label: '', side: '' }
  const held = Math.max(0, heldSec)
  const warn = Math.max(10, Math.min(180, warnSec))
  const alert = Math.max(warn + 5, alertSec)
  let band = 'ok'
  if (held >= alert) band = 'alert'
  else if (held >= warn) band = 'warn'
  const sideLabel = s === 'left' ? 'Izq' : 'Der'
  return {
    side: s,
    heldSec: held,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: band === 'ok' ? `Inter · ${sideLabel}` : `Inter · ${sideLabel} ${Math.trunc(held)}s`,
  }
}

function voicePhrase(st) {
  const side = st.side === 'left' ? 'izquierda' : st.side === 'right' ? 'derecha' : ''
  if (st.band === 'alert') {
    return `Atención. Intermitente ${side} olvidado. Llevas ${Math.trunc(st.heldSec)} segundos.`
  }
  if (st.band === 'warn') return `Cuidado. El intermitente ${side} sigue encendido.`
  return `Intermitente ${side} activo.`
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
  console.log('turn-stuck-smoke →', BASE)
  assert(evaluate('', 40).band === 'idle', 'idle')
  assert(evaluate('left', 10).band === 'ok', 'ok')
  assert(evaluate('left', 35).band === 'warn' && evaluate('left', 35).showWarn, 'warn')
  assert(
    evaluate('right', 70).band === 'alert' &&
      voicePhrase(evaluate('right', 70)).includes('olvidado'),
    'alert',
  )

  const deviceId = `turnstuck-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Turn stuck smoke',
      app_version: '0.62.0',
      version_code: 64,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.62.0',
      version_code: 64,
      vehicle_signals: {
        turn: 'left',
        turn_stuck_sec: 35,
        turn_stuck_side: 'left',
        turn_stuck_warn_sec: 30,
        turn_stuck_alert_sec: 60,
        speed_kmh: 40,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('turn_stuck_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.62.0',
      version_code: 64,
      vehicle_signals: {
        turn: 'right',
        turn_stuck_sec: 75,
        turn_stuck_side: 'right',
        turn_stuck_warn_sec: 30,
        turn_stuck_alert_sec: 60,
        speed_kmh: 40,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('turn_stuck_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK turn-stuck-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
