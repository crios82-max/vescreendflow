import { Router } from 'express'
import { z } from 'zod'
import { db } from './db.js'
import { requireRole } from './fleetOps.js'

export type MaintItem = {
  id: number
  device_id: string
  kind: string
  label: string
  interval_km: number
  last_service_odo_km: number
  warn_km: number
  enabled: number
  updated_at: number
}

export type MaintStatus = MaintItem & {
  odo_km: number | null
  due_at_km: number
  remaining_km: number | null
  /** ok | warn | due | off */
  band: string
}

const DEFAULTS: Array<{
  kind: string
  label: string
  interval_km: number
  warn_km: number
}> = [
  { kind: 'oil', label: 'Aceite', interval_km: 5000, warn_km: 500 },
  { kind: 'tires', label: 'Neumáticos', interval_km: 10000, warn_km: 800 },
  { kind: 'inspection', label: 'Revisión', interval_km: 15000, warn_km: 1000 },
  { kind: 'brakes', label: 'Frenos', interval_km: 20000, warn_km: 1000 },
  { kind: 'filter', label: 'Filtro aire', interval_km: 15000, warn_km: 500 },
]

export function ensureFleetMaintenanceTables() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS fleet_maintenance (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      device_id TEXT NOT NULL,
      kind TEXT NOT NULL,
      label TEXT NOT NULL,
      interval_km REAL NOT NULL,
      last_service_odo_km REAL NOT NULL DEFAULT 0,
      warn_km REAL NOT NULL DEFAULT 500,
      enabled INTEGER NOT NULL DEFAULT 1,
      updated_at INTEGER NOT NULL DEFAULT (unixepoch()),
      UNIQUE(device_id, kind),
      FOREIGN KEY (device_id) REFERENCES fleet_devices(device_id)
    );
    CREATE INDEX IF NOT EXISTS idx_fleet_maint_dev ON fleet_maintenance(device_id);
  `)
}

function rowToItem(r: Record<string, unknown>): MaintItem {
  return {
    id: Number(r.id),
    device_id: String(r.device_id),
    kind: String(r.kind),
    label: String(r.label),
    interval_km: Number(r.interval_km),
    last_service_odo_km: Number(r.last_service_odo_km ?? 0),
    warn_km: Number(r.warn_km ?? 500),
    enabled: Number(r.enabled ?? 1),
    updated_at: Number(r.updated_at),
  }
}

export function evaluateItem(item: MaintItem, odoKm: number | null | undefined): MaintStatus {
  const dueAt = item.last_service_odo_km + item.interval_km
  if (!item.enabled) {
    return {
      ...item,
      odo_km: odoKm ?? null,
      due_at_km: dueAt,
      remaining_km: odoKm != null ? dueAt - odoKm : null,
      band: 'off',
    }
  }
  if (odoKm == null || !Number.isFinite(odoKm)) {
    return {
      ...item,
      odo_km: null,
      due_at_km: dueAt,
      remaining_km: null,
      band: 'ok',
    }
  }
  const remaining = dueAt - odoKm
  let band = 'ok'
  if (remaining <= 0) band = 'due'
  else if (remaining <= item.warn_km) band = 'warn'
  return {
    ...item,
    odo_km: odoKm,
    due_at_km: dueAt,
    remaining_km: remaining,
    band,
  }
}

export function listMaintenance(deviceId: string): MaintItem[] {
  const rows = db
    .prepare(`SELECT * FROM fleet_maintenance WHERE device_id = ? ORDER BY kind`)
    .all(deviceId) as Array<Record<string, unknown>>
  return rows.map(rowToItem)
}

export function seedMaintenanceDefaults(deviceId: string, lastOdoKm = 0) {
  const existing = listMaintenance(deviceId)
  if (existing.length > 0) return existing
  const now = Math.floor(Date.now() / 1000)
  const ins = db.prepare(
    `INSERT INTO fleet_maintenance
     (device_id, kind, label, interval_km, last_service_odo_km, warn_km, enabled, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, 1, ?)`,
  )
  for (const d of DEFAULTS) {
    ins.run(deviceId, d.kind, d.label, d.interval_km, lastOdoKm, d.warn_km, now)
  }
  return listMaintenance(deviceId)
}

export function upsertMaintenance(input: {
  deviceId: string
  kind: string
  label?: string
  intervalKm?: number
  lastServiceOdoKm?: number
  warnKm?: number
  enabled?: boolean
}): MaintItem {
  const kind = input.kind.trim().toLowerCase().slice(0, 32)
  const def = DEFAULTS.find((d) => d.kind === kind)
  const now = Math.floor(Date.now() / 1000)
  const existing = db
    .prepare(`SELECT * FROM fleet_maintenance WHERE device_id = ? AND kind = ?`)
    .get(input.deviceId, kind) as Record<string, unknown> | undefined

  if (existing) {
    const cur = rowToItem(existing)
    db.prepare(
      `UPDATE fleet_maintenance SET
         label = ?,
         interval_km = ?,
         last_service_odo_km = ?,
         warn_km = ?,
         enabled = ?,
         updated_at = ?
       WHERE id = ?`,
    ).run(
      input.label?.trim() || cur.label,
      input.intervalKm ?? cur.interval_km,
      input.lastServiceOdoKm ?? cur.last_service_odo_km,
      input.warnKm ?? cur.warn_km,
      input.enabled == null ? cur.enabled : input.enabled ? 1 : 0,
      now,
      cur.id,
    )
  } else {
    db.prepare(
      `INSERT INTO fleet_maintenance
       (device_id, kind, label, interval_km, last_service_odo_km, warn_km, enabled, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    ).run(
      input.deviceId,
      kind,
      input.label?.trim() || def?.label || kind,
      input.intervalKm ?? def?.interval_km ?? 10000,
      input.lastServiceOdoKm ?? 0,
      input.warnKm ?? def?.warn_km ?? 500,
      input.enabled == null || input.enabled ? 1 : 0,
      now,
    )
  }
  return rowToItem(
    db
      .prepare(`SELECT * FROM fleet_maintenance WHERE device_id = ? AND kind = ?`)
      .get(input.deviceId, kind) as Record<string, unknown>,
  )
}

export function recordService(deviceId: string, kind: string, odoKm: number): MaintItem | null {
  const k = kind.trim().toLowerCase()
  seedMaintenanceDefaults(deviceId, odoKm)
  const row = db
    .prepare(`SELECT * FROM fleet_maintenance WHERE device_id = ? AND kind = ?`)
    .get(deviceId, k) as Record<string, unknown> | undefined
  if (!row) return null
  const now = Math.floor(Date.now() / 1000)
  db.prepare(
    `UPDATE fleet_maintenance SET last_service_odo_km = ?, updated_at = ? WHERE id = ?`,
  ).run(odoKm, now, Number(row.id))
  return rowToItem(
    db.prepare(`SELECT * FROM fleet_maintenance WHERE id = ?`).get(Number(row.id)) as Record<
      string,
      unknown
    >,
  )
}

function recentlyAlerted(deviceId: string, kind: string, cooldownSec: number): boolean {
  const since = Math.floor(Date.now() / 1000) - cooldownSec
  const row = db
    .prepare(
      `SELECT id FROM fleet_alerts WHERE device_id = ? AND kind = ? AND created_at >= ? LIMIT 1`,
    )
    .get(deviceId, kind, since)
  return row != null
}

function insertAlert(
  deviceId: string,
  kind: string,
  severity: string,
  message: string,
  payload: Record<string, unknown> | null,
) {
  const now = Math.floor(Date.now() / 1000)
  db.prepare(
    `INSERT INTO fleet_alerts (device_id, kind, severity, message, payload, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).run(deviceId, kind, severity, message, payload ? JSON.stringify(payload) : null, now)
}

/** Raise fleet alerts for due / warn maintenance (cooldown 6h due, 12h warn). */
export function evaluateMaintenanceAlerts(
  deviceId: string,
  odoKm: number | null | undefined,
): string[] {
  if (odoKm == null || !Number.isFinite(odoKm)) return []
  const items = seedMaintenanceDefaults(deviceId, odoKm)
  const raised: string[] = []
  for (const item of items) {
    const st = evaluateItem(item, odoKm)
    if (st.band === 'due') {
      const kind = `maint_due:${item.kind}`
      if (!recentlyAlerted(deviceId, kind, 6 * 3600)) {
        const over = Math.round(Math.abs(st.remaining_km ?? 0))
        insertAlert(
          deviceId,
          kind,
          'warn',
          `Mantenimiento vencido: ${item.label} (${over} km de atraso)`,
          {
            kind: item.kind,
            odo_km: odoKm,
            due_at_km: st.due_at_km,
            remaining_km: st.remaining_km,
          },
        )
        raised.push(kind)
      }
    } else if (st.band === 'warn') {
      const kind = `maint_warn:${item.kind}`
      if (!recentlyAlerted(deviceId, kind, 12 * 3600)) {
        const rem = Math.round(st.remaining_km ?? 0)
        insertAlert(
          deviceId,
          kind,
          'info',
          `Próximo servicio: ${item.label} en ${rem} km`,
          {
            kind: item.kind,
            odo_km: odoKm,
            due_at_km: st.due_at_km,
            remaining_km: st.remaining_km,
          },
        )
        raised.push(kind)
      }
    }
  }
  return raised
}

export function maintenanceSummary(deviceId: string, odoKm: number | null | undefined) {
  const items = listMaintenance(deviceId)
  if (items.length === 0) {
    return { due: 0, warn: 0, items: [] as MaintStatus[] }
  }
  const statuses = items.map((i) => evaluateItem(i, odoKm))
  return {
    due: statuses.filter((s) => s.band === 'due').length,
    warn: statuses.filter((s) => s.band === 'warn').length,
    items: statuses,
  }
}

export const fleetMaintenanceRouter = Router()

fleetMaintenanceRouter.get('/', (req, res) => {
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : ''
  if (deviceId.length < 8) {
    res.status(400).json({ error: 'device_id requerido' })
    return
  }
  const device = db.prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`).get(deviceId)
  if (!device) {
    res.status(404).json({ error: 'dispositivo no registrado' })
    return
  }
  const odo =
    req.query.odo_km != null && Number.isFinite(Number(req.query.odo_km))
      ? Number(req.query.odo_km)
      : null
  const seed = req.query.seed !== '0'
  if (seed) seedMaintenanceDefaults(deviceId, odo ?? 0)
  const summary = maintenanceSummary(deviceId, odo)
  res.json({ ok: true, ...summary })
})

const upsertSchema = z.object({
  device_id: z.string().min(8).max(64),
  kind: z.string().min(1).max(32),
  label: z.string().max(80).optional(),
  interval_km: z.number().positive().max(500000).optional(),
  last_service_odo_km: z.number().nonnegative().optional(),
  warn_km: z.number().nonnegative().max(50000).optional(),
  enabled: z.boolean().optional(),
})

fleetMaintenanceRouter.put('/', (req, res) => {
  const parsed = upsertSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const device = db
    .prepare(`SELECT device_id FROM fleet_devices WHERE device_id = ?`)
    .get(parsed.data.device_id)
  if (!device) {
    res.status(404).json({ error: 'dispositivo no registrado' })
    return
  }
  const item = upsertMaintenance({
    deviceId: parsed.data.device_id,
    kind: parsed.data.kind,
    label: parsed.data.label,
    intervalKm: parsed.data.interval_km,
    lastServiceOdoKm: parsed.data.last_service_odo_km,
    warnKm: parsed.data.warn_km,
    enabled: parsed.data.enabled,
  })
  res.json({ ok: true, item: evaluateItem(item, parsed.data.last_service_odo_km) })
})

const serviceSchema = z.object({
  device_id: z.string().min(8).max(64),
  kind: z.string().min(1).max(32),
  odo_km: z.number().nonnegative(),
})

fleetMaintenanceRouter.post('/service', (req, res) => {
  const parsed = serviceSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido' })
    return
  }
  const item = recordService(parsed.data.device_id, parsed.data.kind, parsed.data.odo_km)
  if (!item) {
    res.status(404).json({ error: 'ítem no encontrado' })
    return
  }
  res.json({ ok: true, item: evaluateItem(item, parsed.data.odo_km) })
})

export function mountMaintenanceOpsRoutes(ops: Router) {
  ops.get('/maintenance', requireRole('viewer'), (req, res) => {
    const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : null
    const limit = Math.min(500, Math.max(1, Number(req.query.limit) || 200))
    let sql = `SELECT * FROM fleet_maintenance`
    const params: unknown[] = []
    if (deviceId) {
      sql += ` WHERE device_id = ?`
      params.push(deviceId)
    }
    sql += ` ORDER BY device_id, kind LIMIT ?`
    params.push(limit)
    const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
    const items = rows.map(rowToItem)
    // Attach last known odo from telemetry_json when possible
    const withStatus = items.map((item) => {
      const dev = db
        .prepare(`SELECT telemetry_json FROM fleet_devices WHERE device_id = ?`)
        .get(item.device_id) as { telemetry_json: string | null } | undefined
      let odo: number | null = null
      if (dev?.telemetry_json) {
        try {
          const j = JSON.parse(dev.telemetry_json) as Record<string, unknown>
          if (typeof j.odometer_km === 'number') odo = j.odometer_km
        } catch {
          /* ignore */
        }
      }
      return evaluateItem(item, odo)
    })
    res.json({
      items: withStatus,
      due: withStatus.filter((s) => s.band === 'due').length,
      warn: withStatus.filter((s) => s.band === 'warn').length,
    })
  })

  ops.post('/maintenance/service', requireRole('dispatcher'), (req, res) => {
    const parsed = serviceSchema.safeParse(req.body)
    if (!parsed.success) {
      res.status(400).json({ error: 'payload inválido' })
      return
    }
    const item = recordService(parsed.data.device_id, parsed.data.kind, parsed.data.odo_km)
    if (!item) {
      res.status(404).json({ error: 'ítem no encontrado' })
      return
    }
    res.json({ ok: true, item: evaluateItem(item, parsed.data.odo_km) })
  })

  ops.put('/maintenance', requireRole('dispatcher'), (req, res) => {
    const parsed = upsertSchema.safeParse(req.body)
    if (!parsed.success) {
      res.status(400).json({ error: 'payload inválido' })
      return
    }
    const item = upsertMaintenance({
      deviceId: parsed.data.device_id,
      kind: parsed.data.kind,
      label: parsed.data.label,
      intervalKm: parsed.data.interval_km,
      lastServiceOdoKm: parsed.data.last_service_odo_km,
      warnKm: parsed.data.warn_km,
      enabled: parsed.data.enabled,
    })
    res.json({ ok: true, item })
  })
}
