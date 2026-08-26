#!/usr/bin/env node
/**
 * Crowd-on-map offset math smoke (VePlayer 0.27).
 * Mirrors GeoProjection.offsetToLatLng
 */

function offsetToLatLng(ego, headingDeg, xM, yM) {
  const h = (headingDeg * Math.PI) / 180
  const cosH = Math.cos(h)
  const sinH = Math.sin(h)
  const northM = yM * cosH - xM * sinH
  const eastM = yM * sinH + xM * cosH
  const dLat = northM / 111320
  const cosLat = Math.max(1e-6, Math.cos((ego.lat * Math.PI) / 180))
  const dLng = eastM / (111320 * cosLat)
  return { lat: ego.lat + dLat, lng: ego.lng + dLng }
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

const ego = { lat: 10.496, lng: -66.898 }

// Heading north: ahead 111.32m ≈ 0.001° lat
const n = offsetToLatLng(ego, 0, 0, 111.32)
assert(Math.abs(n.lat - (ego.lat + 0.001)) < 1e-6, `north ${n.lat}`)
assert(Math.abs(n.lng - ego.lng) < 1e-7, 'north no lng')

// Heading north: right 50m → east → lng+
const e = offsetToLatLng(ego, 0, 50, 0)
assert(e.lng > ego.lng, 'right → east')
assert(Math.abs(e.lat - ego.lat) < 1e-7, 'right no lat')

// Heading east (90°): ahead 100m → east
const eastHead = offsetToLatLng(ego, 90, 0, 100)
assert(eastHead.lng > ego.lng, 'heading E ahead')
assert(Math.abs(eastHead.lat - ego.lat) < 2e-5, 'heading E lat~0')

console.log('OK crowd-map · Δlat', (n.lat - ego.lat).toFixed(6), '· Δlng right', (e.lng - ego.lng).toFixed(6))
console.log('OK crowd-map-smoke')
