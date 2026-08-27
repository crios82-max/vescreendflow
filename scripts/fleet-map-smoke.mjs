#!/usr/bin/env node
/**
 * Fleet live map API smoke (VePlayer / SenseFlow 0.32).
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
  console.log('fleet-map-smoke →', BASE)
  const deviceId = `map-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Map smoke',
      app_version: '0.32.0',
      version_code: 34,
    }),
  })

  // Seed positions via heartbeats + telemetry
  for (let i = 0; i < 5; i++) {
    const lat = 10.496 + i * 0.001
    const lng = -66.898 + i * 0.0008
    const hb = await j('/api/fleet/heartbeat', {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        lat,
        lng,
        speed_mps: 8 + i,
        vehicle_signals: { speed_mps: 8 + i, odometer_km: 1000 + i },
      }),
    })
    assert(hb.ok, `hb ${i}`)
    // telemetry samples skip if <25s — force via small wait only on last
  }

  // Force a few telemetry rows by direct API if needed — map uses last_lat + trail
  const map = await j('/api/fleet/ops/map?trail=40', {}, 'fleet-viewer-demo')
  assert(map.ok, `map ${JSON.stringify(map.body)}`)
  assert(map.body.counts?.units >= 1, 'units')
  const unit = (map.body.units || []).find((u) => u.device_id === deviceId)
  assert(unit, 'unit found')
  assert(unit.lat != null && unit.lng != null, 'located')
  assert(unit.online === true, 'online')
  assert(Array.isArray(map.body.geofences), 'geofences')
  assert(map.body.counts.located >= 1, 'located count')

  // Raise panic → map unit.panic
  await j('/api/fleet/panic', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, lat: unit.lat, lng: unit.lng }),
  })
  const map2 = await j('/api/fleet/ops/map?trail=10', {}, 'fleet-viewer-demo')
  const u2 = (map2.body.units || []).find((u) => u.device_id === deviceId)
  assert(u2?.panic?.id, 'panic on map')
  assert(map2.body.counts.panic >= 1, 'panic count')

  const html = await fetch(BASE + '/fleet.html')
  assert(html.ok, 'fleet.html')
  const text = await html.text()
  assert(text.includes('fleet-map') && text.includes('leaflet'), 'map markup')

  console.log('OK fleet-map-smoke ·', unit.lat.toFixed(4), unit.lng.toFixed(4), 'panic', u2.panic.id)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
