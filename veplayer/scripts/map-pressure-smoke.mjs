#!/usr/bin/env node
/**
 * MAP pressure smoke (VePlayer 0.77 · Fase 14).
 * OBD PID 010B — intake manifold absolute pressure (kPa).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(mapKpa, speedKmh = 40, warnKpa = 95, alertKpa = 105, speedMin = 20) {
  if (mapKpa == null) return { band: 'idle', showWarn: false, label: '' }
  const map = Math.max(0, Math.min(255, mapKpa))
  const warn = Math.max(50, Math.min(200, warnKpa))
  const alert = Math.min(255, Math.max(warn + 5, alertKpa))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  if (speedKmh < minSpd) {
    return {
      mapKpa: map,
      band: 'ok',
      showWarn: false,
      label: map >= 70 ? `MAP · ${Math.trunc(map)} kPa` : '',
    }
  }
  let band = 'ok'
  if (map >= alert) band = 'alert'
  else if (map >= warn) band = 'warn'
  return {
    mapKpa: map,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `MAP · ${Math.trunc(map)} kPa`,
  }
}

function voicePhrase(st) {
  const k = st.mapKpa != null ? `${Math.trunc(st.mapKpa)} kilopascales` : 'alta'
  if (st.band === 'alert') return `Atención. Presión MAP crítica. ${k}. Reduce demanda.`
  if (st.band === 'warn') return `Cuidado. Presión MAP alta. ${k}.`
  return `Presión MAP a ${k}.`
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
  console.log('map-pressure-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(80, 40).band === 'ok', 'ok')
  assert(evaluate(98, 40).band === 'warn' && evaluate(98, 40).showWarn, 'warn')
  assert(
    evaluate(110, 40).band === 'alert' && voicePhrase(evaluate(110, 40)).includes('crítica'),
    'alert',
  )
  assert(evaluate(110, 10).band === 'ok', 'low speed skip')

  // OBD PID 010B: 41 0B 62 → 98 kPa
  const a = 0x62
  assert(a === 98, 'pid 010B')

  const deviceId = `map-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'MAP smoke',
      app_version: '0.77.0',
      version_code: 79,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.77.0',
      version_code: 79,
      vehicle_signals: {
        map_kpa: 98,
        speed_kmh: 45,
        map_warn_kpa: 95,
        map_alert_kpa: 105,
        map_speed_min_kmh: 20,
        map_pressure: { map_kpa: 98, speed_kmh: 45, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('map_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.77.0',
      version_code: 79,
      vehicle_signals: {
        map_kpa: 112,
        speed_kmh: 50,
        map_warn_kpa: 95,
        map_alert_kpa: 105,
        map_speed_min_kmh: 20,
        map_pressure: { map_kpa: 112, speed_kmh: 50, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('map_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK map-pressure-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
