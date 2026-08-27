#!/usr/bin/env node
/**
 * Harsh brake / accel smoke (VePlayer 0.45 · Fase 8).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(
  accelKmhS,
  absActive = false,
  brakeWarn = 12,
  brakeAlert = 18,
  accelWarn = 10,
  accelAlert = 15,
) {
  const brakeMag = Math.max(0, -accelKmhS)
  const accelMag = Math.max(0, accelKmhS)
  if (brakeMag >= brakeWarn || (absActive && brakeMag >= brakeWarn * 0.6)) {
    const band =
      brakeMag >= brakeAlert || (absActive && brakeMag >= brakeWarn) ? 'brake_alert' : 'brake_warn'
    return { band, kind: 'brake', showWarn: true, label: `Frenada · ${Math.round(brakeMag)}` }
  }
  if (accelMag >= accelWarn) {
    const band = accelMag >= accelAlert ? 'accel_alert' : 'accel_warn'
    return { band, kind: 'accel', showWarn: true, label: `Acel. · ${Math.round(accelMag)}` }
  }
  return { band: 'ok', kind: '', showWarn: false, label: '' }
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
  console.log('harsh-driving-smoke →', BASE)
  assert(evaluate(0).band === 'ok', 'ok')
  assert(evaluate(-14).band === 'brake_warn', 'brake warn')
  assert(evaluate(-20).band === 'brake_alert', 'brake alert')
  assert(evaluate(12).band === 'accel_warn', 'accel warn')
  assert(evaluate(16).band === 'accel_alert', 'accel alert')
  assert(evaluate(-8, true).band === 'brake_warn', 'abs soft')
  assert(evaluate(-13, true).band === 'brake_alert', 'abs escalate')

  const deviceId = `harsh-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Harsh smoke',
      app_version: '0.45.0',
      version_code: 47,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.45.0',
      version_code: 47,
      vehicle_signals: {
        harsh: { kind: 'brake', band: 'brake_warn', accel_kmh_s: -14, abs: false },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert((hbWarn.body.alerts_raised || []).includes('brake_warn'), `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.45.0',
      version_code: 47,
      vehicle_signals: {
        harsh: { kind: 'accel', band: 'accel_alert', accel_kmh_s: 17, abs: false },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('accel_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK harsh-driving-smoke · brake_warn + accel_alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
