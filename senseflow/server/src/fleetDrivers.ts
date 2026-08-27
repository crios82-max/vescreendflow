import { Router } from 'express'
import { z } from 'zod'
import { db } from './db.js'
import { hashPassword, requireRole, verifyPassword } from './fleetOps.js'
import { endShift, startShift } from './fleetTrips.js'

export type FleetDriver = {
  id: number
  code: string
  name: string
  phone: string | null
  language: string
  preferred_dest: string | null
  preferred_lat: number | null
  preferred_lng: number | null
  notes: string | null
  active: number
  created_at: number
}

export function ensureFleetDriversTables() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS fleet_drivers (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      code TEXT NOT NULL UNIQUE,
      name TEXT NOT NULL,
      phone TEXT,
      pin_hash TEXT,
      language TEXT NOT NULL DEFAULT 'es',
      preferred_dest TEXT,
      preferred_lat REAL,
      preferred_lng REAL,
      notes TEXT,
      active INTEGER NOT NULL DEFAULT 1,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );
    CREATE INDEX IF NOT EXISTS idx_fleet_drivers_code ON fleet_drivers(code);
  `)

  try {
    db.exec(`ALTER TABLE fleet_devices ADD COLUMN driver_id INTEGER`)
  } catch {
    // exists
  }

  const n = db.prepare(`SELECT COUNT(*) AS n FROM fleet_drivers`).get() as { n: number }
  if (n.n === 0) {
    const ins = db.prepare(
      `INSERT INTO fleet_drivers
       (code, name, phone, pin_hash, language, preferred_dest, preferred_lat, preferred_lng, notes)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
    ins.run(
      'D001',
      'Carlos Rivas',
      '+58412…',
      hashPassword('1234'),
      'es',
      'Altamira',
      10.4965,
      -66.8492,
      'Demo conductor',
    )
    ins.run(
      'D002',
      'María López',
      null,
      hashPassword('5678'),
      'es',
      'Chacao',
      10.4958,
      -66.8756,
      null,
    )
    ins.run('D003', 'Demo Guest', null, null, 'es', null, null, null, 'Sin PIN')
  }
}

function publicDriver(row: FleetDriver & { pin_hash?: string | null }) {
  return {
    id: row.id,
    code: row.code,
    name: row.name,
    phone: row.phone,
    language: row.language,
    preferred_dest: row.preferred_dest,
    preferred_lat: row.preferred_lat,
    preferred_lng: row.preferred_lng,
    notes: row.notes,
    active: !!row.active,
    has_pin: !!(row as { pin_hash?: string | null }).pin_hash,
    created_at: row.created_at,
  }
}

function getDriverById(id: number) {
  return db
    .prepare(
      `SELECT id, code, name, phone, pin_hash, language, preferred_dest, preferred_lat, preferred_lng,
              notes, active, created_at FROM fleet_drivers WHERE id = ?`,
    )
    .get(id) as (FleetDriver & { pin_hash: string | null }) | undefined
}

function getDriverByCode(code: string) {
  return db
    .prepare(
      `SELECT id, code, name, phone, pin_hash, language, preferred_dest, preferred_lat, preferred_lng,
              notes, active, created_at FROM fleet_drivers WHERE code = ? COLLATE NOCASE`,
    )
    .get(code.trim()) as (FleetDriver & { pin_hash: string | null }) | undefined
}

export function assignDriverToDevice(deviceId: string, driverId: number | null) {
  db.prepare(`UPDATE fleet_devices SET driver_id = ? WHERE device_id = ?`).run(driverId, deviceId)
}

export function driverForDevice(deviceId: string) {
  const row = db
    .prepare(
      `SELECT d.id, d.code, d.name, d.phone, d.pin_hash, d.language, d.preferred_dest,
              d.preferred_lat, d.preferred_lng, d.notes, d.active, d.created_at
       FROM fleet_devices fd
       JOIN fleet_drivers d ON d.id = fd.driver_id
       WHERE fd.device_id = ?`,
    )
    .get(deviceId) as (FleetDriver & { pin_hash: string | null }) | undefined
  return row ? publicDriver(row) : null
}

/** Device-facing: list / login / logout */
export const fleetDriversRouter = Router()

fleetDriversRouter.get('/', (_req, res) => {
  const rows = db
    .prepare(
      `SELECT id, code, name, phone, pin_hash, language, preferred_dest, preferred_lat, preferred_lng,
              notes, active, created_at
       FROM fleet_drivers WHERE active = 1 ORDER BY code`,
    )
    .all() as Array<FleetDriver & { pin_hash: string | null }>
  res.json({ drivers: rows.map(publicDriver) })
})

const loginSchema = z.object({
  device_id: z.string().min(8).max(64),
  code: z.string().min(1).max(32),
  pin: z.string().max(16).optional(),
})

fleetDriversRouter.post('/login', (req, res) => {
  const parsed = loginSchema.safeParse(req.body)
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
  const driver = getDriverByCode(parsed.data.code)
  if (!driver || !driver.active) {
    res.status(404).json({ error: 'conductor no encontrado' })
    return
  }
  if (driver.pin_hash) {
    if (!parsed.data.pin || !verifyPassword(parsed.data.pin, driver.pin_hash)) {
      res.status(401).json({ error: 'PIN inválido' })
      return
    }
  }
  assignDriverToDevice(parsed.data.device_id, driver.id)
  const shift = startShift({
    deviceId: parsed.data.device_id,
    driverId: driver.id,
  })
  res.json({ ok: true, driver: publicDriver(driver), shift })
})

fleetDriversRouter.post('/logout', (req, res) => {
  const deviceId = typeof req.body?.device_id === 'string' ? req.body.device_id : ''
  if (deviceId.length < 8) {
    res.status(400).json({ error: 'device_id requerido' })
    return
  }
  const shift = endShift({ deviceId })
  assignDriverToDevice(deviceId, null)
  res.json({ ok: true, shift })
})

fleetDriversRouter.get('/current', (req, res) => {
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : ''
  if (deviceId.length < 8) {
    res.status(400).json({ error: 'device_id requerido' })
    return
  }
  res.json({ driver: driverForDevice(deviceId) })
})

/** Ops dashboard CRUD + assign */
export function mountDriverOpsRoutes(ops: Router) {
  ops.get('/drivers', (_req, res) => {
    const rows = db
      .prepare(
        `SELECT d.*, 
           (SELECT COUNT(*) FROM fleet_devices fd WHERE fd.driver_id = d.id) AS devices
         FROM fleet_drivers d ORDER BY d.code`,
      )
      .all() as Array<FleetDriver & { pin_hash: string | null; devices: number }>
    res.json({
      drivers: rows.map((r) => ({ ...publicDriver(r), devices: r.devices })),
    })
  })

  const createSchema = z.object({
    code: z.string().min(1).max(32),
    name: z.string().min(1).max(80),
    phone: z.string().max(40).optional(),
    pin: z.string().min(4).max(16).optional(),
    language: z.string().max(8).optional(),
    preferred_dest: z.string().max(80).optional(),
    preferred_lat: z.number().optional(),
    preferred_lng: z.number().optional(),
    notes: z.string().max(200).optional(),
  })

  ops.post('/drivers', requireRole('dispatcher'), (req, res) => {
    const parsed = createSchema.safeParse(req.body)
    if (!parsed.success) {
      res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
      return
    }
    const d = parsed.data
    try {
      const info = db
        .prepare(
          `INSERT INTO fleet_drivers
           (code, name, phone, pin_hash, language, preferred_dest, preferred_lat, preferred_lng, notes)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
        )
        .run(
          d.code.trim().toUpperCase(),
          d.name.trim(),
          d.phone ?? null,
          d.pin ? hashPassword(d.pin) : null,
          d.language ?? 'es',
          d.preferred_dest ?? null,
          d.preferred_lat ?? null,
          d.preferred_lng ?? null,
          d.notes ?? null,
        )
      const row = getDriverById(Number(info.lastInsertRowid))!
      res.status(201).json({ ok: true, driver: publicDriver(row) })
    } catch {
      res.status(409).json({ error: 'código ya existe' })
    }
  })

  const assignSchema = z.object({
    device_id: z.string().min(8).max(64),
    driver_id: z.number().int().positive().nullable(),
  })

  ops.post('/drivers/assign', requireRole('dispatcher'), (req, res) => {
    const parsed = assignSchema.safeParse(req.body)
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
    if (parsed.data.driver_id != null) {
      const driver = getDriverById(parsed.data.driver_id)
      if (!driver || !driver.active) {
        res.status(404).json({ error: 'conductor no encontrado' })
        return
      }
      assignDriverToDevice(parsed.data.device_id, driver.id)
      res.json({ ok: true, driver: publicDriver(driver) })
      return
    }
    assignDriverToDevice(parsed.data.device_id, null)
    res.json({ ok: true, driver: null })
  })
}

/** Resolve driver from set_driver payload (id or code). */
export function resolveDriverPayload(payload: Record<string, unknown> | undefined | null) {
  if (!payload) return null
  if (typeof payload.driver_id === 'number') {
    const d = getDriverById(payload.driver_id)
    return d && d.active ? publicDriver(d) : null
  }
  if (typeof payload.code === 'string') {
    const d = getDriverByCode(payload.code)
    return d && d.active ? publicDriver(d) : null
  }
  if (payload.clear === true || payload.driver_id === null) {
    return { clear: true as const }
  }
  return null
}
