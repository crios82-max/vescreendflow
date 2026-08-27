#!/usr/bin/env node
/**
 * Geofence exit smoke (VePlayer 0.46 · Fase 8).
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
  console.log('geofence-exit-smoke →', BASE)
  const deviceId = `gf-exit-${Date.now().toString(36)}`

  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Geofence exit smoke',
      app_version: '0.46.0',
      version_code: 48,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const gf = await j(
    '/api/fleet/geofences',
    {
      method: 'POST',
      body: JSON.stringify({
        name: `Exit smoke ${Date.now().toString(36)}`,
        lat: 10.2,
        lng: -66.2,
        radius_m: 200,
        max_kmh: 30,
        active: true,
      }),
    },
    TOKEN,
  )
  assert(gf.ok || gf.status === 201, `gf create ${gf.status} ${JSON.stringify(gf.body)}`)
  const gfId = gf.body.id ?? gf.body.geofence?.id
  assert(gfId, `gf id ${JSON.stringify(gf.body)}`)

  const enter = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.2,
      lng: -66.2,
      app_version: '0.46.0',
      version_code: 48,
      vehicle_signals: { speed_mps: 2 },
    }),
  })
  assert(enter.ok, `enter hb ${enter.status}`)
  const raisedEnter = enter.body.alerts_raised || []
  assert(
    raisedEnter.includes(`geofence_enter:${gfId}`),
    `enter raised ${JSON.stringify(raisedEnter)}`,
  )

  const still = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.2005,
      lng: -66.2005,
      app_version: '0.46.0',
      version_code: 48,
    }),
  })
  assert(still.ok, 'still inside')
  assert(
    !(still.body.alerts_raised || []).includes(`geofence_enter:${gfId}`),
    'no re-enter',
  )

  const exit = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.0,
      lng: -66.0,
      app_version: '0.46.0',
      version_code: 48,
    }),
  })
  assert(exit.ok, `exit hb ${exit.status}`)
  const raisedExit = exit.body.alerts_raised || []
  assert(
    raisedExit.includes(`geofence_exit:${gfId}`),
    `exit raised ${JSON.stringify(raisedExit)}`,
  )

  const open = await j('/api/fleet/alerts?open=1')
  assert(
    (open.body.alerts || []).some(
      (a) => a.device_id === deviceId && a.kind === `geofence_exit:${gfId}`,
    ),
    'open exit alert',
  )

  console.log('OK geofence-exit-smoke · enter+exit gf', gfId)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
