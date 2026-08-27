#!/usr/bin/env node
/** O2 voltage smoke (VePlayer 0.84 · Fase 15). OBD PID 014A volts. */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(o2Volts, speedKmh = 40, rpm = 2000, warnLow = 0.1, alertLow = 0.06, warnHigh = 0.88, alertHigh = 0.95, speedMin = 20, rpmMin = 800) {
  if (o2Volts == null) return { band: 'idle', showWarn: false, label: '' }
  const v = Math.max(0, Math.min(1.275, o2Volts))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  const rpmOk = rpm == null || rpm >= rpmMin
  if (speedKmh < minSpd || !rpmOk) {
    return { o2Volts: v, band: 'ok', showWarn: false, label: v >= 0.15 && v <= 0.85 ? `O2 · ${v.toFixed(2)} V` : '' }
  }
  let band = 'ok'
  if (v <= alertLow || v >= alertHigh) band = 'alert'
  else if (v <= warnLow || v >= warnHigh) band = 'warn'
  return { o2Volts: v, band, showWarn: band === 'warn' || band === 'alert', label: `O2 · ${v.toFixed(2)} V` }
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
  console.log('o2-voltage-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(0.45, 40).band === 'ok', 'ok')
  assert(evaluate(0.08, 40).band === 'warn', 'warn low')
  assert(evaluate(0.04, 40).band === 'alert', 'alert low')
  assert(evaluate(0.9, 40).band === 'warn', 'warn high')
  assert(evaluate(0.98, 40).band === 'alert', 'alert high')
  assert(evaluate(0.04, 10).band === 'ok', 'low speed')
  // PID 014A: 41 4A 5A → 0x5A/200 = 0.45 V
  assert(0x5a / 200 === 0.45, 'pid 014A')

  const deviceId = `o2-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'O2 smoke', app_version: '0.84.0', version_code: 86 }),
  })

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.84.0',
      version_code: 86,
      vehicle_signals: {
        o2_b1s1_volts: 0.08,
        speed_kmh: 45,
        rpm: 2200,
        o2_warn_low_v: 0.1,
        o2_alert_low_v: 0.06,
        o2_warn_high_v: 0.88,
        o2_alert_high_v: 0.95,
        o2_speed_min_kmh: 20,
        o2_rpm_min: 800,
        o2_voltage: { o2_volts: 0.08, speed_kmh: 45, rpm: 2200, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, 'hb warn')
  assert((hbWarn.body.alerts_raised || []).includes('o2_warn'), `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.84.0',
      version_code: 86,
      vehicle_signals: {
        o2_b1s1_volts: 0.04,
        speed_kmh: 50,
        rpm: 2500,
        o2_alert_low_v: 0.06,
        o2_voltage: { o2_volts: 0.04, speed_kmh: 50, rpm: 2500, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, 'hb alert')
  assert((hbAlert.body.alerts_raised || []).includes('o2_alert'), `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`)
  console.log('OK o2-voltage-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
