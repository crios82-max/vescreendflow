import { Router } from 'express'
import { z } from 'zod'
import { db, type Activity } from './db.js'
import { encodeGeohash, decodeGeohashCenter } from './geohash.js'

export const apiRouter = Router()

const WINDOW_SEC = Number(process.env.SENSEFLOW_WINDOW_SEC || 15 * 60)
const MIN_DEVICES = Number(process.env.SENSEFLOW_MIN_DEVICES || 1)

const pingSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  accuracy_m: z.number().nonnegative().optional(),
  speed_mps: z.number().optional(),
  activity: z.enum(['IN_VEHICLE', 'ON_FOOT', 'STILL', 'UNKNOWN']),
  device_bucket: z.string().min(8).max(64),
  ts: z.number().int().positive().optional(),
})

const batchSchema = z.object({
  pings: z.array(pingSchema).min(1).max(100),
})

apiRouter.post('/pings', (req, res) => {
  const parsed = batchSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }

  const insert = db.prepare(`
    INSERT INTO pings (lat, lng, accuracy_m, speed_mps, activity, geohash, device_bucket, ts)
    VALUES (@lat, @lng, @accuracy_m, @speed_mps, @activity, @geohash, @device_bucket, @ts)
  `)

  const now = Math.floor(Date.now() / 1000)
  const tx = db.transaction((pings: z.infer<typeof batchSchema>['pings']) => {
    let n = 0
    for (const p of pings) {
      const ts = p.ts ?? now
      insert.run({
        lat: p.lat,
        lng: p.lng,
        accuracy_m: p.accuracy_m ?? null,
        speed_mps: p.speed_mps ?? null,
        activity: p.activity,
        geohash: encodeGeohash(p.lat, p.lng, 7),
        device_bucket: p.device_bucket,
        ts,
      })
      n++
    }
    return n
  })

  const inserted = tx(parsed.data.pings)
  res.status(201).json({ ok: true, inserted })
})

type Bbox = { minLat: number; minLng: number; maxLat: number; maxLng: number }

function parseBbox(raw: unknown): Bbox | null {
  if (typeof raw !== 'string') return null
  const parts = raw.split(',').map(Number)
  if (parts.length !== 4 || parts.some((n) => Number.isNaN(n))) return null
  const [minLng, minLat, maxLng, maxLat] = parts
  return { minLat, minLng, maxLat, maxLng }
}

function sinceTs(windowSec = WINDOW_SEC): number {
  return Math.floor(Date.now() / 1000) - windowSec
}

/** Traffic layer: avg speed by geohash for IN_VEHICLE pings. */
apiRouter.get('/traffic', (req, res) => {
  const bbox = parseBbox(req.query.bbox)
  const since = sinceTs()

  let rows: Array<{
    geohash: string
    avg_speed: number
    samples: number
    devices: number
  }>

  if (bbox) {
    rows = db
      .prepare(
        `
      SELECT geohash,
             AVG(COALESCE(speed_mps, 0)) AS avg_speed,
             COUNT(*) AS samples,
             COUNT(DISTINCT device_bucket) AS devices
      FROM pings
      WHERE activity = 'IN_VEHICLE'
        AND ts >= ?
        AND lat BETWEEN ? AND ?
        AND lng BETWEEN ? AND ?
      GROUP BY geohash
      HAVING devices >= ?
    `,
      )
      .all(since, bbox.minLat, bbox.maxLat, bbox.minLng, bbox.maxLng, MIN_DEVICES) as typeof rows
  } else {
    rows = db
      .prepare(
        `
      SELECT geohash,
             AVG(COALESCE(speed_mps, 0)) AS avg_speed,
             COUNT(*) AS samples,
             COUNT(DISTINCT device_bucket) AS devices
      FROM pings
      WHERE activity = 'IN_VEHICLE' AND ts >= ?
      GROUP BY geohash
      HAVING devices >= ?
    `,
      )
      .all(since, MIN_DEVICES) as typeof rows
  }

  const features = rows.map((r) => {
    const { lat, lng } = decodeGeohashCenter(r.geohash)
    const kmh = r.avg_speed * 3.6
    const level = kmh >= 50 ? 'free' : kmh >= 25 ? 'moderate' : 'slow'
    return {
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [lng, lat] },
      properties: {
        geohash: r.geohash,
        avg_speed_mps: r.avg_speed,
        avg_speed_kmh: Math.round(kmh * 10) / 10,
        level,
        samples: r.samples,
        devices: r.devices,
      },
    }
  })

  res.json({
    type: 'FeatureCollection',
    features,
    meta: { since, window_sec: WINDOW_SEC, kind: 'traffic' },
  })
})

/** Crowd layer: unique device buckets for ON_FOOT / STILL. */
apiRouter.get('/crowd', (req, res) => {
  const bbox = parseBbox(req.query.bbox)
  const since = sinceTs()

  let rows: Array<{
    geohash: string
    devices: number
    samples: number
  }>

  if (bbox) {
    rows = db
      .prepare(
        `
      SELECT geohash,
             COUNT(DISTINCT device_bucket) AS devices,
             COUNT(*) AS samples
      FROM pings
      WHERE activity IN ('ON_FOOT', 'STILL')
        AND ts >= ?
        AND lat BETWEEN ? AND ?
        AND lng BETWEEN ? AND ?
      GROUP BY geohash
      HAVING devices >= ?
    `,
      )
      .all(since, bbox.minLat, bbox.maxLat, bbox.minLng, bbox.maxLng, MIN_DEVICES) as typeof rows
  } else {
    rows = db
      .prepare(
        `
      SELECT geohash,
             COUNT(DISTINCT device_bucket) AS devices,
             COUNT(*) AS samples
      FROM pings
      WHERE activity IN ('ON_FOOT', 'STILL') AND ts >= ?
      GROUP BY geohash
      HAVING devices >= ?
    `,
      )
      .all(since, MIN_DEVICES) as typeof rows
  }

  const maxDevices = rows.reduce((m, r) => Math.max(m, r.devices), 1)

  const features = rows.map((r) => {
    const { lat, lng } = decodeGeohashCenter(r.geohash)
    const intensity = r.devices / maxDevices
    return {
      type: 'Feature' as const,
      geometry: { type: 'Point' as const, coordinates: [lng, lat] },
      properties: {
        geohash: r.geohash,
        devices: r.devices,
        samples: r.samples,
        intensity: Math.round(intensity * 100) / 100,
      },
    }
  })

  res.json({
    type: 'FeatureCollection',
    features,
    meta: { since, window_sec: WINDOW_SEC, kind: 'crowd' },
  })
})

apiRouter.get('/stats', (_req, res) => {
  const since = sinceTs()
  const total = db.prepare(`SELECT COUNT(*) AS n FROM pings WHERE ts >= ?`).get(since) as {
    n: number
  }
  const byActivity = db
    .prepare(
      `
    SELECT activity, COUNT(*) AS n, COUNT(DISTINCT device_bucket) AS devices
    FROM pings WHERE ts >= ?
    GROUP BY activity
  `,
    )
    .all(since) as Array<{ activity: Activity; n: number; devices: number }>

  res.json({
    window_sec: WINDOW_SEC,
    pings: total.n,
    by_activity: byActivity,
  })
})

/** Actors around ego for Tesla-like left viz (meters: +y ahead, +x right). */
apiRouter.get('/surround', (req, res) => {
  const lat = Number(req.query.lat)
  const lng = Number(req.query.lng)
  const radiusM = Math.min(300, Math.max(40, Number(req.query.radius_m) || 120))
  if (Number.isNaN(lat) || Number.isNaN(lng)) {
    res.status(400).json({ error: 'lat/lng requeridos' })
    return
  }
  const since = sinceTs(Math.min(WINDOW_SEC, 5 * 60))
  // ~1° lat ≈ 111km; lng scales with cos(lat)
  const dLat = radiusM / 111_320
  const dLng = radiusM / (111_320 * Math.cos((lat * Math.PI) / 180))

  const rows = db
    .prepare(
      `
    SELECT lat, lng, speed_mps, activity, device_bucket, ts
    FROM pings
    WHERE ts >= ?
      AND lat BETWEEN ? AND ?
      AND lng BETWEEN ? AND ?
    ORDER BY ts DESC
    LIMIT 400
  `,
    )
    .all(since, lat - dLat, lat + dLat, lng - dLng, lng + dLng) as Array<{
    lat: number
    lng: number
    speed_mps: number | null
    activity: Activity
    device_bucket: string
    ts: number
  }>

  // Keep latest ping per device
  const latest = new Map<string, (typeof rows)[0]>()
  for (const r of rows) {
    if (!latest.has(r.device_bucket)) latest.set(r.device_bucket, r)
  }

  const actors = [...latest.values()]
    .map((r) => {
      const { x, y } = toLocalMeters(lat, lng, r.lat, r.lng)
      const dist = Math.hypot(x, y)
      if (dist < 2 || dist > radiusM) return null
      const speed = r.speed_mps ?? 0
      const kind =
        r.activity === 'ON_FOOT' || r.activity === 'STILL'
          ? 'person'
          : speed >= 8 && speed < 18
            ? 'motorcycle'
            : speed >= 18
              ? 'car'
              : r.activity === 'IN_VEHICLE'
                ? 'car'
                : 'unknown'
      return {
        id: r.device_bucket.slice(0, 12),
        kind,
        x_m: Math.round(x * 10) / 10,
        y_m: Math.round(y * 10) / 10,
        speed_mps: Math.round(speed * 10) / 10,
        source: 'senseflow',
      }
    })
    .filter(Boolean)

  res.json({
    ego: { lat, lng },
    radius_m: radiusM,
    actors,
  })
})

function toLocalMeters(
  egoLat: number,
  egoLng: number,
  lat: number,
  lng: number,
): { x: number; y: number } {
  const mPerDegLat = 111_320
  const mPerDegLng = 111_320 * Math.cos((egoLat * Math.PI) / 180)
  const y = (lat - egoLat) * mPerDegLat // north ≈ ahead if heading north; UI treats +y as ahead
  const x = (lng - egoLng) * mPerDegLng // east ≈ right
  return { x, y }
}
