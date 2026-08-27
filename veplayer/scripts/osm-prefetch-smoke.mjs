#!/usr/bin/env node
/**
 * OSM offline prefetch math smoke (VePlayer 0.37).
 * Mirrors WebMercator.tilesForBoundsRange + MapBounds.around.
 */
const TILE = 256
const PI = Math.PI

function assert(c, m) {
  if (!c) throw new Error(m)
}

function latLngToWorld(lat, lng, zoom) {
  const scale = TILE * 2 ** zoom
  const x = ((lng + 180) / 360) * scale
  const latC = Math.max(-85.05112878, Math.min(85.05112878, lat))
  const sinLat = Math.sin((latC * PI) / 180)
  const y = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * scale
  return { x, y }
}

function tilesForBounds(bounds, zoom) {
  const z = zoom
  const n = 1 << z
  const nw = latLngToWorld(bounds.maxLat, bounds.minLng, z)
  const se = latLngToWorld(bounds.minLat, bounds.maxLng, z)
  const x0 = Math.max(0, Math.min(n - 1, Math.floor(nw.x / TILE)))
  const x1 = Math.max(0, Math.min(n - 1, Math.floor((se.x - 1e-9) / TILE)))
  const y0 = Math.max(0, Math.min(n - 1, Math.floor(nw.y / TILE)))
  const y1 = Math.max(0, Math.min(n - 1, Math.floor((se.y - 1e-9) / TILE)))
  const out = []
  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) out.push({ z, x, y })
  }
  return out
}

function tilesForBoundsRange(bounds, zMin, zMax, maxTiles = 2500) {
  const out = []
  for (let z = zMin; z <= zMax; z++) {
    const batch = tilesForBounds(bounds, z)
    if (out.length + batch.length > maxTiles) {
      out.push(...batch.slice(0, maxTiles - out.length))
      break
    }
    out.push(...batch)
  }
  return out
}

function around(lat, lng, radiusKm = 4) {
  const dLat = radiusKm / 111
  const cosLat = Math.max(0.2, Math.cos((lat * PI) / 180))
  const dLng = radiusKm / (111 * cosLat)
  return {
    minLat: lat - dLat,
    maxLat: lat + dLat,
    minLng: lng - dLng,
    maxLng: lng + dLng,
  }
}

const caracas = around(10.496, -66.898, 4)
const keys = tilesForBoundsRange(caracas, 12, 14, 2000)
assert(keys.length >= 10, `too few ${keys.length}`)
assert(keys.length <= 2000, `too many ${keys.length}`)
assert(keys.every((k) => k.z >= 12 && k.z <= 14), 'zoom range')

const tiny = tilesForBoundsRange(around(10.496, -66.898, 0.8), 14, 15, 500)
assert(tiny.length >= 1 && tiny.length <= 200, `tiny ${tiny.length}`)

const capped = tilesForBoundsRange(around(10.496, -66.898, 12), 12, 16, 100)
assert(capped.length === 100, `cap ${capped.length}`)

console.log('OK prefetch math · Caracas 12–14 ·', keys.length, 'tiles · z14–15 box', tiny.length)

// Optional: fetch 1 tile from planned set (polite)
const sample = keys[Math.floor(keys.length / 2)]
const url = `https://tile.openstreetmap.org/${sample.z}/${sample.x}/${sample.y}.png`
try {
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'VePlayer/0.37-smoke (vescreenflow.com; +https://vescreenflow.com)',
      Accept: 'image/png',
    },
  })
  assert(res.ok, `HTTP ${res.status}`)
  const buf = Buffer.from(await res.arrayBuffer())
  assert(buf.length > 200 && buf[0] === 0x89, 'png')
  console.log('OK osm fetch', `${sample.z}/${sample.x}/${sample.y}`, buf.length, 'B')
} catch (e) {
  console.log('OSM fetch skip:', e.message)
}

// Fleet cmd accepted
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
const deviceId = `tiles-smoke-${Date.now().toString(36)}`
try {
  await fetch(BASE + '/api/fleet/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Tiles smoke',
      app_version: '0.37.0',
      version_code: 39,
    }),
  })
  const cmd = await fetch(BASE + '/api/fleet/command', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-fleet-token': 'fleet-dispatcher-demo',
    },
    body: JSON.stringify({
      device_id: deviceId,
      command: 'prefetch_tiles',
      payload: { mode: 'around', radius_km: 3 },
    }),
  })
  const body = await cmd.json()
  assert(cmd.ok, `cmd ${cmd.status} ${JSON.stringify(body)}`)
  console.log('OK prefetch_tiles cmd queued')
} catch (e) {
  console.log('SenseFlow cmd skip:', e.message)
}

console.log('OK osm-prefetch-smoke')
