import { Router } from 'express'
import { z } from 'zod'
import { db } from './db.js'

export const fleetRouter = Router()

function randomPairCode(): string {
  return String(Math.floor(10000000 + Math.random() * 90000000))
}

const registerSchema = z.object({
  device_id: z.string().min(8).max(64),
  name: z.string().max(80).optional(),
  app_version: z.string().max(32).optional(),
  version_code: z.number().int().nonnegative().optional(),
})

fleetRouter.post('/register', (req, res) => {
  const parsed = registerSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const { device_id, name, app_version, version_code } = parsed.data
  const existing = db
    .prepare(`SELECT device_id, pair_code FROM fleet_devices WHERE device_id = ?`)
    .get(device_id) as { device_id: string; pair_code: string | null } | undefined

  const pair = existing?.pair_code || randomPairCode()
  const now = Math.floor(Date.now() / 1000)
  db.prepare(
    `
    INSERT INTO fleet_devices (device_id, pair_code, name, app_version, version_code, last_seen_at, status)
    VALUES (@device_id, @pair_code, @name, @app_version, @version_code, @now, 'online')
    ON CONFLICT(device_id) DO UPDATE SET
      name = COALESCE(@name, name),
      app_version = COALESCE(@app_version, app_version),
      version_code = COALESCE(@version_code, version_code),
      last_seen_at = @now,
      status = 'online'
  `,
  ).run({
    device_id,
    pair_code: pair,
    name: name ?? null,
    app_version: app_version ?? null,
    version_code: version_code ?? null,
    now,
  })

  res.status(201).json({ ok: true, device_id, pair_code: pair })
})

const heartbeatSchema = z.object({
  device_id: z.string().min(8).max(64),
  app_version: z.string().max(32).optional(),
  version_code: z.number().int().nonnegative().optional(),
  lat: z.number().optional(),
  lng: z.number().optional(),
  speed_mps: z.number().optional(),
  reverse: z.boolean().optional(),
})

fleetRouter.post('/heartbeat', (req, res) => {
  const parsed = heartbeatSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const d = parsed.data
  const now = Math.floor(Date.now() / 1000)
  const row = db.prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`).get(d.device_id)
  if (!row) {
    res.status(404).json({ error: 'dispositivo no registrado — POST /api/fleet/register' })
    return
  }
  db.prepare(
    `
    UPDATE fleet_devices SET
      last_seen_at = @now,
      app_version = COALESCE(@app_version, app_version),
      version_code = COALESCE(@version_code, version_code),
      last_lat = COALESCE(@lat, last_lat),
      last_lng = COALESCE(@lng, last_lng),
      last_speed_mps = COALESCE(@speed_mps, last_speed_mps),
      reverse = COALESCE(@reverse, reverse),
      status = 'online'
    WHERE device_id = @device_id
  `,
  ).run({
    device_id: d.device_id,
    now,
    app_version: d.app_version ?? null,
    version_code: d.version_code ?? null,
    lat: d.lat ?? null,
    lng: d.lng ?? null,
    speed_mps: d.speed_mps ?? null,
    reverse: d.reverse == null ? null : d.reverse ? 1 : 0,
  })

  const latest = db
    .prepare(`SELECT version_name, version_code, apk_url, notes FROM ota_releases ORDER BY version_code DESC LIMIT 1`)
    .get() as
    | { version_name: string; version_code: number; apk_url: string; notes: string | null }
    | undefined

  const updateAvailable =
    latest != null &&
    d.version_code != null &&
    latest.version_code > d.version_code

  res.json({
    ok: true,
    server_time: now,
    ota: latest
      ? {
          update_available: updateAvailable,
          latest_version_name: latest.version_name,
          latest_version_code: latest.version_code,
          apk_url: latest.apk_url,
          notes: latest.notes,
        }
      : null,
  })
})

const pairSchema = z.object({
  pair_code: z.string().min(6).max(12),
  name: z.string().max(80).optional(),
})

fleetRouter.post('/pair', (req, res) => {
  const parsed = pairSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido' })
    return
  }
  const device = db
    .prepare(`SELECT * FROM fleet_devices WHERE pair_code = ?`)
    .get(parsed.data.pair_code) as Record<string, unknown> | undefined
  if (!device) {
    res.status(404).json({ error: 'código no encontrado' })
    return
  }
  if (parsed.data.name) {
    db.prepare(`UPDATE fleet_devices SET name = ? WHERE pair_code = ?`).run(
      parsed.data.name,
      parsed.data.pair_code,
    )
  }
  res.json({ ok: true, device })
})

fleetRouter.get('/devices', (_req, res) => {
  const rows = db
    .prepare(
      `SELECT device_id, pair_code, name, app_version, version_code, last_seen_at,
              last_lat, last_lng, last_speed_mps, reverse, status
       FROM fleet_devices ORDER BY last_seen_at DESC`,
    )
    .all()
  res.json({ devices: rows })
})

fleetRouter.get('/ota/latest', (_req, res) => {
  const latest = db
    .prepare(`SELECT version_name, version_code, apk_url, notes, created_at FROM ota_releases ORDER BY version_code DESC LIMIT 1`)
    .get()
  res.json({ release: latest ?? null })
})

const otaSchema = z.object({
  version_name: z.string().min(1),
  version_code: z.number().int().positive(),
  apk_url: z.string().url(),
  notes: z.string().optional(),
})

fleetRouter.post('/ota', (req, res) => {
  const parsed = otaSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  db.prepare(
    `INSERT INTO ota_releases (version_name, version_code, apk_url, notes)
     VALUES (@version_name, @version_code, @apk_url, @notes)
     ON CONFLICT(version_code) DO UPDATE SET
       version_name = @version_name,
       apk_url = @apk_url,
       notes = @notes`,
  ).run({
    version_name: parsed.data.version_name,
    version_code: parsed.data.version_code,
    apk_url: parsed.data.apk_url,
    notes: parsed.data.notes ?? null,
  })
  res.status(201).json({ ok: true })
})
