import { Router } from 'express'
import { z } from 'zod'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { db } from './db.js'
import {
  evaluateFleetAlerts,
  openAlertsForDevice,
  openPanicForDevice,
  raisePanic,
  raiseIncident,
  ackPanicsForDevice,
  attachPanicClip,
  panicClipUrlFromAlert,
  activeSpeedZone,
  recordTelemetrySample,
  insertAlert,
} from './fleetPro.js'
import {
  evaluateMaintenanceAlerts,
  maintenanceSummary,
  recordService,
  upsertMaintenance,
} from './fleetMaintenance.js'
import { assertCanMutate, logOtaEvent } from './fleetOps.js'
import {
  assignDriverToDevice,
  driverForDevice,
  resolveDriverPayload,
} from './fleetDrivers.js'
import { openShiftForDevice, startShift, touchShift, endShift } from './fleetTrips.js'

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
  driver_id: z.number().int().positive().optional(),
  driver_code: z.string().max(32).optional(),
  odo_km: z.number().nonnegative().optional(),
  shift_delta_km: z.number().nonnegative().optional(),
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

  // Sync driver assignment from device heartbeat when provided
  if (d.driver_id != null || d.driver_code) {
    const resolved = resolveDriverPayload({
      driver_id: d.driver_id,
      code: d.driver_code,
    })
    if (resolved && !('clear' in resolved)) {
      assignDriverToDevice(d.device_id, resolved.id)
      if (!openShiftForDevice(d.device_id)) {
        startShift({
          deviceId: d.device_id,
          driverId: resolved.id,
          odoKm: d.odo_km,
          lat: d.lat,
          lng: d.lng,
        })
      }
    }
  }

  const odoFromSignals =
    typeof signals?.odometer_km === 'number' ? (signals.odometer_km as number) : undefined
  const odoKm = d.odo_km ?? odoFromSignals
  touchShift({
    deviceId: d.device_id,
    odoKm,
    deltaKm: d.shift_delta_km,
    lat: d.lat,
    lng: d.lng,
    signals: signals as Record<string, unknown> | undefined,
  })

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
    speed,
  )
  const maintRaised = evaluateMaintenanceAlerts(d.device_id, odoKm)
  raised.push(...maintRaised)
  const openAlerts = openAlertsForDevice(d.device_id)
  const maintenance = maintenanceSummary(d.device_id, odoKm)
  const speedZone = activeSpeedZone(d.lat, d.lng)

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
    maintenance: {
      due: maintenance.due,
      warn: maintenance.warn,
      items: maintenance.items.map((i) => ({
        kind: i.kind,
        label: i.label,
        band: i.band,
        remaining_km: i.remaining_km,
        due_at_km: i.due_at_km,
        interval_km: i.interval_km,
        last_service_odo_km: i.last_service_odo_km,
        enabled: i.enabled === 1,
      })),
    },
    panic: (() => {
      const p = openPanicForDevice(d.device_id)
      return p
        ? {
            open: true,
            id: p.id,
            message: p.message,
            created_at: p.created_at,
            clip_url: panicClipUrlFromAlert(p),
          }
        : { open: false }
    })(),
    speed_zone: speedZone,
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
    driver: driverForDevice(d.device_id),
    shift: openShiftForDevice(d.device_id),
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
      `SELECT fd.device_id, fd.pair_code, fd.name, fd.app_version, fd.version_code, fd.last_seen_at,
              fd.last_lat, fd.last_lng, fd.last_speed_mps, fd.reverse, fd.status, fd.telemetry_json,
              fd.driver_id, dr.code AS driver_code, dr.name AS driver_name
       FROM fleet_devices fd
       LEFT JOIN fleet_drivers dr ON dr.id = fd.driver_id
       ORDER BY fd.last_seen_at DESC`,
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
  const op = assertCanMutate(req, res, 'ota')
  if (!op) return
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

  const actor = `${op.name}<${op.role}>`
  const payload = JSON.stringify({
    apk_url: release.apk_url,
    silent: parsed.data.silent !== false,
    version_code: release.version_code,
    version_name: release.version_name,
  })
  const ins = db.prepare(
    `INSERT INTO fleet_commands (device_id, command, payload, status, issued_by)
     VALUES (?, 'ota', ?, 'pending', ?)`,
  )
  const queued: string[] = []
  const tx = db.transaction(() => {
    for (const d of devices) {
      const exists = db.prepare(`SELECT 1 FROM fleet_devices WHERE device_id = ?`).get(d.device_id)
      if (!exists) continue
      ins.run(d.device_id, payload, actor)
      queued.push(d.device_id)
    }
  })
  tx()
  logOtaEvent({
    version_name: release.version_name,
    version_code: release.version_code,
    apk_url: release.apk_url,
    queued: queued.length,
    actor,
    notes: 'rollout',
  })
  res.status(201).json({
    ok: true,
    version_code: release.version_code,
    version_name: release.version_name,
    apk_url: release.apk_url,
    queued: queued.length,
    device_ids: queued,
    issued_by: actor,
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
    'read_dtc',
    'clear_dtc',
    'seed_dtc',
    'prefetch_tiles',
    'nav_dest',
    'lock_task',
    'apply_kiosk',
    'run_diag',
    'set_dbc',
    'fm_tune',
    'set_driver',
    'set_speed_limit',
    'set_fuel_warn',
    'set_idle_warn',
    'service_done',
    'set_maintenance',
    'panic_ack',
  ]),
  payload: z.record(z.string(), z.unknown()).optional(),
})

fleetRouter.post('/command', (req, res) => {
  const parsed = commandSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const op = assertCanMutate(req, res, parsed.data.command)
  if (!op) return
  const exists = db
    .prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`)
    .get(parsed.data.device_id)
  if (!exists) {
    res.status(404).json({ error: 'dispositivo no encontrado' })
    return
  }
  const actor = `${op.name}<${op.role}>`
  if (parsed.data.command === 'set_driver') {
    const resolved = resolveDriverPayload(parsed.data.payload ?? null)
    if (resolved && 'clear' in resolved) {
      endShift({ deviceId: parsed.data.device_id })
      assignDriverToDevice(parsed.data.device_id, null)
    } else if (resolved) {
      assignDriverToDevice(parsed.data.device_id, resolved.id)
      startShift({ deviceId: parsed.data.device_id, driverId: resolved.id })
    } else {
      res.status(400).json({ error: 'set_driver requiere code, driver_id o clear' })
      return
    }
  }
  if (parsed.data.command === 'service_done') {
    const p = parsed.data.payload ?? {}
    const kind = typeof p.kind === 'string' ? p.kind : ''
    const odo = typeof p.odo_km === 'number' ? p.odo_km : null
    if (!kind || odo == null) {
      res.status(400).json({ error: 'service_done requiere kind + odo_km' })
      return
    }
    const item = recordService(parsed.data.device_id, kind, odo)
    if (!item) {
      res.status(404).json({ error: 'ítem mantenimiento no encontrado' })
      return
    }
  }
  if (parsed.data.command === 'set_maintenance') {
    const p = parsed.data.payload ?? {}
    const kind = typeof p.kind === 'string' ? p.kind : ''
    if (!kind) {
      res.status(400).json({ error: 'set_maintenance requiere kind' })
      return
    }
    upsertMaintenance({
      deviceId: parsed.data.device_id,
      kind,
      label: typeof p.label === 'string' ? p.label : undefined,
      intervalKm: typeof p.interval_km === 'number' ? p.interval_km : undefined,
      lastServiceOdoKm:
        typeof p.last_service_odo_km === 'number'
          ? p.last_service_odo_km
          : typeof p.last_odo_km === 'number'
            ? p.last_odo_km
            : undefined,
      warnKm: typeof p.warn_km === 'number' ? p.warn_km : undefined,
      enabled: typeof p.enabled === 'boolean' ? p.enabled : undefined,
    })
  }
  if (parsed.data.command === 'panic_ack') {
    ackPanicsForDevice(parsed.data.device_id)
  }
  let payloadJson: string | null = parsed.data.payload
    ? JSON.stringify(parsed.data.payload)
    : null
  if (parsed.data.command === 'message') {
    const text =
      typeof parsed.data.payload?.text === 'string' && parsed.data.payload.text.trim()
        ? String(parsed.data.payload.text).trim().slice(0, 500)
        : 'Mensaje de flota'
    const alertId = insertAlert(parsed.data.device_id, 'message', 'info', text, {
      requires_ack: true,
      from: actor,
    })
    payloadJson = JSON.stringify({
      ...(parsed.data.payload ?? {}),
      text,
      alert_id: alertId,
      requires_ack: true,
    })
  }
  const info = db
    .prepare(
      `INSERT INTO fleet_commands (device_id, command, payload, status, issued_by)
       VALUES (?, ?, ?, 'pending', ?)`,
    )
    .run(
      parsed.data.device_id,
      parsed.data.command,
      payloadJson,
      actor,
    )
  res.status(201).json({ ok: true, id: Number(info.lastInsertRowid), issued_by: actor })
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
    .prepare(
      `SELECT id, name, lat, lng, radius_m, max_kmh, active, created_at FROM fleet_geofences ORDER BY id`,
    )
    .all()
  res.json({ geofences: rows })
})

const geofenceSchema = z.object({
  name: z.string().min(1).max(80),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  radius_m: z.number().positive().max(50_000).default(250),
  max_kmh: z.number().positive().max(160).nullable().optional(),
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
      `INSERT INTO fleet_geofences (name, lat, lng, radius_m, max_kmh, active)
       VALUES (?, ?, ?, ?, ?, ?)`,
    )
    .run(
      parsed.data.name,
      parsed.data.lat,
      parsed.data.lng,
      parsed.data.radius_m,
      parsed.data.max_kmh ?? null,
      parsed.data.active === false ? 0 : 1,
    )
  res.status(201).json({ ok: true, id: Number(info.lastInsertRowid) })
})

const geofencePatchSchema = z.object({
  max_kmh: z.number().positive().max(160).nullable().optional(),
  name: z.string().min(1).max(80).optional(),
  radius_m: z.number().positive().max(50_000).optional(),
  active: z.boolean().optional(),
})

fleetRouter.patch('/geofences/:id', (req, res) => {
  const id = Number(req.params.id)
  if (!Number.isFinite(id)) {
    res.status(400).json({ error: 'id inválido' })
    return
  }
  const parsed = geofencePatchSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido' })
    return
  }
  const row = db.prepare(`SELECT id FROM fleet_geofences WHERE id = ?`).get(id)
  if (!row) {
    res.status(404).json({ error: 'geofence no encontrada' })
    return
  }
  const d = parsed.data
  if (d.max_kmh !== undefined) {
    db.prepare(`UPDATE fleet_geofences SET max_kmh = ? WHERE id = ?`).run(d.max_kmh, id)
  }
  if (d.name != null) {
    db.prepare(`UPDATE fleet_geofences SET name = ? WHERE id = ?`).run(d.name, id)
  }
  if (d.radius_m != null) {
    db.prepare(`UPDATE fleet_geofences SET radius_m = ? WHERE id = ?`).run(d.radius_m, id)
  }
  if (d.active != null) {
    db.prepare(`UPDATE fleet_geofences SET active = ? WHERE id = ?`).run(d.active ? 1 : 0, id)
  }
  const updated = db
    .prepare(
      `SELECT id, name, lat, lng, radius_m, max_kmh, active, created_at FROM fleet_geofences WHERE id = ?`,
    )
    .get(id)
  res.json({ ok: true, geofence: updated })
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
  sql += ' ORDER BY CASE WHEN severity = \'critical\' THEN 0 ELSE 1 END, id DESC LIMIT 100'
  const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
  res.json({
    alerts: rows.map((r) => ({
      ...r,
      payload: typeof r.payload === 'string' ? safeJson(r.payload) : r.payload,
    })),
  })
})

const panicSchema = z.object({
  device_id: z.string().min(8).max(64),
  lat: z.number().optional(),
  lng: z.number().optional(),
  note: z.string().max(200).optional(),
  source: z.string().max(32).optional(),
  driver_code: z.string().max(32).optional(),
  driver_name: z.string().max(80).optional(),
})

fleetRouter.post('/panic', (req, res) => {
  const parsed = panicSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const exists = db
    .prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`)
    .get(parsed.data.device_id)
  if (!exists) {
    res.status(404).json({ error: 'dispositivo no registrado' })
    return
  }
  const result = raisePanic(parsed.data.device_id, {
    lat: parsed.data.lat,
    lng: parsed.data.lng,
    note: parsed.data.note,
    source: parsed.data.source ?? 'device',
    driver_code: parsed.data.driver_code,
    driver_name: parsed.data.driver_name,
  })
  const alert = openPanicForDevice(parsed.data.device_id)
  res.status(result.deduped ? 200 : 201).json({
    ok: true,
    deduped: result.deduped,
    alert: alert
      ? {
          id: alert.id,
          kind: alert.kind,
          severity: alert.severity,
          message: alert.message,
          created_at: alert.created_at,
          clip_url: panicClipUrlFromAlert(alert),
        }
      : { id: result.id },
  })
})

const incidentSchema = z.object({
  device_id: z.string().min(8).max(64),
  category: z.enum(['accident', 'breakdown', 'traffic', 'other']).default('other'),
  note: z.string().max(280).optional(),
  lat: z.number().optional(),
  lng: z.number().optional(),
  source: z.string().max(32).optional(),
  driver_code: z.string().max(32).optional(),
  driver_name: z.string().max(80).optional(),
  // optional inline clip (JPEG base64)
  clip_kind: z.enum(['jpeg', 'jpg', 'png']).optional(),
  clip_base64: z.string().min(8).max(2_800_000).optional(),
  clip_sim: z.boolean().optional(),
})

fleetRouter.post('/incident', (req, res) => {
  const parsed = incidentSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const exists = db
    .prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`)
    .get(parsed.data.device_id)
  if (!exists) {
    res.status(404).json({ error: 'dispositivo no registrado' })
    return
  }

  let clipUrl: string | null = null
  if (parsed.data.clip_base64) {
    let raw: Buffer
    try {
      const b64 = parsed.data.clip_base64.replace(/^data:[^;]+;base64,/, '')
      raw = Buffer.from(b64, 'base64')
    } catch {
      res.status(400).json({ error: 'clip base64 inválido' })
      return
    }
    if (raw.length < 32 || raw.length > 2_500_000) {
      res.status(400).json({ error: 'clip demasiado pequeño o grande' })
      return
    }
    const kind = parsed.data.clip_kind === 'png' ? 'png' : 'jpeg'
    const ext = kind === 'png' ? 'png' : 'jpg'
    const stamp = Date.now().toString(36)
    const safeDev = parsed.data.device_id.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 24)
    const filename = `inc-${safeDev}-${stamp}.${ext}`
    const clipsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../clips')
    fs.mkdirSync(clipsDir, { recursive: true })
    fs.writeFileSync(path.join(clipsDir, filename), raw)
    clipUrl = `/clips/${filename}`
  }

  const result = raiseIncident(parsed.data.device_id, {
    category: parsed.data.category,
    note: parsed.data.note,
    lat: parsed.data.lat,
    lng: parsed.data.lng,
    source: parsed.data.source ?? 'device',
    driver_code: parsed.data.driver_code,
    driver_name: parsed.data.driver_name,
    clip_url: clipUrl,
  })

  if (clipUrl && result.id > 0) {
    attachPanicClip(parsed.data.device_id, {
      alertId: result.id,
      clipUrl,
      kind: parsed.data.clip_kind === 'png' ? 'png' : 'jpeg',
      camera: 'incident',
      durationSec: 8,
      bytes: null,
      sim: parsed.data.clip_sim ?? false,
    })
  }

  const row = db
    .prepare(
      `SELECT id, device_id, kind, severity, message, payload, created_at, acked_at
       FROM fleet_alerts WHERE id = ?`,
    )
    .get(result.id) as
    | {
        id: number
        kind: string
        severity: string
        message: string
        payload: string | null
        created_at: number
      }
    | undefined

  res.status(result.deduped ? 200 : 201).json({
    ok: true,
    deduped: result.deduped,
    alert: row
      ? {
          id: row.id,
          kind: row.kind,
          severity: row.severity,
          message: row.message,
          created_at: row.created_at,
          clip_url: panicClipUrlFromAlert(row as never) ?? clipUrl,
          payload: row.payload ? safeJson(row.payload) : null,
        }
      : { id: result.id, clip_url: clipUrl },
  })
})

const panicClipSchema = z.object({
  device_id: z.string().min(8).max(64),
  alert_id: z.number().int().positive().optional(),
  kind: z.enum(['jpeg', 'jpg', 'mp4', 'png']).default('jpeg'),
  data_base64: z.string().min(8).max(2_800_000),
  camera: z.string().max(64).optional(),
  duration_sec: z.number().min(0).max(120).optional(),
  sim: z.boolean().optional(),
  captured_at_ms: z.number().optional(),
})

fleetRouter.post('/panic/clip', (req, res) => {
  const parsed = panicClipSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const exists = db
    .prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`)
    .get(parsed.data.device_id)
  if (!exists) {
    res.status(404).json({ error: 'dispositivo no registrado' })
    return
  }
  const open = openPanicForDevice(parsed.data.device_id)
  if (!open && parsed.data.alert_id == null) {
    res.status(409).json({ error: 'no hay SOS abierto — envía panic primero' })
    return
  }

  let raw: Buffer
  try {
    const b64 = parsed.data.data_base64.replace(/^data:[^;]+;base64,/, '')
    raw = Buffer.from(b64, 'base64')
  } catch {
    res.status(400).json({ error: 'base64 inválido' })
    return
  }
  if (raw.length < 32 || raw.length > 2_500_000) {
    res.status(400).json({ error: 'clip demasiado pequeño o grande' })
    return
  }

  const kind = parsed.data.kind === 'jpg' ? 'jpeg' : parsed.data.kind
  const ext = kind === 'mp4' ? 'mp4' : kind === 'png' ? 'png' : 'jpg'
  const stamp = Date.now().toString(36)
  const safeDev = parsed.data.device_id.replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 24)
  const filename = `sos-${safeDev}-${stamp}.${ext}`
  const clipsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../clips')
  fs.mkdirSync(clipsDir, { recursive: true })
  fs.writeFileSync(path.join(clipsDir, filename), raw)

  const clipUrl = `/clips/${filename}`
  const updated = attachPanicClip(parsed.data.device_id, {
    alertId: parsed.data.alert_id ?? open?.id ?? null,
    clipUrl,
    kind,
    camera: parsed.data.camera ?? null,
    durationSec: parsed.data.duration_sec ?? 8,
    bytes: raw.length,
    sim: parsed.data.sim ?? false,
  })
  if (!updated) {
    res.status(409).json({ error: 'no se pudo adjuntar clip al SOS' })
    return
  }
  res.status(201).json({
    ok: true,
    clip_url: clipUrl,
    bytes: raw.length,
    alert_id: updated.id,
    kind,
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

const messageAckSchema = z.object({
  device_id: z.string().min(8).max(64),
  alert_id: z.number().int().positive(),
})

fleetRouter.post('/message/ack', (req, res) => {
  const parsed = messageAckSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido' })
    return
  }
  const row = db
    .prepare(
      `SELECT id, device_id, kind, message, acked_at FROM fleet_alerts WHERE id = ?`,
    )
    .get(parsed.data.alert_id) as
    | { id: number; device_id: string; kind: string; message: string; acked_at: number | null }
    | undefined
  if (!row || row.device_id !== parsed.data.device_id) {
    res.status(404).json({ error: 'mensaje no encontrado' })
    return
  }
  if (row.kind !== 'message' && row.kind !== 'message_reply') {
    // Allow ack of any open alert from device for convenience
  }
  const now = Math.floor(Date.now() / 1000)
  if (row.acked_at == null) {
    db.prepare(`UPDATE fleet_alerts SET acked_at = ? WHERE id = ?`).run(now, row.id)
  }
  res.json({
    ok: true,
    alert_id: row.id,
    acked: true,
    message: row.message,
  })
})

const CANNED = new Set(['ok', 'en_camino', 'retraso', 'ayuda', 'recibido'])

const messageReplySchema = z.object({
  device_id: z.string().min(8).max(64),
  alert_id: z.number().int().positive().optional(),
  text: z.string().min(1).max(500).optional(),
  canned: z.string().min(1).max(40).optional(),
})

fleetRouter.post('/message/reply', (req, res) => {
  const parsed = messageReplySchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido' })
    return
  }
  const cannedKey = parsed.data.canned?.trim().toLowerCase().replace(/\s+/g, '_')
  const cannedLabels: Record<string, string> = {
    ok: 'OK',
    recibido: 'Recibido',
    en_camino: 'En camino',
    retraso: 'Voy con retraso',
    ayuda: 'Necesito ayuda',
  }
  let text =
    (parsed.data.text && parsed.data.text.trim()) ||
    (cannedKey && cannedLabels[cannedKey]) ||
    ''
  if (!text) {
    res.status(400).json({ error: 'text o canned requerido' })
    return
  }
  text = text.slice(0, 500)
  if (cannedKey && !CANNED.has(cannedKey) && !parsed.data.text) {
    res.status(400).json({ error: 'canned desconocido' })
    return
  }

  let parent: { id: number; message: string } | null = null
  if (parsed.data.alert_id) {
    const row = db
      .prepare(
        `SELECT id, device_id, kind, message, acked_at FROM fleet_alerts WHERE id = ?`,
      )
      .get(parsed.data.alert_id) as
      | {
          id: number
          device_id: string
          kind: string
          message: string
          acked_at: number | null
        }
      | undefined
    if (!row || row.device_id !== parsed.data.device_id) {
      res.status(404).json({ error: 'mensaje origen no encontrado' })
      return
    }
    parent = { id: row.id, message: row.message }
    if (row.acked_at == null) {
      const now = Math.floor(Date.now() / 1000)
      db.prepare(`UPDATE fleet_alerts SET acked_at = ? WHERE id = ?`).run(now, row.id)
    }
  }

  const replyId = insertAlert(
    parsed.data.device_id,
    'message_reply',
    cannedKey === 'ayuda' ? 'warn' : 'info',
    text,
    {
      parent_alert_id: parent?.id ?? null,
      parent_message: parent?.message ?? null,
      canned: cannedKey ?? null,
      from: 'device',
    },
  )

  res.status(201).json({
    ok: true,
    reply: {
      id: replyId,
      kind: 'message_reply',
      message: text,
      canned: cannedKey ?? null,
      parent_alert_id: parent?.id ?? null,
    },
    parent_acked: parent != null,
  })
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
