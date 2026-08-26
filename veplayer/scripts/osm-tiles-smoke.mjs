#!/usr/bin/env node
/**
 * Web Mercator + OSM tile covering smoke (VePlayer 0.19).
 * Mirrors Android WebMercator.kt math; optionally fetches one OSM tile.
 */

const TILE = 256
const PI = Math.PI

function latLngToWorld(lat, lng, zoom) {
  const scale = TILE * 2 ** zoom
  const x = ((lng + 180) / 360) * scale
  const latC = Math.max(-85.05112878, Math.min(85.05112878, lat))
  const sinLat = Math.sin((latC * PI) / 180)
  const y = (0.5 - Math.log((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * scale
  return { x, y }
}

function zoomForBounds(bounds, width, height, pad = 24) {
  const availW = Math.max(64, width - pad * 2)
  const availH = Math.max(64, height - pad * 2)
  for (let z = 18; z >= 2; z--) {
    const nw = latLngToWorld(bounds.maxLat, bounds.minLng, z)
    const se = latLngToWorld(bounds.minLat, bounds.maxLng, z)
    const bw = Math.max(1, se.x - nw.x)
    const bh = Math.max(1, se.y - nw.y)
    if (bw <= availW && bh <= availH) return z
  }
  return 2
}

function viewportFor(bounds, width, height, pad = 24) {
  const zoom = zoomForBounds(bounds, width, height, pad)
  const nw = latLngToWorld(bounds.maxLat, bounds.minLng, zoom)
  const se = latLngToWorld(bounds.minLat, bounds.maxLng, zoom)
  const bw = Math.max(1, se.x - nw.x)
  const bh = Math.max(1, se.y - nw.y)
  const availW = Math.max(1, width - pad * 2)
  const availH = Math.max(1, height - pad * 2)
  const scale = Math.min(availW / bw, availH / bh)
  const usedW = bw * scale
  const usedH = bh * scale
  const originWorldX = nw.x - (availW - usedW) / (2 * scale) - pad / scale
  const originWorldY = nw.y - (availH - usedH) / (2 * scale) - pad / scale
  return { zoom, originWorldX, originWorldY, scale }
}

function project(lat, lng, vp) {
  const w = latLngToWorld(lat, lng, vp.zoom)
  return {
    x: (w.x - vp.originWorldX) * vp.scale,
    y: (w.y - vp.originWorldY) * vp.scale,
  }
}

function tilesCovering(vp, width, height) {
  const n = 1 << vp.zoom
  const left = vp.originWorldX
  const top = vp.originWorldY
  const right = left + width / vp.scale
  const bottom = top + height / vp.scale
  const x0 = Math.max(0, Math.min(n - 1, Math.floor(left / TILE)))
  const x1 = Math.max(0, Math.min(n - 1, Math.floor((right - 1e-6) / TILE)))
  const y0 = Math.max(0, Math.min(n - 1, Math.floor(top / TILE)))
  const y1 = Math.max(0, Math.min(n - 1, Math.floor((bottom - 1e-6) / TILE)))
  const out = []
  for (let x = x0; x <= x1; x++) {
    for (let y = y0; y <= y1; y++) out.push({ z: vp.zoom, x, y })
  }
  return out
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

// Caracas demo box (Altamira ↔ Bellas Artes-ish)
const bounds = { minLat: 10.49, maxLat: 10.51, minLng: -66.92, maxLng: -66.84 }
const vp = viewportFor(bounds, 800, 480)
assert(vp.zoom >= 10 && vp.zoom <= 16, `zoom ${vp.zoom}`)
assert(vp.scale > 0, 'scale')

const ego = project(10.496, -66.898, vp)
const dest = project(10.4965, -66.8492, vp)
assert(ego.x < dest.x, 'east is right')
assert(ego.y > 0 && dest.y > 0, 'on canvas')

const tiles = tilesCovering(vp, 800, 480)
assert(tiles.length >= 1 && tiles.length <= 64, `tile count ${tiles.length}`)
assert(tiles.every((t) => t.z === vp.zoom), 'same zoom')

console.log(
  'OK mercator · z',
  vp.zoom,
  '· tiles',
  tiles.length,
  '· scale',
  vp.scale.toFixed(3),
  '· ego',
  `${ego.x.toFixed(0)},${ego.y.toFixed(0)}`,
)

const sample = tiles[Math.floor(tiles.length / 2)]
const url = `https://tile.openstreetmap.org/${sample.z}/${sample.x}/${sample.y}.png`
try {
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'VePlayer/0.19-smoke (vescreenflow.com; +https://vescreenflow.com)',
      Accept: 'image/png',
    },
  })
  assert(res.ok, `HTTP ${res.status}`)
  const buf = Buffer.from(await res.arrayBuffer())
  assert(buf.length > 200, 'png bytes')
  assert(buf[0] === 0x89 && buf[1] === 0x50, 'PNG magic')
  console.log('OK osm tile fetch', `${sample.z}/${sample.x}/${sample.y}`, buf.length, 'B')
} catch (e) {
  console.log('OSM fetch skip:', e.message)
}

console.log('OK osm-tiles-smoke')
