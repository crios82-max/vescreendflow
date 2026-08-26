#!/usr/bin/env node
/**
 * Fleet shift / trip log smoke (VePlayer 0.25).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

async function j(path, init = {}, token) {
  const headers = { 'content-type': 'application/json', ...(init.headers || {}) }
  if (token) headers['x-fleet-token'] = token
  const r = await fetch(BASE + path, { ...init, headers })
  const text = await r.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { ok: r.ok, status: r.status, body }
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

async function main() {
  console.log('shift-smoke →', BASE)
  const deviceId = `shift-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Shift smoke',
      app_version: '0.25.0',
      version_code: 27,
    }),
  })

  const login = await j('/api/fleet/drivers/login', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, code: 'D001', pin: '1234' }),
  })
  assert(login.ok && login.body.shift?.status === 'open', 'login opens shift')
  const shiftId = login.body.shift.id

  const cur = await j(`/api/fleet/shifts/current?device_id=${deviceId}`)
  assert(cur.body.shift?.id === shiftId, 'current shift')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.25.0',
      version_code: 27,
      driver_id: login.body.driver.id,
      odo_km: 100.0,
      vehicle_signals: { odometer_km: 100.0, speed_mps: 10 },
    }),
  })
  assert(hb.ok && hb.body.shift?.status === 'open', 'hb shift')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      driver_id: login.body.driver.id,
      odo_km: 112.5,
      vehicle_signals: { odometer_km: 112.5 },
    }),
  })
  assert(hb2.body.shift?.distance_km >= 12, `distance ${hb2.body.shift?.distance_km}`)

  const end = await j('/api/fleet/shifts/end', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, odo_km: 115, distance_km: 15 }),
  })
  assert(end.ok && end.body.shift.status === 'closed', 'closed')
  assert(end.body.shift.distance_km >= 15, 'end distance')

  const none = await j(`/api/fleet/shifts/current?device_id=${deviceId}`)
  assert(none.body.shift == null, 'no open')

  const list = await j(`/api/fleet/shifts?device_id=${deviceId}`)
  assert(list.body.shifts?.length >= 1, 'history')

  const ops = await j('/api/fleet/ops/shifts?limit=10', {}, 'fleet-viewer-demo')
  assert(ops.ok && Array.isArray(ops.body.shifts), 'ops shifts')

  const csv = await fetch(BASE + '/api/fleet/ops/reports/export?kind=shifts&limit=20', {
    headers: { 'x-fleet-token': 'fleet-viewer-demo' },
  })
  assert(csv.ok, 'csv shifts')
  const text = await csv.text()
  assert(text.includes('distance_km'), 'csv header')

  console.log('OK shift-smoke · km', end.body.shift.distance_km)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
