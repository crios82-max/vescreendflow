import { Router, type Request, type Response, type NextFunction } from 'express'
import { z } from 'zod'
import { db } from './db.js'

export type FleetRole = 'viewer' | 'dispatcher' | 'admin'

export type OperatorRow = {
  id: number
  name: string
  role: FleetRole
  token: string
  created_at: number
}

const ROLE_RANK: Record<FleetRole, number> = {
  viewer: 1,
  dispatcher: 2,
  admin: 3,
}

/** Commands restricted to admin only. */
const ADMIN_ONLY = new Set(['wipe'])

/** Commands that mutate devices — dispatcher+. */
const DISPATCH_CMDS = new Set([
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
  'set_dbc',
  'fm_tune',
])

export function ensureFleetOpsTables() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS fleet_operators (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      role TEXT NOT NULL CHECK (role IN ('viewer','dispatcher','admin')),
      token TEXT NOT NULL UNIQUE,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE TABLE IF NOT EXISTS fleet_ota_events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      version_name TEXT NOT NULL,
      version_code INTEGER NOT NULL,
      apk_url TEXT,
      queued INTEGER NOT NULL DEFAULT 0,
      actor TEXT,
      notes TEXT,
      created_at INTEGER NOT NULL DEFAULT (unixepoch())
    );

    CREATE INDEX IF NOT EXISTS idx_fleet_ota_events_ts ON fleet_ota_events(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_fleet_cmd_created ON fleet_commands(created_at DESC);
  `)

  // issued_by / actor on commands
  try {
    db.exec(`ALTER TABLE fleet_commands ADD COLUMN issued_by TEXT`)
  } catch {
    /* exists */
  }

  const n = db.prepare(`SELECT COUNT(*) AS n FROM fleet_operators`).get() as { n: number }
  if (n.n === 0) {
    const ins = db.prepare(
      `INSERT INTO fleet_operators (name, role, token) VALUES (?, ?, ?)`,
    )
    ins.run('Admin flota', 'admin', 'fleet-admin-demo')
    ins.run('Despacho', 'dispatcher', 'fleet-dispatch-demo')
    ins.run('Solo lectura', 'viewer', 'fleet-viewer-demo')
  }
}

export function resolveOperator(req: Request): {
  name: string
  role: FleetRole
  token: string
} {
  const header =
    (typeof req.headers['x-fleet-token'] === 'string' && req.headers['x-fleet-token']) ||
    (typeof req.query.token === 'string' && req.query.token) ||
    ''
  if (header) {
    const row = db
      .prepare(`SELECT name, role, token FROM fleet_operators WHERE token = ?`)
      .get(header) as { name: string; role: FleetRole; token: string } | undefined
    if (row) return row
  }
  // Default open mode for local demos: dispatcher (can cmd, no wipe)
  return { name: 'anon', role: 'dispatcher', token: '' }
}

export function requireRole(min: FleetRole) {
  return (req: Request, res: Response, next: NextFunction) => {
    const op = resolveOperator(req)
    ;(req as Request & { fleetOp?: typeof op }).fleetOp = op
    if (ROLE_RANK[op.role] < ROLE_RANK[min]) {
      res.status(403).json({ error: 'rol insuficiente', role: op.role, need: min })
      return
    }
    next()
  }
}

export function canIssueCommand(role: FleetRole, command: string): boolean {
  if (!DISPATCH_CMDS.has(command) && command !== 'wipe') {
    // unknown — admin only
    return role === 'admin'
  }
  if (ADMIN_ONLY.has(command)) return role === 'admin'
  if (ROLE_RANK[role] < ROLE_RANK.dispatcher) return false
  return true
}

export function logOtaEvent(input: {
  version_name: string
  version_code: number
  apk_url?: string
  queued: number
  actor?: string
  notes?: string
}) {
  db.prepare(
    `INSERT INTO fleet_ota_events (version_name, version_code, apk_url, queued, actor, notes)
     VALUES (@version_name, @version_code, @apk_url, @queued, @actor, @notes)`,
  ).run({
    version_name: input.version_name,
    version_code: input.version_code,
    apk_url: input.apk_url ?? null,
    queued: input.queued,
    actor: input.actor ?? null,
    notes: input.notes ?? null,
  })
}

export const fleetOpsRouter = Router()

fleetOpsRouter.get('/operators', requireRole('admin'), (_req, res) => {
  const rows = db
    .prepare(`SELECT id, name, role, token, created_at FROM fleet_operators ORDER BY id`)
    .all()
  res.json({ operators: rows })
})

fleetOpsRouter.get('/me', (req, res) => {
  const op = resolveOperator(req)
  res.json({
    name: op.name,
    role: op.role,
    can_dispatch: ROLE_RANK[op.role] >= ROLE_RANK.dispatcher,
    can_wipe: op.role === 'admin',
    can_manage_ops: op.role === 'admin',
  })
})

const opSchema = z.object({
  name: z.string().min(1).max(80),
  role: z.enum(['viewer', 'dispatcher', 'admin']),
  token: z.string().min(8).max(64).optional(),
})

fleetOpsRouter.post('/operators', requireRole('admin'), (req, res) => {
  const parsed = opSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const token =
    parsed.data.token ||
    `fleet-${parsed.data.role}-${Math.random().toString(36).slice(2, 10)}`
  const info = db
    .prepare(`INSERT INTO fleet_operators (name, role, token) VALUES (?, ?, ?)`)
    .run(parsed.data.name, parsed.data.role, token)
  res.status(201).json({ ok: true, id: Number(info.lastInsertRowid), token })
})

fleetOpsRouter.get('/commands/history', (req, res) => {
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : null
  const limit = Math.min(200, Math.max(1, Number(req.query.limit) || 50))
  let sql = `SELECT id, device_id, command, payload, status, created_at, acked_at, issued_by
             FROM fleet_commands`
  const params: unknown[] = []
  if (deviceId) {
    sql += ` WHERE device_id = ?`
    params.push(deviceId)
  }
  sql += ` ORDER BY id DESC LIMIT ?`
  params.push(limit)
  const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
  res.json({
    commands: rows.map((r) => ({
      ...r,
      payload: typeof r.payload === 'string' ? safeJson(r.payload) : r.payload,
    })),
  })
})

fleetOpsRouter.get('/ota/history', (_req, res) => {
  const releases = db
    .prepare(
      `SELECT version_name, version_code, apk_url, notes, created_at FROM ota_releases ORDER BY version_code DESC LIMIT 30`,
    )
    .all()
  const events = db
    .prepare(
      `SELECT id, version_name, version_code, apk_url, queued, actor, notes, created_at
       FROM fleet_ota_events ORDER BY id DESC LIMIT 50`,
    )
    .all()
  res.json({ releases, events })
})

fleetOpsRouter.get('/reports/summary', (_req, res) => {
  const now = Math.floor(Date.now() / 1000)
  const day = now - 86_400
  const devices = db.prepare(`SELECT COUNT(*) AS n FROM fleet_devices`).get() as { n: number }
  const online = db
    .prepare(`SELECT COUNT(*) AS n FROM fleet_devices WHERE last_seen_at >= ?`)
    .get(now - 120) as { n: number }
  const stale = devices.n - online.n

  const cmds = db
    .prepare(
      `SELECT status, COUNT(*) AS n FROM fleet_commands WHERE created_at >= ? GROUP BY status`,
    )
    .all(day) as Array<{ status: string; n: number }>
  const cmdsByStatus = Object.fromEntries(cmds.map((c) => [c.status, c.n]))

  const alerts = db
    .prepare(
      `SELECT kind, COUNT(*) AS n FROM fleet_alerts WHERE created_at >= ? GROUP BY kind ORDER BY n DESC`,
    )
    .all(day) as Array<{ kind: string; n: number }>

  const versions = db
    .prepare(
      `SELECT COALESCE(app_version,'?') AS app_version, COALESCE(version_code,0) AS version_code, COUNT(*) AS n
       FROM fleet_devices GROUP BY app_version, version_code ORDER BY version_code DESC`,
    )
    .all() as Array<{ app_version: string; version_code: number; n: number }>

  const speed = db
    .prepare(
      `SELECT AVG(speed_mps) AS avg_mps, MAX(speed_mps) AS max_mps, COUNT(*) AS samples
       FROM fleet_telemetry WHERE ts >= ? AND speed_mps IS NOT NULL`,
    )
    .get(day) as { avg_mps: number | null; max_mps: number | null; samples: number }

  const openAlerts = db
    .prepare(`SELECT COUNT(*) AS n FROM fleet_alerts WHERE acked_at IS NULL`)
    .get() as { n: number }

  const latestOta = db
    .prepare(`SELECT version_name, version_code FROM ota_releases ORDER BY version_code DESC LIMIT 1`)
    .get() as { version_name: string; version_code: number } | undefined

  const belowOta =
    latestOta != null
      ? (
          db
            .prepare(
              `SELECT COUNT(*) AS n FROM fleet_devices WHERE version_code IS NULL OR version_code < ?`,
            )
            .get(latestOta.version_code) as { n: number }
        ).n
      : 0

  res.json({
    generated_at: now,
    window_s: 86_400,
    fleet: {
      devices: devices.n,
      online: online.n,
      stale,
      open_alerts: openAlerts.n,
    },
    commands_24h: cmdsByStatus,
    alerts_24h: alerts,
    versions,
    ota: {
      latest: latestOta ?? null,
      devices_below_latest: belowOta,
    },
    telemetry_24h: {
      samples: speed.samples,
      avg_kmh: speed.avg_mps != null ? speed.avg_mps * 3.6 : null,
      max_kmh: speed.max_mps != null ? speed.max_mps * 3.6 : null,
    },
  })
})

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return { text: raw }
  }
}
