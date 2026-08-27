#!/usr/bin/env node
/**
 * High throttle smoke (VePlayer 0.64 · Fase 12).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(
  throttlePct,
  speedKmh = 40,
  highForSec = 0,
  warnPct = 70,
  alertPct = 85,
  alertHoldSec = 8,
  speedMinKmh = 20,
) {
  if (throttlePct == null) return { band: 'idle', showWarn: false, label: '' }
  const thr = Math.max(0, Math.min(100, throttlePct))
  const speed = Math.max(0, speedKmh)
  const warn = Math.max(40, Math.min(95, warnPct))
  const alert = Math.min(100, Math.max(warn + 5, alertPct))
  const hold = Math.max(2, Math.min(60, alertHoldSec))
  if (speed < speedMinKmh) {
    return { band: 'ok', showWarn: false, throttlePct: thr, label: thr >= 40 ? `Acel · ${Math.trunc(thr)}%` : '' }
  }
  const high = thr >= warn
  let band = 'ok'
  if (thr >= alert || (high && highForSec >= hold)) band = 'alert'
  else if (high) band = 'warn'
  return {
    throttlePct: thr,
    highForSec: high ? highForSec : 0,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `Acel · ${Math.trunc(thr)}%`,
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
  console.log('high-throttle-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(40, 40).band === 'ok', 'ok')
  assert(evaluate(75, 40, 2).band === 'warn' && evaluate(75, 40, 2).showWarn, 'warn')
  assert(evaluate(90, 40, 1).band === 'alert', 'alert pct')
  assert(evaluate(75, 40, 9).band === 'alert', 'alert hold')
  assert(evaluate(90, 5).band === 'ok' && !evaluate(90, 5).showWarn, 'below min speed')

  // OBD PID 0111: 41 11 A0 → A*100/255 ≈ 62.7%
  assert(Math.round((0xa0 * 100) / 255) === 63, 'pid 0111')

  const deviceId = `thr-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Throttle smoke',
      app_version: '0.64.0',
      version_code: 66,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.64.0',
      version_code: 66,
      vehicle_signals: {
        throttle_pct: 75,
        throttle_high_sec: 3,
        throttle_warn_pct: 70,
        throttle_alert_pct: 85,
        throttle_alert_hold_sec: 8,
        speed_kmh: 45,
        throttle: { throttle_pct: 75, high_for_sec: 3, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('throttle_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.64.0',
      version_code: 66,
      vehicle_signals: {
        throttle_pct: 92,
        throttle_high_sec: 2,
        throttle_warn_pct: 70,
        throttle_alert_pct: 85,
        throttle_alert_hold_sec: 8,
        speed_kmh: 50,
        throttle: { throttle_pct: 92, high_for_sec: 2, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('throttle_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK high-throttle-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
