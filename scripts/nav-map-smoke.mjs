#!/usr/bin/env node
/**
 * Native map projection + SenseFlow nav route/waypoints smoke (VePlayer 0.33).
 */

function project(lat, lng, bounds, width, height, pad = 28) {
  const w = Math.max(1, width - pad * 2)
  const h = Math.max(1, height - pad * 2)
  const x = pad + ((lng - bounds.minLng) / Math.max(1e-9, bounds.maxLng - bounds.minLng)) * w
  const y = pad + ((bounds.maxLat - lat) / Math.max(1e-9, bounds.maxLat - bounds.minLat)) * h
  return { x, y }
}

function haversineM(a, b) {
  const R = 6371000
  const toR = (d) => (d * Math.PI) / 180
  const dLat = toR(b.lat - a.lat)
  const dLng = toR(b.lng - a.lng)
  const x =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toR(a.lat)) * Math.cos(toR(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(x))
}

function progressAlong(path, ego) {
  let best = Infinity
  let bestIdx = 0
  path.forEach((p, i) => {
    const d = haversineM(ego, p)
    if (d < best) {
      best = d
      bestIdx = i
    }
  })
  let total = 0
  let done = 0
  for (let i = 0; i < path.length - 1; i++) {
    const seg = haversineM(path[i], path[i + 1])
    total += seg
    if (i < bestIdx) done += seg
  }
  return total <= 0 ? 0 : done / total
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

const bounds = { minLat: 10.49, maxLat: 10.51, minLng: -66.92, maxLng: -66.84 }
const tl = project(10.51, -66.92, bounds, 800, 480)
const br = project(10.49, -66.84, bounds, 800, 480)
assert(tl.x < br.x && tl.y < br.y, 'projection orientation')

const path = [
  { lat: 10.496, lng: -66.898 },
  { lat: 10.4962, lng: -66.88 },
  { lat: 10.4965, lng: -66.8492 },
]
const mid = path[1]
const p = progressAlong(path, mid)
assert(p > 0.2 && p < 0.9, `progress ${p}`)
console.log('OK nav-map projection · progress', p.toFixed(2))

const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

async function main() {
  const dest = await fetch(BASE + '/api/nav/destinations').then((r) => r.json())
  assert(Array.isArray(dest.destinations) && dest.destinations.length >= 3, 'destinations')
  const d0 = dest.destinations[0]
  const d1 = dest.destinations[1]
  const route = await fetch(
    `${BASE}/api/nav/route?from_lat=10.496&from_lng=-66.898&to_lat=${d0.lat}&to_lng=${d0.lng}&dest_name=${encodeURIComponent(d0.name)}`,
  ).then((r) => r.json())
  assert(route.geometry?.coordinates?.length >= 2 || route.distance_m > 0, 'route')
  console.log(
    'SenseFlow route',
    route.source,
    'pts',
    route.geometry?.coordinates?.length ?? 0,
    '→',
    d0.name,
  )

  const multi = await fetch(
    `${BASE}/api/nav/route?from_lat=10.496&from_lng=-66.898` +
      `&to_lat=${d0.lat}&to_lng=${d0.lng}&dest_name=${encodeURIComponent(d0.name)}` +
      `&via=${d1.lat},${d1.lng}&via_names=${encodeURIComponent(d1.name)}`,
  ).then((r) => r.json())
  assert(multi.ok, 'multi ok')
  assert(Array.isArray(multi.waypoints) && multi.waypoints.length >= 2, `waypoints ${JSON.stringify(multi.waypoints)}`)
  assert(multi.waypoints.some((w) => w.role === 'via'), 'has via')
  assert(multi.waypoints.some((w) => w.role === 'dest'), 'has dest')
  assert(Array.isArray(multi.legs) && multi.legs.length >= 2, `legs ${multi.legs?.length}`)
  assert(multi.geometry?.coordinates?.length >= 2 || multi.distance_m > 0, 'multi geometry')
  console.log(
    'SenseFlow multi-stop',
    multi.source,
    'legs',
    multi.legs.length,
    '→',
    multi.waypoints.map((w) => w.name).join(' → '),
  )
  console.log('OK nav-map-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
