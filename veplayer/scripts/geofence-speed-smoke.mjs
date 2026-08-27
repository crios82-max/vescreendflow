#!/usr/bin/env node
/**
 * Geofence speed limits smoke (VePlayer 0.34).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
const TOKEN = 'fleet-admin-demo'

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
  console.log('geofence-speed-smoke →', BASE)
  const deviceId = `gf-speed-${Date.now().toString(36)}`

  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Geofence speed smoke',
      app_version: '0.34.0',
      version_code: 36,
    }),
  })
  assert(reg.ok, `register ${reg.status}`)

  // Outside any fence → no speed_zone
  const out = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.0,
      lng: -66.0,
      app_version: '0.34.0',
      version_code: 36,
      vehicle_signals: { speed_mps: 20 },
    }),
  })
  assert(out.ok, `hb out ${out.status}`)
  assert(out.body.speed_zone == null, 'no zone outside')

  // Inside Base Caracas (10.496, -66.898, r=400, max 40)
  const inside = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.496,
      lng: -66.898,
      app_version: '0.34.0',
      version_code: 36,
      vehicle_signals: { speed_mps: 5 },
    }),
  })
  assert(inside.ok, `hb inside ${inside.status}`)
  assert(inside.body.speed_zone?.max_kmh === 40, `zone max ${JSON.stringify(inside.body.speed_zone)}`)
  assert(String(inside.body.speed_zone?.name || '').includes('Caracas'), 'zone name')
  const zoneId = inside.body.speed_zone.id

  // Overspeed in zone → geofence_speed alert
  const over = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.4961,
      lng: -66.8981,
      app_version: '0.34.0',
      version_code: 36,
      vehicle_signals: { speed_mps: 15 }, // ~54 km/h > 40+2
    }),
  })
  assert(over.ok, `hb over ${over.status}`)
  const raised = over.body.alerts_raised || []
  assert(
    raised.some((k) => String(k).startsWith('geofence_speed:')),
    `raised ${JSON.stringify(raised)}`,
  )

  const alerts = await j('/api/fleet/alerts?open=1')
  assert(
    (alerts.body.alerts || []).some(
      (a) => a.device_id === deviceId && String(a.kind).startsWith('geofence_speed'),
    ),
    'open geofence_speed',
  )

  // Create fence with max_kmh + PATCH
  const created = await j(
    '/api/fleet/geofences',
    {
      method: 'POST',
      body: JSON.stringify({
        name: `Smoke zone ${Date.now().toString(36)}`,
        lat: 10.5,
        lng: -66.9,
        radius_m: 200,
        max_kmh: 30,
        active: true,
      }),
    },
    TOKEN,
  )
  assert(created.ok || created.status === 201, `create ${created.status} ${JSON.stringify(created.body)}`)
  const gfId = created.body.id
  assert(gfId, 'create id')

  const patched = await j(
    `/api/fleet/geofences/${gfId}`,
    {
      method: 'PATCH',
      body: JSON.stringify({ max_kmh: 25 }),
    },
    TOKEN,
  )
  assert(patched.ok, `patch ${patched.status}`)
  assert(
    Number(patched.body.geofence?.max_kmh) === 25,
    `patched max ${JSON.stringify(patched.body)}`,
  )

  const list = await j('/api/fleet/geofences')
  assert(
    (list.body.geofences || []).some((g) => g.id === gfId && Number(g.max_kmh) === 25),
    `list ${JSON.stringify(list.body).slice(0, 240)}`,
  )

  console.log('OK geofence-speed-smoke · zone', zoneId, '· gf', gfId)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
