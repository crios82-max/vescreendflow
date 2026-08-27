#!/usr/bin/env node
/** Timing advance smoke (VePlayer 0.83 · Fase 15). OBD PID 010E degrees. */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(timingDeg, speedKmh = 40, rpm = 2000, warnDeg = 38, alertDeg = 45, speedMin = 20, rpmMin = 800) {
  if (timingDeg == null) return { band: 'idle', showWarn: false, label: '' }
  const deg = Math.max(-64, Math.min(64, timingDeg))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  const rpmOk = rpm == null || rpm >= rpmMin
  if (speedKmh < minSpd || !rpmOk) {
    return { timingDeg: deg, band: 'ok', showWarn: false, label: deg >= 5 ? `Timing · ${Math.trunc(deg)}°` : '' }
  }
  let band = 'ok'
  if (deg >= alertDeg) band = 'alert'
  else if (deg >= warnDeg) band = 'warn'
  return { timingDeg: deg, band, showWarn: band === 'warn' || band === 'alert', label: `Timing · ${Math.trunc(deg)}°` }
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
  console.log('timing-advance-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(25, 40).band === 'ok', 'ok')
  assert(evaluate(40, 40).band === 'warn', 'warn')
  assert(evaluate(48, 40).band === 'alert', 'alert')
  assert(evaluate(48, 10).band === 'ok', 'low speed')
  // PID 010E: 41 0E BA → (0xBA/2)-64 = 29°
  assert((0xba / 2) - 64 === 29, 'pid 010E')

  const deviceId = `timing-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Timing smoke', app_version: '0.83.0', version_code: 85 }),
  })

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.83.0',
      version_code: 85,
      vehicle_signals: {
        timing_advance_deg: 40,
        speed_kmh: 45,
        rpm: 2200,
        timing_warn_deg: 38,
        timing_alert_deg: 45,
        timing_speed_min_kmh: 20,
        timing_rpm_min: 800,
        timing_advance: { timing_deg: 40, speed_kmh: 45, rpm: 2200, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, 'hb warn')
  assert((hbWarn.body.alerts_raised || []).includes('timing_warn'), `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.83.0',
      version_code: 85,
      vehicle_signals: {
        timing_advance_deg: 48,
        speed_kmh: 50,
        rpm: 2500,
        timing_warn_deg: 38,
        timing_alert_deg: 45,
        timing_advance: { timing_deg: 48, speed_kmh: 50, rpm: 2500, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, 'hb alert')
  assert((hbAlert.body.alerts_raised || []).includes('timing_alert'), `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`)
  console.log('OK timing-advance-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
