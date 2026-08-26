import { Router } from 'express'
import { z } from 'zod'
import { db } from './db.js'
import {
  evaluateFleetAlerts,
  openAlertsForDevice,
  recordTelemetrySample,
} from './fleetPro.js'

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
  vehicle_signals: z.record(z.unknown()).optional(),
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

  const signals = d.vehicle_signals
  const speedFromSignals =
    typeof signals?.speed_mps === 'number' ? (signals.speed_mps as number) : undefined
  const reverseFromSignals =
    typeof signals?.reverse === 'boolean' ? (signals.reverse as boolean) : undefined
  const speed = d.speed_mps ?? speedFromSignals
  const reverse = d.reverse ?? reverseFromSignals
  const telemetryJson = signals != null ? JSON.stringify(signals) : null

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
      telemetry_json = COALESCE(@telemetry_json, telemetry_json),
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
    speed_mps: speed ?? null,
    reverse: reverse == null ? null : reverse ? 1 : 0,
    telemetry_json: telemetryJson,
  })

  recordTelemetrySample(
    d.device_id,
    d.lat,
    d.lng,
    speed,
    signals as Record<string, unknown> | undefined,
  )
  const raised = evaluateFleetAlerts(
    d.device_id,
    d.lat,
    d.lng,
    signals as Record<string, unknown> | undefined,
  )
  const openAlerts = openAlertsForDevice(d.device_id)

  const latest = db
    .prepare(`SELECT version_name, version_code, apk_url, notes FROM ota_releases ORDER BY version_code DESC LIMIT 1`)
    .get() as
    | { version_name: string; version_code: number; apk_url: string; notes: string | null }
    | undefined

  const updateAvailable =
    latest != null &&
    d.version_code != null &&
    latest.version_code > d.version_code

  const pending = db
    .prepare(
      `SELECT id, command, payload, created_at FROM fleet_commands
       WHERE device_id = ? AND status = 'pending'
       ORDER BY id ASC LIMIT 20`,
    )
    .all(d.device_id) as Array<{ id: number; command: string; payload: string | null; created_at: number }>

  res.json({
    ok: true,
    server_time: now,
    alerts_raised: raised,
    alerts: openAlerts.map((a) => ({
      id: a.id,
      kind: a.kind,
      severity: a.severity,
      message: a.message,
      created_at: a.created_at,
    })),
    ota: latest
      ? {
          update_available: updateAvailable,
          latest_version_name: latest.version_name,
          latest_version_code: latest.version_code,
          apk_url: latest.apk_url,
          notes: latest.notes,
        }
      : null,
    commands: pending.map((c) => ({
      id: c.id,
      command: c.command,
      payload: c.payload ? safeJson(c.payload) : null,
      created_at: c.created_at,
    })),
  })
})

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return { text: raw }
  }
}

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
              last_lat, last_lng, last_speed_mps, reverse, status, telemetry_json
       FROM fleet_devices ORDER BY last_seen_at DESC`,
    )
    .all() as Array<Record<string, unknown>>

  const devices = rows.map((r) => {
    let vehicle_signals: unknown = null
    const raw = r.telemetry_json
    if (typeof raw === 'string' && raw.length > 0) {
      try {
        vehicle_signals = JSON.parse(raw)
      } catch {
        vehicle_signals = null
      }
    }
    const { telemetry_json: _drop, ...rest } = r
    return { ...rest, vehicle_signals }
  })
  res.json({ devices })
})

fleetRouter.get('/ota/latest', (_req, res) => {
  const latest = db
    .prepare(`SELECT version_name, version_code, apk_url, notes, created_at FROM ota_releases ORDER BY version_code DESC LIMIT 1`)
    .get()
  res.json({ release: latest ?? null })
})

fleetRouter.get('/ota/releases', (_req, res) => {
  const rows = db
    .prepare(
      `SELECT version_name, version_code, apk_url, notes, created_at FROM ota_releases ORDER BY version_code DESC LIMIT 50`,
    )
    .all()
  res.json({ releases: rows })
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

/** Queue silent OTA to devices below a version_code (default: latest release). */
const rolloutSchema = z.object({
  version_code: z.number().int().positive().optional(),
  device_ids: z.array(z.string().min(8).max(64)).optional(),
  silent: z.boolean().optional().default(true),
})

fleetRouter.post('/ota/rollout', (req, res) => {
  const parsed = rolloutSchema.safeParse(req.body ?? {})
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const targetCode =
    parsed.data.version_code ??
    (
      db
        .prepare(`SELECT version_code FROM ota_releases ORDER BY version_code DESC LIMIT 1`)
        .get() as { version_code: number } | undefined
    )?.version_code
  if (!targetCode) {
    res.status(404).json({ error: 'no hay releases OTA' })
    return
  }
  const release = db
    .prepare(
      `SELECT version_name, version_code, apk_url FROM ota_releases WHERE version_code = ?`,
    )
    .get(targetCode) as
    | { version_name: string; version_code: number; apk_url: string }
    | undefined
  if (!release) {
    res.status(404).json({ error: 'release no encontrado' })
    return
  }

  let devices: Array<{ device_id: string; version_code: number | null }>
  if (parsed.data.device_ids?.length) {
    devices = parsed.data.device_ids.map((id) => {
      const row = db
        .prepare(`SELECT device_id, version_code FROM fleet_devices WHERE device_id = ?`)
        .get(id) as { device_id: string; version_code: number | null } | undefined
      return row ?? { device_id: id, version_code: null }
    })
  } else {
    devices = db
      .prepare(
        `SELECT device_id, version_code FROM fleet_devices
         WHERE version_code IS NULL OR version_code < ?`,
      )
      .all(targetCode) as Array<{ device_id: string; version_code: number | null }>
  }

  const payload = JSON.stringify({
    apk_url: release.apk_url,
    silent: parsed.data.silent !== false,
    version_code: release.version_code,
    version_name: release.version_name,
  })
  const ins = db.prepare(
    `INSERT INTO fleet_commands (device_id, command, payload, status)
     VALUES (?, 'ota', ?, 'pending')`,
  )
  const queued: string[] = []
  const tx = db.transaction(() => {
    for (const d of devices) {
      // skip unknown device_ids that were explicitly listed but not registered
      const exists = db.prepare(`SELECT 1 FROM fleet_devices WHERE device_id = ?`).get(d.device_id)
      if (!exists) continue
      ins.run(d.device_id, payload)
      queued.push(d.device_id)
    }
  })
  tx()
  res.status(201).json({
    ok: true,
    version_code: release.version_code,
    version_name: release.version_name,
    apk_url: release.apk_url,
    queued: queued.length,
    device_ids: queued,
  })
})

const commandSchema = z.object({
  device_id: z.string().min(8).max(64),
  command: z.enum([
    'restart',
    'lock',
    'message',
    'wipe',
    'ota',
    'set_source',
    'reboot_obd',
    'nav_dest',
    'lock_task',
    'apply_kiosk',
    'run_diag',
  ]),
  payload: z.record(z.string(), z.unknown()).optional(),
})

fleetRouter.post('/command', (req, res) => {
  const parsed = commandSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const exists = db
    .prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`)
    .get(parsed.data.device_id)
  if (!exists) {
    res.status(404).json({ error: 'dispositivo no encontrado' })
    return
  }
  const info = db
    .prepare(
      `INSERT INTO fleet_commands (device_id, command, payload, status)
       VALUES (?, ?, ?, 'pending')`,
    )
    .run(
      parsed.data.device_id,
      parsed.data.command,
      parsed.data.payload ? JSON.stringify(parsed.data.payload) : null,
    )
  res.status(201).json({ ok: true, id: Number(info.lastInsertRowid) })
})

const ackSchema = z.object({
  device_id: z.string().min(8).max(64),
  command_ids: z.array(z.number().int().positive()).min(1).max(50),
  status: z.enum(['acked', 'done', 'failed']).default('acked'),
})

fleetRouter.post('/command/ack', (req, res) => {
  const parsed = ackSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const now = Math.floor(Date.now() / 1000)
  const upd = db.prepare(
    `UPDATE fleet_commands SET status = ?, acked_at = ? WHERE id = ? AND device_id = ?`,
  )
  const tx = db.transaction(() => {
    let n = 0
    for (const id of parsed.data.command_ids) {
      n += upd.run(parsed.data.status, now, id, parsed.data.device_id).changes
    }
    return n
  })
  res.json({ ok: true, updated: tx() })
})

// —— Flota pro: geofences / alerts / telemetry ——

fleetRouter.get('/geofences', (_req, res) => {
  const rows = db
    .prepare(`SELECT id, name, lat, lng, radius_m, active, created_at FROM fleet_geofences ORDER BY id`)
    .all()
  res.json({ geofences: rows })
})

const geofenceSchema = z.object({
  name: z.string().min(1).max(80),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  radius_m: z.number().positive().max(50_000).default(250),
  active: z.boolean().optional(),
})

fleetRouter.post('/geofences', (req, res) => {
  const parsed = geofenceSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const info = db
    .prepare(
      `INSERT INTO fleet_geofences (name, lat, lng, radius_m, active)
       VALUES (?, ?, ?, ?, ?)`,
    )
    .run(
      parsed.data.name,
      parsed.data.lat,
      parsed.data.lng,
      parsed.data.radius_m,
      parsed.data.active === false ? 0 : 1,
    )
  res.status(201).json({ ok: true, id: Number(info.lastInsertRowid) })
})

fleetRouter.get('/alerts', (req, res) => {
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : null
  const openOnly = req.query.open !== '0'
  let sql = `SELECT id, device_id, kind, severity, message, payload, created_at, acked_at
             FROM fleet_alerts`
  const params: unknown[] = []
  const where: string[] = []
  if (deviceId) {
    where.push('device_id = ?')
    params.push(deviceId)
  }
  if (openOnly) where.push('acked_at IS NULL')
  if (where.length) sql += ` WHERE ${where.join(' AND ')}`
  sql += ' ORDER BY id DESC LIMIT 100'
  const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
  res.json({
    alerts: rows.map((r) => ({
      ...r,
      payload: typeof r.payload === 'string' ? safeJson(r.payload) : r.payload,
    })),
  })
})

fleetRouter.post('/alerts/:id/ack', (req, res) => {
  const id = Number(req.params.id)
  if (!Number.isFinite(id)) {
    res.status(400).json({ error: 'id inválido' })
    return
  }
  const now = Math.floor(Date.now() / 1000)
  const info = db.prepare(`UPDATE fleet_alerts SET acked_at = ? WHERE id = ?`).run(now, id)
  res.json({ ok: true, updated: info.changes })
})

fleetRouter.get('/telemetry/:deviceId', (req, res) => {
  const deviceId = req.params.deviceId
  const limit = Math.min(500, Math.max(1, Number(req.query.limit) || 100))
  const rows = db
    .prepare(
      `SELECT id, ts, lat, lng, speed_mps, telemetry_json
       FROM fleet_telemetry WHERE device_id = ? ORDER BY ts DESC LIMIT ?`,
    )
    .all(deviceId, limit) as Array<Record<string, unknown>>
  res.json({
    samples: rows
      .map((r) => ({
        id: r.id,
        ts: r.ts,
        lat: r.lat,
        lng: r.lng,
        speed_mps: r.speed_mps,
        vehicle_signals:
          typeof r.telemetry_json === 'string' ? safeJson(r.telemetry_json as string) : null,
      }))
      .reverse(),
  })
})
