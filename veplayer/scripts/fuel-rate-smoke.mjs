#!/usr/bin/env node
/**
 * Fuel rate smoke (VePlayer 0.72 · Fase 13).
 * OBD PID 015E — ((A*256)+B)/20 g/s → L/h.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
const GRAMS_PER_LITER = 740

function gpsToLph(gps) {
  return (gps * 3600) / GRAMS_PER_LITER
}

function evaluate(fuelRateGps, speedKmh = 40, warnLph = 55, alertLph = 80, speedMin = 20) {
  if (fuelRateGps == null) return { band: 'idle', showWarn: false, label: '' }
  const lph = gpsToLph(Math.max(0, fuelRateGps))
  const warn = Math.max(10, Math.min(200, warnLph))
  const alert = Math.min(250, Math.max(warn + 5, alertLph))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  if (speedKmh < minSpd) {
    return {
      fuelRateLph: lph,
      band: 'ok',
      showWarn: false,
      label: lph >= 20 ? `Comb · ${Math.trunc(lph)} L/h` : '',
    }
  }
  let band = 'ok'
  if (lph >= alert) band = 'alert'
  else if (lph >= warn) band = 'warn'
  return {
    fuelRateLph: lph,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `Comb · ${Math.trunc(lph)} L/h`,
  }
}

function voicePhrase(st) {
  const l = st.fuelRateLph != null ? `${Math.trunc(st.fuelRateLph)} litros por hora` : 'alta'
  if (st.band === 'alert') return `Atención. Consumo de combustible crítico. ${l}. Reduce velocidad.`
  if (st.band === 'warn') return `Cuidado. Consumo alto. ${l}.`
  return `Consumo a ${l}.`
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
  console.log('fuel-rate-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(8, 40).band === 'ok', 'ok')
  assert(evaluate(12, 40).band === 'warn' && evaluate(12, 40).showWarn, 'warn')
  assert(
    evaluate(18, 40).band === 'alert' && voicePhrase(evaluate(18, 40)).includes('crítico'),
    'alert',
  )
  assert(evaluate(18, 10).band === 'ok', 'low speed skip')

  // OBD PID 015E: 41 5E 02 58 → (0x0258)/20 = 30.4 g/s
  const a = 0x02
  const b = 0x58
  assert(((a * 256 + b) / 20).toFixed(1) === '30.0', 'pid 015E')

  const deviceId = `fuel-rate-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Fuel rate smoke',
      app_version: '0.72.0',
      version_code: 74,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const warnGps = (60 * 740) / 3600 // ~60 L/h
  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.72.0',
      version_code: 74,
      vehicle_signals: {
        fuel_rate_gps: warnGps,
        fuel_rate_lph: 60,
        speed_kmh: 50,
        fuel_rate_warn_lph: 55,
        fuel_rate_alert_lph: 80,
        fuel_rate_speed_min_kmh: 20,
        fuel_rate: { fuel_rate_lph: 60, band: 'warn', show_warn: true, speed_kmh: 50 },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('fuel_rate_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const alertGps = (90 * 740) / 3600
  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.72.0',
      version_code: 74,
      vehicle_signals: {
        fuel_rate_gps: alertGps,
        fuel_rate_lph: 90,
        speed_kmh: 55,
        fuel_rate_warn_lph: 55,
        fuel_rate_alert_lph: 80,
        fuel_rate_speed_min_kmh: 20,
        fuel_rate: { fuel_rate_lph: 90, band: 'alert', show_warn: true, speed_kmh: 55 },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('fuel_rate_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK fuel-rate-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
