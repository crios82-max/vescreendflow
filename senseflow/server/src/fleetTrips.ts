import { Router } from 'express'
import { z } from 'zod'
import { db } from './db.js'
import { requireRole } from './fleetOps.js'

export type FleetShift = {
  id: number
  device_id: string
  driver_id: number | null
  driver_code: string | null
  driver_name: string | null
  started_at: number
  ended_at: number | null
  start_odo_km: number | null
  end_odo_km: number | null
  distance_km: number
  start_lat: number | null
  start_lng: number | null
  end_lat: number | null
  end_lng: number | null
  status: string
  idle_sec: number
  overspeed_sec: number
  abs_events: number
  high_throttle_sec: number
  eco_score: number | null
  eco_band: string | null
}

/** Pure eco score — mirrors VePlayer EcoScore.kt */
export function evaluateEco(input: {
  idle_sec: number
  overspeed_sec: number
  abs_events: number
  high_throttle_sec: number
  distance_km: number
}): { score: number; band: string; penalties: Record<string, number> } {
  const idlePen = Math.min(30, Math.floor(input.idle_sec / 60) * 2)
  const overPen = Math.min(40, Math.floor(input.overspeed_sec / 8))
  const absPen = Math.min(20, input.abs_events * 5)
  const thrPen = Math.min(20, Math.floor(input.high_throttle_sec / 15))
  const score = Math.max(0, Math.min(100, 100 - idlePen - overPen - absPen - thrPen))
  const band = score >= 80 ? 'good' : score >= 55 ? 'fair' : 'poor'
  return {
    score,
    band,
    penalties: { idle: idlePen, overspeed: overPen, abs: absPen, throttle: thrPen },
  }
}

export function ensureFleetShiftsTables() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS fleet_shifts (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT NOT NULL,
      driver_id INTEGER,
      started_at INTEGER NOT NULL,
      ended_at INTEGER,
      start_odo_km REAL,
      end_odo_km REAL,
      distance_km REAL NOT NULL DEFAULT 0,
      start_lat REAL,
      start_lng REAL,
      end_lat REAL,
      end_lng REAL,
      status TEXT NOT NULL DEFAULT 'open',
      FOREIGN KEY (device_id) REFERENCES fleet_devices(device_id)
    );
    CREATE INDEX IF NOT EXISTS idx_fleet_shifts_dev ON fleet_shifts(device_id, status);
    CREATE INDEX IF NOT EXISTS idx_fleet_shifts_drv ON fleet_shifts(driver_id, started_at DESC);
  `)
  for (const col of [
    'ALTER TABLE fleet_shifts ADD COLUMN idle_sec REAL NOT NULL DEFAULT 0',
    'ALTER TABLE fleet_shifts ADD COLUMN overspeed_sec REAL NOT NULL DEFAULT 0',
    'ALTER TABLE fleet_shifts ADD COLUMN abs_events INTEGER NOT NULL DEFAULT 0',
    'ALTER TABLE fleet_shifts ADD COLUMN high_throttle_sec REAL NOT NULL DEFAULT 0',
    'ALTER TABLE fleet_shifts ADD COLUMN eco_score REAL',
    'ALTER TABLE fleet_shifts ADD COLUMN eco_band TEXT',
  ]) {
    try {
      db.exec(col)
    } catch {
      /* exists */
    }
  }
}

function rowToShift(r: Record<string, unknown>): FleetShift {
  const idle = Number(r.idle_sec ?? 0)
  const over = Number(r.overspeed_sec ?? 0)
  const abs = Number(r.abs_events ?? 0)
  const thr = Number(r.high_throttle_sec ?? 0)
  const dist = Number(r.distance_km ?? 0)
  const eco =
    r.eco_score != null
      ? { score: Number(r.eco_score), band: String(r.eco_band || 'fair') }
      : evaluateEco({
          idle_sec: idle,
          overspeed_sec: over,
          abs_events: abs,
          high_throttle_sec: thr,
          distance_km: dist,
        })
  return {
    id: Number(r.id),
    device_id: String(r.device_id),
    driver_id: r.driver_id != null ? Number(r.driver_id) : null,
    driver_code: (r.driver_code as string) || null,
    driver_name: (r.driver_name as string) || null,
    started_at: Number(r.started_at),
    ended_at: r.ended_at != null ? Number(r.ended_at) : null,
    start_odo_km: r.start_odo_km != null ? Number(r.start_odo_km) : null,
    end_odo_km: r.end_odo_km != null ? Number(r.end_odo_km) : null,
    distance_km: dist,
    start_lat: r.start_lat != null ? Number(r.start_lat) : null,
    start_lng: r.start_lng != null ? Number(r.start_lng) : null,
    end_lat: r.end_lat != null ? Number(r.end_lat) : null,
    end_lng: r.end_lng != null ? Number(r.end_lng) : null,
    status: String(r.status),
    idle_sec: idle,
    overspeed_sec: over,
    abs_events: abs,
    high_throttle_sec: thr,
    eco_score: eco.score,
    eco_band: eco.band,
  }
}

const SHIFT_SELECT = `
  SELECT s.*, d.code AS driver_code, d.name AS driver_name
  FROM fleet_shifts s
  LEFT JOIN fleet_drivers d ON d.id = s.driver_id
`

export function openShiftForDevice(deviceId: string): FleetShift | null {
  const row = db
    .prepare(`${SHIFT_SELECT} WHERE s.device_id = ? AND s.status = 'open' ORDER BY s.id DESC LIMIT 1`)
    .get(deviceId) as Record<string, unknown> | undefined
  return row ? rowToShift(row) : null
}

export function startShift(input: {
  deviceId: string
  driverId?: number | null
  odoKm?: number | null
  lat?: number | null
  lng?: number | null
}): FleetShift {
  const existing = openShiftForDevice(input.deviceId)
  if (existing) {
    if (input.driverId == null || existing.driver_id === input.driverId) {
      return existing
    }
    endShift({
      deviceId: input.deviceId,
      odoKm: input.odoKm,
      lat: input.lat,
      lng: input.lng,
    })
  }
  const now = Math.floor(Date.now() / 1000)
  const driverId =
    input.driverId ??
    (
      db.prepare(`SELECT driver_id FROM fleet_devices WHERE device_id = ?`).get(input.deviceId) as
        | { driver_id: number | null }
        | undefined
    )?.driver_id ??
    null
  const info = db
    .prepare(
      `INSERT INTO fleet_shifts
       (device_id, driver_id, started_at, start_odo_km, start_lat, start_lng, status, distance_km,
        idle_sec, overspeed_sec, abs_events, high_throttle_sec, eco_score, eco_band)
       VALUES (?, ?, ?, ?, ?, ?, 'open', 0, 0, 0, 0, 0, 100, 'good')`,
    )
    .run(
      input.deviceId,
      driverId,
      now,
      input.odoKm ?? null,
      input.lat ?? null,
      input.lng ?? null,
    )
  return (
    openShiftForDevice(input.deviceId) ??
    rowToShift({
      id: Number(info.lastInsertRowid),
      device_id: input.deviceId,
      driver_id: driverId,
      started_at: now,
      status: 'open',
      distance_km: 0,
      idle_sec: 0,
      overspeed_sec: 0,
      abs_events: 0,
      high_throttle_sec: 0,
      eco_score: 100,
      eco_band: 'good',
    })
  )
}

export function endShift(input: {
  deviceId: string
  odoKm?: number | null
  lat?: number | null
  lng?: number | null
  distanceKm?: number | null
}): FleetShift | null {
  const open = openShiftForDevice(input.deviceId)
  if (!open) return null
  const now = Math.floor(Date.now() / 1000)
  let distance = input.distanceKm
  if (distance == null && open.start_odo_km != null && input.odoKm != null) {
    distance = Math.max(0, input.odoKm - open.start_odo_km)
  }
  if (distance == null) distance = open.distance_km
  const eco = evaluateEco({
    idle_sec: open.idle_sec,
    overspeed_sec: open.overspeed_sec,
    abs_events: open.abs_events,
    high_throttle_sec: open.high_throttle_sec,
    distance_km: distance,
  })
  db.prepare(
    `UPDATE fleet_shifts SET
       status = 'closed',
       ended_at = ?,
       end_odo_km = ?,
       end_lat = ?,
       end_lng = ?,
       distance_km = ?,
       eco_score = ?,
       eco_band = ?
     WHERE id = ?`,
  ).run(
    now,
    input.odoKm ?? null,
    input.lat ?? null,
    input.lng ?? null,
    distance,
    eco.score,
    eco.band,
    open.id,
  )
  const row = db.prepare(`${SHIFT_SELECT} WHERE s.id = ?`).get(open.id) as Record<string, unknown>
  return rowToShift(row)
}

/**
 * Heartbeat: bump distance + eco accumulators from vehicle_signals.
 * @param dtSec sample interval estimate (default ~25s heartbeat)
 */
export function touchShift(input: {
  deviceId: string
  odoKm?: number | null
  deltaKm?: number | null
  lat?: number | null
  lng?: number | null
  signals?: Record<string, unknown> | null
  dtSec?: number
}) {
  const open = openShiftForDevice(input.deviceId)
  if (!open) return null
  let distance = open.distance_km
  let startOdo = open.start_odo_km
  if (startOdo == null && input.odoKm != null) {
    db.prepare(`UPDATE fleet_shifts SET start_odo_km = ? WHERE id = ?`).run(input.odoKm, open.id)
    startOdo = input.odoKm
  }
  if (startOdo != null && input.odoKm != null) {
    distance = Math.max(distance, input.odoKm - startOdo)
  } else if (input.deltaKm != null && input.deltaKm > 0) {
    distance += input.deltaKm
  }

  const dt = Math.max(1, Math.min(120, input.dtSec ?? 25))
  let idle = open.idle_sec
  let over = open.overspeed_sec
  let absN = open.abs_events
  let thr = open.high_throttle_sec
  const sig = input.signals
  if (sig) {
    const speedMps = typeof sig.speed_mps === 'number' ? sig.speed_mps : null
    const speedKmh = speedMps != null ? speedMps * 3.6 : typeof sig.speed_kmh === 'number' ? sig.speed_kmh : null
    const idleSecSample = typeof sig.idle_sec === 'number' ? sig.idle_sec : null
    const ignition = typeof sig.ignition === 'string' ? sig.ignition : ''
    const ignOn = ignition === 'on' || ignition === 'acc' || ignition === 'start' || ignition === ''
    if (idleSecSample != null && idleSecSample > 0) {
      // device reports cumulative idle for current stop — take max growth approx via dt when >0
      idle += dt
    } else if (speedKmh != null && speedKmh < 3 && ignOn) {
      idle += dt
    }
    const limit = typeof sig.speed_limit_kmh === 'number' ? sig.speed_limit_kmh : 50
    if (speedKmh != null && speedKmh > limit + 5) {
      over += dt
    }
    if (sig.abs_active === true) absN += 1
    const throttle = typeof sig.throttle_pct === 'number' ? sig.throttle_pct : null
    if (throttle != null && throttle > 80 && speedKmh != null && speedKmh > 20) {
      thr += dt
    }
  }

  const eco = evaluateEco({
    idle_sec: idle,
    overspeed_sec: over,
    abs_events: absN,
    high_throttle_sec: thr,
    distance_km: distance,
  })

  db.prepare(
    `UPDATE fleet_shifts SET
       distance_km = ?,
       end_lat = COALESCE(?, end_lat),
       end_lng = COALESCE(?, end_lng),
       idle_sec = ?,
       overspeed_sec = ?,
       abs_events = ?,
       high_throttle_sec = ?,
       eco_score = ?,
       eco_band = ?
     WHERE id = ?`,
  ).run(
    distance,
    input.lat ?? null,
    input.lng ?? null,
    idle,
    over,
    absN,
    thr,
    eco.score,
    eco.band,
    open.id,
  )
  return openShiftForDevice(input.deviceId)
}

export const fleetShiftsRouter = Router()

const startSchema = z.object({
  device_id: z.string().min(8).max(64),
  driver_id: z.number().int().positive().optional(),
  odo_km: z.number().nonnegative().optional(),
  lat: z.number().optional(),
  lng: z.number().optional(),
})

fleetShiftsRouter.post('/start', (req, res) => {
  const parsed = startSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido' })
    return
  }
  const device = db
    .prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`)
    .get(parsed.data.device_id)
  if (!device) {
    res.status(404).json({ error: 'dispositivo no registrado' })
    return
  }
  const shift = startShift({
    deviceId: parsed.data.device_id,
    driverId: parsed.data.driver_id,
    odoKm: parsed.data.odo_km,
    lat: parsed.data.lat,
    lng: parsed.data.lng,
  })
  res.status(201).json({ ok: true, shift })
})

const endSchema = z.object({
  device_id: z.string().min(8).max(64),
  odo_km: z.number().nonnegative().optional(),
  lat: z.number().optional(),
  lng: z.number().optional(),
  distance_km: z.number().nonnegative().optional(),
})

fleetShiftsRouter.post('/end', (req, res) => {
  const parsed = endSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido' })
    return
  }
  const shift = endShift({
    deviceId: parsed.data.device_id,
    odoKm: parsed.data.odo_km,
    lat: parsed.data.lat,
    lng: parsed.data.lng,
    distanceKm: parsed.data.distance_km,
  })
  if (!shift) {
    res.status(404).json({ error: 'sin turno abierto' })
    return
  }
  res.json({ ok: true, shift })
})

fleetShiftsRouter.get('/current', (req, res) => {
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : ''
  if (deviceId.length < 8) {
    res.status(400).json({ error: 'device_id requerido' })
    return
  }
  res.json({ shift: openShiftForDevice(deviceId) })
})

fleetShiftsRouter.get('/', (req, res) => {
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : null
  const driverId = req.query.driver_id != null ? Number(req.query.driver_id) : null
  const limit = Math.min(200, Math.max(1, Number(req.query.limit) || 50))
  let sql = `${SHIFT_SELECT} WHERE 1=1`
  const params: unknown[] = []
  if (deviceId) {
    sql += ` AND s.device_id = ?`
    params.push(deviceId)
  }
  if (driverId && Number.isFinite(driverId)) {
    sql += ` AND s.driver_id = ?`
    params.push(driverId)
  }
  sql += ` ORDER BY s.id DESC LIMIT ?`
  params.push(limit)
  const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
  res.json({ shifts: rows.map(rowToShift) })
})

export function mountShiftOpsRoutes(ops: Router) {
  ops.get('/shifts', requireRole('viewer'), (req, res) => {
    const limit = Math.min(500, Math.max(1, Number(req.query.limit) || 100))
    const rows = db
      .prepare(`${SHIFT_SELECT} ORDER BY s.id DESC LIMIT ?`)
      .all(limit) as Array<Record<string, unknown>>
    res.json({ shifts: rows.map(rowToShift) })
  })
}
