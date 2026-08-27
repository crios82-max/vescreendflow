#!/usr/bin/env node
/**
 * Eco live HUD smoke (VePlayer 0.67 · Fase 12).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluateEcoAccum({ idle_sec = 0, overspeed_sec = 0, abs_events = 0, high_throttle_sec = 0 }) {
  const idlePen = Math.min(30, Math.floor(idle_sec / 60) * 2)
  const overPen = Math.min(40, Math.floor(overspeed_sec / 8))
  const absPen = Math.min(20, abs_events * 5)
  const thrPen = Math.min(20, Math.floor(high_throttle_sec / 15))
  const score = Math.max(0, Math.min(100, 100 - idlePen - overPen - absPen - thrPen))
  const band = score >= 80 ? 'good' : score >= 55 ? 'fair' : 'poor'
  return { score, band }
}

function evaluateLive(score, warnScore = 70, alertScore = 50, active = true) {
  if (!active || score == null) return { band: 'idle', showWarn: false, active: false, score: 100 }
  const s = Math.max(0, Math.min(100, score))
  const band = s >= 80 ? 'good' : s >= 55 ? 'fair' : 'poor'
  return {
    score: s,
    band,
    showWarn: s < warnScore,
    active: true,
    label: `Eco ${s} · ${band}`,
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
  console.log('eco-live-smoke →', BASE)
  assert(evaluateEcoAccum({}).score === 100, 'perfect')
  assert(evaluateLive(100).band === 'good' && !evaluateLive(100).showWarn, 'good')
  assert(evaluateLive(65).showWarn && evaluateLive(65).band === 'fair', 'warn fair')
  assert(evaluateLive(42).band === 'poor' && evaluateLive(42).showWarn, 'poor')

  const deviceId = `eco-live-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Eco live smoke',
      app_version: '0.67.0',
      version_code: 69,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.67.0',
      version_code: 69,
      vehicle_signals: {
        eco_score: 62,
        eco_band: 'fair',
        eco_warn_score: 70,
        eco_alert_score: 50,
        eco_live: { score: 62, band: 'fair', show_warn: true, active: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('eco_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.67.0',
      version_code: 69,
      vehicle_signals: {
        eco_score: 40,
        eco_band: 'poor',
        eco_warn_score: 70,
        eco_alert_score: 50,
        eco_live: { score: 40, band: 'poor', show_warn: true, active: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('eco_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK eco-live-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
