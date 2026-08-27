#!/usr/bin/env node
/**
 * Eco scorecards smoke (VePlayer 0.36).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluateEco({ idle_sec, overspeed_sec, abs_events, high_throttle_sec }) {
  const idlePen = Math.min(30, Math.floor(idle_sec / 60) * 2)
  const overPen = Math.min(40, Math.floor(overspeed_sec / 8))
  const absPen = Math.min(20, abs_events * 5)
  const thrPen = Math.min(20, Math.floor(high_throttle_sec / 15))
  const score = Math.max(0, Math.min(100, 100 - idlePen - overPen - absPen - thrPen))
  const band = score >= 80 ? 'good' : score >= 55 ? 'fair' : 'poor'
  return { score, band }
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
  console.log('eco-score-smoke →', BASE)
  const good = evaluateEco({ idle_sec: 0, overspeed_sec: 0, abs_events: 0, high_throttle_sec: 0 })
  assert(good.score === 100 && good.band === 'good', 'perfect')
  const poor = evaluateEco({ idle_sec: 1800, overspeed_sec: 400, abs_events: 5, high_throttle_sec: 300 })
  assert(poor.score < 55 && poor.band === 'poor', `poor ${JSON.stringify(poor)}`)

  const deviceId = `eco-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Eco smoke',
      app_version: '0.36.0',
      version_code: 38,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const start = await j('/api/fleet/shifts/start', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, odo_km: 1000 }),
  })
  assert(start.ok || start.status === 201, `start ${start.status}`)
  assert(start.body.shift?.eco_score === 100, `eco start ${JSON.stringify(start.body.shift)}`)

  // Overspeed heartbeats
  for (let i = 0; i < 3; i++) {
    const hb = await j('/api/fleet/heartbeat', {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        odo_km: 1000.5 + i * 0.2,
        vehicle_signals: {
          speed_mps: 25, // 90 km/h
          speed_limit_kmh: 50,
          throttle_pct: 90,
          ignition: 'on',
          abs_active: i === 0,
        },
      }),
    })
    assert(hb.ok, `hb ${i}`)
    assert(hb.body.shift?.eco_score != null, 'shift eco in hb')
  }

  const cur = await j(`/api/fleet/shifts/current?device_id=${deviceId}`)
  assert(cur.body.shift?.overspeed_sec > 0, `over ${JSON.stringify(cur.body.shift)}`)
  assert(cur.body.shift.eco_score < 100, `score dropped ${cur.body.shift.eco_score}`)

  const end = await j('/api/fleet/shifts/end', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, odo_km: 1002 }),
  })
  assert(end.ok, 'end')
  assert(end.body.shift?.status === 'closed', 'closed')
  assert(typeof end.body.shift.eco_score === 'number', 'final eco')
  assert(end.body.shift.eco_band, 'band')

  console.log(
    'OK eco-score-smoke · score',
    end.body.shift.eco_score,
    end.body.shift.eco_band,
    '· over',
    end.body.shift.overspeed_sec,
  )
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
