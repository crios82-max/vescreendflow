#!/usr/bin/env node
/**
 * Door ajar HUD smoke (VePlayer 0.39 · Fase 7).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function openLabels(doors) {
  const out = []
  if (doors.fl) out.push('FL')
  if (doors.fr) out.push('FR')
  if (doors.rl) out.push('RL')
  if (doors.rr) out.push('RR')
  if (doors.trunk) out.push('baúl')
  if (doors.hood) out.push('capó')
  return out
}

function evaluate(doors, speedKmh, reverse = false, warnKmh = 5, alertKmh = 20) {
  const labels = openLabels(doors)
  if (!labels.length) return { band: 'closed', showWarn: false, label: '' }
  let band = 'ajar'
  if (reverse || speedKmh >= alertKmh) band = 'alert'
  else if (speedKmh >= warnKmh) band = 'warn'
  return {
    openLabels: labels,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: labels.join('+'),
  }
}

function voicePhrase(st) {
  const doors = (st.openLabels || []).join(', ')
  if (st.band === 'alert') return `Atención. Puerta abierta en movimiento. ${doors}.`
  if (st.band === 'warn') return `Cuidado. Puerta abierta. ${doors}.`
  return `Puerta abierta. ${doors}.`
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
  console.log('door-ajar-smoke →', BASE)
  const closed = evaluate({ fl: false }, 40)
  assert(closed.band === 'closed' && !closed.showWarn, 'closed')
  const ajar = evaluate({ fl: true }, 0)
  assert(ajar.band === 'ajar' && !ajar.showWarn && ajar.label === 'FL', 'ajar')
  const warn = evaluate({ fl: true, fr: true }, 8)
  assert(warn.band === 'warn' && warn.showWarn && warn.label === 'FL+FR', 'warn')
  const alert = evaluate({ trunk: true }, 25)
  assert(alert.band === 'alert' && voicePhrase(alert).includes('movimiento'), 'alert')
  const rev = evaluate({ hood: true }, 0, true)
  assert(rev.band === 'alert', 'reverse alert')

  const deviceId = `door-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Door ajar smoke',
      app_version: '0.39.0',
      version_code: 41,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbAjar = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.39.0',
      version_code: 41,
      vehicle_signals: {
        speed_mps: 0,
        gear: 'P',
        doors: { fl: true, fr: false, rl: false, rr: false, trunk: false, hood: false },
        source: 'obd_sim',
      },
    }),
  })
  assert(hbAjar.ok, `hb ajar ${hbAjar.status}`)
  const raisedAjar = hbAjar.body.alerts_raised || []
  assert(raisedAjar.includes('door_ajar'), `ajar raised ${JSON.stringify(raisedAjar)}`)

  const hbMove = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.39.0',
      version_code: 41,
      vehicle_signals: {
        speed_mps: 12,
        gear: 'D',
        doors: { fl: true, fr: false, rl: false, rr: false, trunk: false, hood: false },
        source: 'obd_sim',
      },
    }),
  })
  assert(hbMove.ok, `hb move ${hbMove.status}`)
  const raisedMove = hbMove.body.alerts_raised || []
  assert(raisedMove.includes('door_moving'), `moving raised ${JSON.stringify(raisedMove)}`)

  console.log(
    'OK door-ajar-smoke ·',
    alert.label,
    '· raised',
    [...raisedAjar, ...raisedMove].filter((k) => k.startsWith('door')),
  )
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
