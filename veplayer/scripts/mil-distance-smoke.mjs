#!/usr/bin/env node
/**
 * MIL distance smoke (VePlayer 0.73 · Fase 13).
 * OBD PID 0121 — distance with MIL on (km).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(distanceKm, milOn = true, warnKm = 50, alertKm = 100) {
  if (distanceKm == null || !milOn) {
    return { band: distanceKm == null ? 'idle' : 'ok', showWarn: false, label: '' }
  }
  const km = Math.max(0, distanceKm)
  const warn = Math.max(5, warnKm)
  const alert = Math.max(warn + 5, alertKm)
  let band = 'ok'
  if (km >= alert) band = 'alert'
  else if (km >= warn) band = 'warn'
  return {
    distanceKm: km,
    milOn,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `MIL · ${Math.trunc(km)} km`,
  }
}

function voicePhrase(st) {
  const km = st.distanceKm != null ? `${Math.trunc(st.distanceKm)} kilómetros` : 'muchos kilómetros'
  if (st.band === 'alert') {
    return `Atención. Llevas ${km} con la luz de motor encendida. Revisa el vehículo pronto.`
  }
  if (st.band === 'warn') return `Cuidado. Has recorrido ${km} con MIL activa. Programa revisión.`
  return `Distancia con MIL. ${km}.`
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
  console.log('mil-distance-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(30).band === 'ok', 'ok')
  assert(evaluate(65).band === 'warn' && evaluate(65).showWarn, 'warn')
  assert(
    evaluate(120).band === 'alert' && voicePhrase(evaluate(120)).includes('Atención'),
    'alert',
  )
  assert(evaluate(80, false).band === 'ok', 'mil off')

  // OBD PID 0121: 41 21 01 F4 → 0x01F4 = 500 km
  const a = 0x01
  const b = 0xf4
  assert(a * 256 + b === 500, 'pid 0121')

  const deviceId = `mil-dist-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'MIL dist smoke',
      app_version: '0.73.0',
      version_code: 75,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.73.0',
      version_code: 75,
      vehicle_signals: {
        mil: true,
        mil_distance_km: 65,
        mil_dist_warn_km: 50,
        mil_dist_alert_km: 100,
        mil_dist: { distance_km: 65, mil_on: true, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('mil_dist_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.73.0',
      version_code: 75,
      vehicle_signals: {
        mil: true,
        mil_distance_km: 150,
        mil_dist_warn_km: 50,
        mil_dist_alert_km: 100,
        mil_dist: { distance_km: 150, mil_on: true, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('mil_dist_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK mil-distance-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
