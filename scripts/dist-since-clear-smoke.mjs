#!/usr/bin/env node
/**
 * Distance since DTC clear smoke (VePlayer 0.74 · Fase 14).
 * OBD PID 0131 — km since codes cleared.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(distanceKm, faultActive = true, warnKm = 100, alertKm = 200) {
  if (distanceKm == null || !faultActive) {
    return { band: distanceKm == null ? 'idle' : 'ok', showWarn: false, label: '' }
  }
  const km = Math.max(0, distanceKm)
  const warn = Math.max(10, warnKm)
  const alert = Math.max(warn + 10, alertKm)
  let band = 'ok'
  if (km >= alert) band = 'alert'
  else if (km >= warn) band = 'warn'
  return {
    distanceKm: km,
    faultActive,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `Clear · ${Math.trunc(km)} km`,
  }
}

function voicePhrase(st) {
  const km = st.distanceKm != null ? `${Math.trunc(st.distanceKm)} kilómetros` : 'muchos kilómetros'
  if (st.band === 'alert') {
    return `Atención. Llevas ${km} desde el último reset de fallas sin reparar. Revisa el vehículo.`
  }
  if (st.band === 'warn') {
    return `Cuidado. ${km} desde limpiar códigos y la falla sigue. Programa servicio.`
  }
  return `Distancia desde clear. ${km}.`
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
  console.log('dist-since-clear-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(50).band === 'ok', 'ok')
  assert(evaluate(120).band === 'warn' && evaluate(120).showWarn, 'warn')
  assert(
    evaluate(250).band === 'alert' && voicePhrase(evaluate(250)).includes('Atención'),
    'alert',
  )
  assert(evaluate(150, false).band === 'ok', 'no fault')

  // OBD PID 0131: 41 31 00 C8 → 200 km
  const a = 0x00
  const b = 0xc8
  assert(a * 256 + b === 200, 'pid 0131')

  const deviceId = `dist-clear-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Dist clear smoke',
      app_version: '0.74.0',
      version_code: 76,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.74.0',
      version_code: 76,
      vehicle_signals: {
        mil: true,
        dtc_count: 1,
        dist_since_clear_km: 120,
        dist_clear_warn_km: 100,
        dist_clear_alert_km: 200,
        dist_since_clear: {
          distance_km: 120,
          fault_active: true,
          band: 'warn',
          show_warn: true,
        },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('dist_clear_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.74.0',
      version_code: 76,
      vehicle_signals: {
        mil: true,
        dtc_count: 2,
        dist_since_clear_km: 250,
        dist_clear_warn_km: 100,
        dist_clear_alert_km: 200,
        dist_since_clear: {
          distance_km: 250,
          fault_active: true,
          band: 'alert',
          show_warn: true,
        },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('dist_clear_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK dist-since-clear-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
