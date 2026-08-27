#!/usr/bin/env node
/**
 * Driver safety scorecard smoke (VePlayer 0.58 · Fase 10).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(
  {
    harsh_brake_events = 0,
    harsh_accel_events = 0,
    overspeed_sec = 0,
    seatbelt_events = 0,
    impact_events = 0,
    route_dev_sec = 0,
  },
  warnScore = 70,
  alertScore = 50,
  active = true,
) {
  if (!active) return { score: 100, band: 'idle', showWarn: false, active: false }
  const brakePen = Math.min(25, harsh_brake_events * 5)
  const accelPen = Math.min(20, harsh_accel_events * 4)
  const overPen = Math.min(25, Math.floor(overspeed_sec / 12))
  const beltPen = Math.min(24, seatbelt_events * 8)
  const impactPen = Math.min(24, impact_events * 12)
  const routePen = Math.min(15, Math.floor(route_dev_sec / 40))
  const score = Math.max(
    0,
    Math.min(100, 100 - brakePen - accelPen - overPen - beltPen - impactPen - routePen),
  )
  const band = score >= 80 ? 'good' : score >= 60 ? 'fair' : 'poor'
  return {
    score,
    band,
    showWarn: score < warnScore,
    active: true,
    penalties: {
      harsh_brake: brakePen,
      harsh_accel: accelPen,
      overspeed: overPen,
      seatbelt: beltPen,
      impact: impactPen,
      route: routePen,
    },
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
  console.log('driver-scorecard-smoke →', BASE)
  const perfect = evaluate({})
  assert(perfect.score === 100 && perfect.band === 'good', 'perfect')
  const mid = evaluate({ harsh_brake_events: 3, overspeed_sec: 120, seatbelt_events: 1 })
  assert(mid.score < 70 && mid.showWarn, `mid ${JSON.stringify(mid)}`)
  const poor = evaluate({
    harsh_brake_events: 5,
    harsh_accel_events: 5,
    overspeed_sec: 400,
    seatbelt_events: 3,
    impact_events: 2,
    route_dev_sec: 200,
  })
  assert(poor.band === 'poor' && poor.score <= 50, `poor ${JSON.stringify(poor)}`)

  const deviceId = `score-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Driver score smoke',
      app_version: '0.58.0',
      version_code: 60,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.58.0',
      version_code: 60,
      vehicle_signals: {
        driver_score_warn: 70,
        driver_score_alert: 50,
        driver_score: {
          score: 62,
          band: 'fair',
          show_warn: true,
          active: true,
          harsh_brake_events: 2,
          overspeed_sec: 80,
        },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('score_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.58.0',
      version_code: 60,
      vehicle_signals: {
        driver_score_warn: 70,
        driver_score_alert: 50,
        driver_score: {
          score: 42,
          band: 'poor',
          show_warn: true,
          active: true,
          impact_events: 2,
          seatbelt_events: 2,
        },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('score_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK driver-scorecard-smoke · warn+alert · score', mid.score, '→', poor.score)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
