#!/usr/bin/env node
/**
 * Intake air temp smoke (VePlayer 0.71 · Fase 13).
 * OBD PID 010F — A - 40 °C (high IAT / heat soak).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(intakeAirC, warnC = 50, alertC = 60) {
  if (intakeAirC == null) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.max(30, warnC)
  const alert = Math.max(warn + 1, alertC)
  let band = 'ok'
  if (intakeAirC >= alert) band = 'alert'
  else if (intakeAirC >= warn) band = 'warn'
  return {
    intakeAirC,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `${Math.trunc(intakeAirC)}°C`,
  }
}

function voicePhrase(st) {
  const c = st.intakeAirC != null ? `${Math.trunc(st.intakeAirC)} grados` : 'elevada'
  if (st.band === 'alert') {
    return `Atención. Aire de admisión muy caliente. ${c}. Reduce carga o detente.`
  }
  if (st.band === 'warn') return `Cuidado. Admisión caliente. Aire a ${c}.`
  return `Admisión a ${c}.`
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
  console.log('intake-air-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(42).band === 'ok', 'ok')
  assert(evaluate(55).band === 'warn' && evaluate(55).showWarn, 'warn')
  assert(
    evaluate(65).band === 'alert' && voicePhrase(evaluate(65)).includes('caliente'),
    'alert',
  )

  // OBD PID 010F: 41 0F 6E → 0x6E - 40 = 70 °C
  const a = 0x6e
  assert(a - 40 === 70, 'pid 010F')

  const deviceId = `intake-air-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Intake air smoke',
      app_version: '0.71.0',
      version_code: 73,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.71.0',
      version_code: 73,
      vehicle_signals: {
        intake_air_c: 55,
        intake_air_warn_c: 50,
        intake_air_alert_c: 60,
        intake_air: { intake_air_c: 55, band: 'warn', show_warn: true, label: '55°C' },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('intake_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.71.0',
      version_code: 73,
      vehicle_signals: {
        intake_air_c: 68,
        intake_air_warn_c: 50,
        intake_air_alert_c: 60,
        intake_air: { intake_air_c: 68, band: 'alert', show_warn: true, label: '68°C' },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('intake_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK intake-air-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
