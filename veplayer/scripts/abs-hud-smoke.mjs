#!/usr/bin/env node
/**
 * ABS HUD smoke (VePlayer 0.63 · Fase 11).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(active, activeForSec = 0, events = 0, warnSec = 0.5, alertSec = 2, alertEvents = 3) {
  const held = Math.max(0, activeForSec)
  const n = Math.max(0, events)
  const warn = Math.max(0.2, Math.min(5, warnSec))
  const alert = Math.max(warn + 0.3, alertSec)
  const nAlert = Math.max(2, Math.min(20, alertEvents))
  if (!active && n <= 0 && held <= 0) return { band: 'idle', showWarn: false, label: '' }
  let band = 'idle'
  if ((active && held >= alert) || n >= nAlert) band = 'alert'
  else if (active && held >= warn) band = 'warn'
  else if (active || n > 0) band = 'ok'
  return {
    active: !!active,
    activeForSec: held,
    events: n,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: band === 'idle' ? '' : 'ABS',
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
  console.log('abs-hud-smoke →', BASE)
  assert(evaluate(false).band === 'idle', 'idle')
  assert(evaluate(true, 0.2).band === 'ok', 'ok short')
  assert(evaluate(true, 0.8).band === 'warn' && evaluate(true, 0.8).showWarn, 'warn')
  assert(evaluate(true, 2.5).band === 'alert', 'alert hold')
  assert(evaluate(false, 0, 3).band === 'alert', 'alert events')

  const deviceId = `abs-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'ABS HUD smoke',
      app_version: '0.63.0',
      version_code: 65,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.63.0',
      version_code: 65,
      vehicle_signals: {
        abs_active: true,
        abs_active_sec: 0.8,
        abs_events: 1,
        abs_warn_sec: 0.5,
        abs_alert_sec: 2,
        abs_alert_events: 3,
        abs: { active: true, active_for_sec: 0.8, events: 1, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('abs_warn') ||
      (hbWarn.body.alerts_raised || []).includes('abs'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.63.0',
      version_code: 65,
      vehicle_signals: {
        abs_active: true,
        abs_active_sec: 2.5,
        abs_events: 4,
        abs_warn_sec: 0.5,
        abs_alert_sec: 2,
        abs_alert_events: 3,
        abs: { active: true, active_for_sec: 2.5, events: 4, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('abs_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK abs-hud-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
