#!/usr/bin/env node
/**
 * Gear roll (P/N moving) smoke (VePlayer 0.66 · Fase 12).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(gear, speedKmh, warnKmh = 5, alertKmh = 20) {
  const speed = Math.max(0, speedKmh)
  const g = String(gear || '').toUpperCase()
  if (g !== 'P' && g !== 'N') {
    return { band: 'ok', showWarn: false, label: '', gear: g, speedKmh: speed }
  }
  const warn = Math.max(1, Math.min(40, warnKmh))
  const alert = Math.max(warn + 1, alertKmh)
  if (speed < warn) {
    return { band: 'idle', showWarn: false, label: `Marcha ${g}`, gear: g, speedKmh: speed }
  }
  const band = speed >= alert ? 'alert' : 'warn'
  return {
    band,
    showWarn: true,
    label: `${g} · ${Math.trunc(speed)} km/h`,
    gear: g,
    speedKmh: speed,
  }
}

function voicePhrase(st) {
  const g = st.gear === 'P' ? 'parking' : st.gear === 'N' ? 'neutral' : st.gear
  if (st.band === 'alert') return `Atención. Vehículo en movimiento en ${g}. Pon marcha o frena.`
  if (st.band === 'warn') return `Cuidado. Te estás desplazando en ${g}.`
  return `Marcha ${g}.`
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
  console.log('gear-roll-smoke →', BASE)
  assert(evaluate('D', 40).band === 'ok', 'ok drive')
  assert(evaluate('N', 2).band === 'idle', 'idle')
  assert(evaluate('N', 8).band === 'warn' && evaluate('N', 8).showWarn, 'warn')
  assert(
    evaluate('P', 25).band === 'alert' && voicePhrase(evaluate('P', 25)).includes('parking'),
    'alert',
  )

  const deviceId = `gearroll-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Gear roll smoke',
      app_version: '0.66.0',
      version_code: 68,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.66.0',
      version_code: 68,
      vehicle_signals: {
        gear: 'N',
        speed_kmh: 8,
        speed_mps: 8 / 3.6,
        gear_roll_warn_kmh: 5,
        gear_roll_alert_kmh: 20,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('gear_roll_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.66.0',
      version_code: 68,
      vehicle_signals: {
        gear: 'P',
        speed_kmh: 25,
        speed_mps: 25 / 3.6,
        gear_roll_warn_kmh: 5,
        gear_roll_alert_kmh: 20,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('gear_roll_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK gear-roll-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
