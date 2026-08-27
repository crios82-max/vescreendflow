#!/usr/bin/env node
/**
 * Engine oil temp smoke (VePlayer 0.70 · Fase 13).
 * OBD PID 015C — A - 40 °C.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(oilTempC, warnC = 120, alertC = 130) {
  if (oilTempC == null) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.max(90, warnC)
  const alert = Math.max(warn + 1, alertC)
  let band = 'ok'
  if (oilTempC >= alert) band = 'alert'
  else if (oilTempC >= warn) band = 'warn'
  return {
    oilTempC,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `${Math.trunc(oilTempC)}°C`,
  }
}

function voicePhrase(st) {
  const c = st.oilTempC != null ? `${Math.trunc(st.oilTempC)} grados` : 'elevada'
  if (st.band === 'alert') {
    return `Atención. Aceite del motor crítico. ${c}. Detén el vehículo con seguridad.`
  }
  if (st.band === 'warn') return `Cuidado. Aceite caliente. ${c}.`
  return `Aceite a ${c}.`
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
  console.log('oil-temp-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(100).band === 'ok', 'ok')
  assert(evaluate(125).band === 'warn' && evaluate(125).showWarn, 'warn')
  assert(
    evaluate(135).band === 'alert' && voicePhrase(evaluate(135)).includes('crítico'),
    'alert',
  )

  // OBD PID 015C: 41 5C 9C → 0x9C - 40 = 116 °C
  const a = 0x9c
  assert(a - 40 === 116, 'pid 015C')

  const deviceId = `oil-temp-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Oil temp smoke',
      app_version: '0.70.0',
      version_code: 72,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.70.0',
      version_code: 72,
      vehicle_signals: {
        oil_temp_c: 125,
        oil_temp_warn_c: 120,
        oil_temp_alert_c: 130,
        oil_temp: { oil_temp_c: 125, band: 'warn', show_warn: true, label: '125°C' },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('oil_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.70.0',
      version_code: 72,
      vehicle_signals: {
        oil_temp_c: 140,
        oil_temp_warn_c: 120,
        oil_temp_alert_c: 130,
        oil_temp: { oil_temp_c: 140, band: 'alert', show_warn: true, label: '140°C' },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('oil_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK oil-temp-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
