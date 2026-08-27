#!/usr/bin/env node
/**
 * Fuel pressure smoke (VePlayer 0.80 · Fase 15).
 * OBD PID 010A — fuel rail pressure kPa (A * 3).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(pressureKpa, speedKmh = 40, warnKpa = 280, alertKpa = 220, speedMin = 20) {
  if (pressureKpa == null) return { band: 'idle', showWarn: false, label: '' }
  const kpa = Math.max(0, Math.min(765, pressureKpa))
  const alert = Math.max(100, Math.min(400, alertKpa))
  const warn = Math.min(500, Math.max(alert + 20, warnKpa))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  if (speedKmh < minSpd) {
    return {
      pressureKpa: kpa,
      band: 'ok',
      showWarn: false,
      label: kpa <= 350 ? `FuelP · ${Math.trunc(kpa)} kPa` : '',
    }
  }
  let band = 'ok'
  if (kpa <= alert) band = 'alert'
  else if (kpa <= warn) band = 'warn'
  return {
    pressureKpa: kpa,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `FuelP · ${Math.trunc(kpa)} kPa`,
  }
}

function voicePhrase(st) {
  const k = st.pressureKpa != null ? `${Math.trunc(st.pressureKpa)} kilopascales` : 'baja'
  if (st.band === 'alert') {
    return `Atención. Presión de combustible crítica. ${k}. Revisa bomba y filtro.`
  }
  if (st.band === 'warn') return `Cuidado. Presión de combustible baja. ${k}.`
  return `Presión combustible a ${k}.`
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
  console.log('fuel-pressure-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(350, 40).band === 'ok', 'ok')
  assert(evaluate(260, 40).band === 'warn' && evaluate(260, 40).showWarn, 'warn')
  assert(
    evaluate(200, 40).band === 'alert' && voicePhrase(evaluate(200, 40)).includes('crítica'),
    'alert',
  )
  assert(evaluate(200, 10).band === 'ok', 'low speed skip')

  // OBD PID 010A: 41 0A 57 → 0x57 * 3 = 261 kPa
  const a = 0x57
  assert(a * 3 === 261, 'pid 010A')

  const deviceId = `fuel-press-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Fuel press smoke',
      app_version: '0.80.0',
      version_code: 82,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.80.0',
      version_code: 82,
      vehicle_signals: {
        fuel_pressure_kpa: 260,
        speed_kmh: 45,
        fuel_press_warn_kpa: 280,
        fuel_press_alert_kpa: 220,
        fuel_press_speed_min_kmh: 20,
        fuel_pressure: {
          pressure_kpa: 260,
          speed_kmh: 45,
          band: 'warn',
          show_warn: true,
        },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('fuel_press_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.80.0',
      version_code: 82,
      vehicle_signals: {
        fuel_pressure_kpa: 200,
        speed_kmh: 50,
        fuel_press_warn_kpa: 280,
        fuel_press_alert_kpa: 220,
        fuel_press_speed_min_kmh: 20,
        fuel_pressure: {
          pressure_kpa: 200,
          speed_kmh: 50,
          band: 'alert',
          show_warn: true,
        },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('fuel_press_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK fuel-pressure-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
