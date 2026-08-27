#!/usr/bin/env node
/**
 * Impact / collision detect smoke (VePlayer 0.55 · Fase 10).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(
  decelKmhS,
  yawDegS,
  speedKmh,
  decelWarn = 28,
  decelAlert = 40,
  yawWarn = 80,
  yawAlert = 120,
  speedMin = 8,
) {
  const speed = Math.max(0, speedKmh)
  const decel = Math.max(0, decelKmhS)
  const yaw = Math.abs(yawDegS)
  if (speed < speedMin && decel < decelWarn && yaw < yawWarn) {
    return { band: 'idle', showWarn: false, label: '' }
  }
  const dWarn = Math.max(15, Math.min(60, decelWarn))
  const dAlert = Math.max(dWarn + 1, decelAlert)
  const yWarn = Math.max(40, Math.min(200, yawWarn))
  const yAlert = Math.max(yWarn + 1, yawAlert)
  let decelBand = 'ok'
  if (decel >= dAlert) decelBand = 'alert'
  else if (decel >= dWarn) decelBand = 'warn'
  let yawBand = 'ok'
  if (yaw >= yAlert) yawBand = 'alert'
  else if (yaw >= yWarn) yawBand = 'warn'
  let band = 'ok'
  if (decelBand === 'alert' || yawBand === 'alert') band = 'alert'
  else if (decelBand === 'warn' || yawBand === 'warn') band = 'warn'
  if (band === 'ok') return { band: 'ok', showWarn: false, label: '' }
  let kind = 'decel'
  if (decelBand === 'alert' && yawBand !== 'alert') kind = 'decel'
  else if (yawBand === 'alert' && decelBand !== 'alert') kind = 'yaw'
  else if (decel >= yaw / 2) kind = 'decel'
  else kind = 'yaw'
  return {
    band,
    kind,
    showWarn: true,
    decelKmhS: decel,
    yawDegS: yaw,
    label: kind === 'yaw' ? `Impacto · yaw ${Math.trunc(yaw)}°/s` : `Impacto · ${Math.trunc(decel)} km/h/s`,
  }
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
  console.log('impact-detect-smoke →', BASE)
  assert(evaluate(10, 0, 40).band === 'ok', 'ok')
  assert(evaluate(30, 0, 40).band === 'warn' && evaluate(30, 0, 40).kind === 'decel', 'warn decel')
  assert(evaluate(45, 0, 50).band === 'alert', 'alert decel')
  assert(evaluate(0, 90, 30).band === 'warn' && evaluate(0, 90, 30).kind === 'yaw', 'warn yaw')
  assert(evaluate(0, 130, 30).band === 'alert', 'alert yaw')

  const deviceId = `impact-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Impact smoke',
      app_version: '0.55.0',
      version_code: 57,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.55.0',
      version_code: 57,
      vehicle_signals: {
        impact: {
          band: 'warn',
          kind: 'decel',
          decel_kmh_s: 32,
          yaw_deg_s: 10,
          speed_kmh: 45,
          g_approx: 0.9,
          show_warn: true,
        },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('impact_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.55.0',
      version_code: 57,
      vehicle_signals: {
        impact: {
          band: 'alert',
          kind: 'yaw',
          decel_kmh_s: 12,
          yaw_deg_s: 140,
          speed_kmh: 50,
          g_approx: 0.3,
          show_warn: true,
        },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('impact_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK impact-detect-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
