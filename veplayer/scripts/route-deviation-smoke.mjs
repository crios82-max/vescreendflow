#!/usr/bin/env node
/**
 * Route deviation smoke (VePlayer 0.57 · Fase 10).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

/** Local equirectangular distance to segment (m) — mirrors GeoProjection. */
function distanceToSegmentM(p, a, b) {
  const cosLat = Math.max(1e-6, Math.cos((a.lat * Math.PI) / 180))
  const toXy = (ll) => [
    (ll.lng - a.lng) * 111320 * cosLat,
    (ll.lat - a.lat) * 111320,
  ]
  const [px, py] = toXy(p)
  const [bx, by] = toXy(b)
  const len2 = bx * bx + by * by
  if (len2 < 1e-4) {
    const dx = (p.lng - a.lng) * 111320 * cosLat
    const dy = (p.lat - a.lat) * 111320
    return Math.sqrt(dx * dx + dy * dy)
  }
  let t = (px * bx + py * by) / len2
  t = Math.max(0, Math.min(1, t))
  const dx = px - t * bx
  const dy = py - t * by
  return Math.sqrt(dx * dx + dy * dy)
}

function distanceToRouteM(path, ego) {
  if (!path.length) return Infinity
  if (path.length === 1) return distanceToSegmentM(ego, path[0], path[0])
  let best = Infinity
  for (let i = 0; i < path.length - 1; i++) {
    best = Math.min(best, distanceToSegmentM(ego, path[i], path[i + 1]))
  }
  return best
}

function evaluate(distanceM, offRouteSec = 0, hasRoute = true, warnM = 80, alertM = 150, holdSec = 8) {
  if (!hasRoute) return { band: 'idle', showWarn: false, label: '', hasRoute: false }
  const dist = Math.max(0, distanceM)
  const warn = Math.max(20, Math.min(500, warnM))
  const alert = Math.max(warn + 10, alertM)
  const hold = Math.max(0, Math.min(120, holdSec))
  let band = 'ok'
  if (dist >= alert) band = 'alert'
  else if (dist >= warn) band = 'warn'
  const showWarn = (band === 'warn' || band === 'alert') && offRouteSec >= hold
  const label =
    band === 'alert'
      ? `Fuera ruta · ${Math.trunc(dist)} m`
      : band === 'warn'
        ? `Desvío · ${Math.trunc(dist)} m`
        : dist > 5
          ? `En ruta · ${Math.trunc(dist)} m`
          : 'En ruta'
  return { band, showWarn, label, distanceM: dist, hasRoute: true }
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
  console.log('route-deviation-smoke →', BASE)

  const path = [
    { lat: 10.5, lng: -66.9 },
    { lat: 10.5, lng: -66.89 },
    { lat: 10.51, lng: -66.89 },
  ]
  const onRoute = distanceToRouteM(path, { lat: 10.5, lng: -66.895 })
  assert(onRoute < 20, `on-route ${onRoute}`)
  const off = distanceToRouteM(path, { lat: 10.5, lng: -66.88 })
  assert(off > 80, `off-route ${off}`)

  assert(evaluate(10, 20).band === 'ok' && !evaluate(10, 20).showWarn, 'ok')
  assert(evaluate(90, 3).band === 'warn' && !evaluate(90, 3).showWarn, 'warn hold')
  assert(evaluate(90, 10).band === 'warn' && evaluate(90, 10).showWarn, 'warn held')
  assert(evaluate(160, 10).band === 'alert' && evaluate(160, 10).showWarn, 'alert')

  const deviceId = `route-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Route deviation smoke',
      app_version: '0.57.0',
      version_code: 59,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.57.0',
      version_code: 59,
      vehicle_signals: {
        route_off_m: 95,
        route_warn_m: 80,
        route_alert_m: 150,
        route_dev: {
          band: 'warn',
          distance_m: 95,
          off_route_sec: 12,
          show_warn: true,
          has_route: true,
        },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('route_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.57.0',
      version_code: 59,
      vehicle_signals: {
        route_off_m: 180,
        route_warn_m: 80,
        route_alert_m: 150,
        route_dev: {
          band: 'alert',
          distance_m: 180,
          off_route_sec: 20,
          show_warn: true,
          has_route: true,
        },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('route_deviate'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK route-deviation-smoke · warn+deviate')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
