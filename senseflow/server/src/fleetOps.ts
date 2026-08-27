import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'crypto'
import { Router, type Request, type Response, type NextFunction } from 'express'
import { z } from 'zod'
import { db } from './db.js'

export type FleetRole = 'viewer' | 'dispatcher' | 'admin'

export type FleetOp = {
  id?: number
  name: string
  role: FleetRole
  token: string
  authenticated: boolean
}

const ROLE_RANK: Record<FleetRole, number> = {
  viewer: 1,
  dispatcher: 2,
  admin: 3,
}

const ADMIN_ONLY = new Set(['wipe'])

const DISPATCH_CMDS = new Set([
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
])

const SESSION_TTL_S = 12 * 3600

/** Local/dev default: anon dispatcher. Prod: FLEET_OPEN_MODE=0 */
export function isOpenMode(): boolean {
  return process.env.FLEET_OPEN_MODE !== '0'
}

export function hashToken(raw: string): string {
  return createHash('sha256').update(raw).digest('hex')
}

export function hashPassword(password: string, saltHex?: string): string {
  const salt = saltHex ?? randomBytes(16).toString('hex')
  const hash = scryptSync(password, salt, 32).toString('hex')
  return `${salt}:${hash}`
}

export function verifyPassword(password: string, stored: string): boolean {
  const [salt, hash] = stored.split(':')
  if (!salt || !hash) return false
  const got = scryptSync(password, salt, 32)
  const want = Buffer.from(hash, 'hex')
  if (got.length !== want.length) return false
  return timingSafeEqual(got, want)
}

function newApiToken(role: string): string {
  return `fleet-${role}-${randomBytes(12).toString('hex')}`
}

function newSessionId(): string {
  return randomBytes(24).toString('hex')
}

export function ensureFleetOpsTables() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS fleet_operators (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      role TEXT NOT NULL CHECK (role IN ('viewer','dispatcher','admin')),
      token TEXT UNIQUE,
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

    CREATE TABLE IF NOT EXISTS fleet_sessions (
      session_id TEXT PRIMARY KEY,
      operator_id INTEGER NOT NULL,
      expires_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL DEFAULT (unixepoch()),
      FOREIGN KEY (operator_id) REFERENCES fleet_operators(id)
    );

    CREATE INDEX IF NOT EXISTS idx_fleet_ota_events_ts ON fleet_ota_events(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_fleet_cmd_created ON fleet_commands(created_at DESC);
    CREATE INDEX IF NOT EXISTS idx_fleet_sessions_exp ON fleet_sessions(expires_at);
  `)

  try {
    db.exec(`ALTER TABLE fleet_commands ADD COLUMN issued_by TEXT`)
  } catch {
    /* exists */
  }
  try {
    db.exec(`ALTER TABLE fleet_operators ADD COLUMN username TEXT`)
  } catch {
    /* exists */
  }
  try {
    db.exec(`ALTER TABLE fleet_operators ADD COLUMN password_hash TEXT`)
  } catch {
    /* exists */
  }
  try {
    db.exec(`ALTER TABLE fleet_operators ADD COLUMN token_hash TEXT`)
  } catch {
    /* exists */
  }

  // Make token nullable (legacy NOT NULL blocks hashing-at-rest)
  const opSql = (
    db.prepare(`SELECT sql FROM sqlite_master WHERE type='table' AND name='fleet_operators'`).get() as
      | { sql: string }
      | undefined
  )?.sql
  if (opSql && /token TEXT NOT NULL UNIQUE/i.test(opSql)) {
    db.exec(`
      BEGIN;
      CREATE TABLE fleet_operators_v3 (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        role TEXT NOT NULL CHECK (role IN ('viewer','dispatcher','admin')),
        username TEXT,
        password_hash TEXT,
        token_hash TEXT,
        token TEXT UNIQUE,
        created_at INTEGER NOT NULL DEFAULT (unixepoch())
      );
      INSERT INTO fleet_operators_v3 (id, name, role, username, password_hash, token_hash, token, created_at)
        SELECT id, name, role,
          COALESCE(username, NULL),
          password_hash,
          token_hash,
          token,
          created_at
        FROM fleet_operators;
      DROP TABLE fleet_operators;
      ALTER TABLE fleet_operators_v3 RENAME TO fleet_operators;
      COMMIT;
    `)
  }

  // Migrate plaintext tokens → hashed; seed passwords if missing
  const rows = db
    .prepare(
      `SELECT id, name, role, token, username, password_hash, token_hash FROM fleet_operators`,
    )
    .all() as Array<{
    id: number
    name: string
    role: FleetRole
    token: string | null
    username: string | null
    password_hash: string | null
    token_hash: string | null
  }>

  const upd = db.prepare(
    `UPDATE fleet_operators SET username = ?, password_hash = ?, token_hash = ?, token = NULL WHERE id = ?`,
  )

  if (rows.length === 0) {
    const ins = db.prepare(
      `INSERT INTO fleet_operators (name, role, username, password_hash, token_hash, token)
       VALUES (?, ?, ?, ?, ?, NULL)`,
    )
    const seeds: Array<[string, FleetRole, string, string, string]> = [
      ['Admin flota', 'admin', 'admin', 'admin123', 'fleet-admin-demo'],
      ['Despacho', 'dispatcher', 'despacho', 'dispatch123', 'fleet-dispatch-demo'],
      ['Solo lectura', 'viewer', 'viewer', 'viewer123', 'fleet-viewer-demo'],
    ]
    for (const [name, role, user, pw, apiTok] of seeds) {
      ins.run(name, role, user, hashPassword(pw), hashToken(apiTok))
    }
  } else {
    for (const r of rows) {
      let username = r.username
      let passwordHash = r.password_hash
      let tokenHash = r.token_hash
      if (!username) {
        username =
          r.role === 'admin' ? 'admin' : r.role === 'dispatcher' ? 'despacho' : 'viewer'
      }
      if (!passwordHash) {
        const pw =
          r.role === 'admin' ? 'admin123' : r.role === 'dispatcher' ? 'dispatch123' : 'viewer123'
        passwordHash = hashPassword(pw)
      }
      if (!tokenHash && r.token) {
        tokenHash = hashToken(r.token)
      }
      if (!tokenHash) {
        // preserve known demo API tokens by role
        const demo =
          r.role === 'admin'
            ? 'fleet-admin-demo'
            : r.role === 'dispatcher'
              ? 'fleet-dispatch-demo'
              : 'fleet-viewer-demo'
        tokenHash = hashToken(demo)
      }
      if (
        r.username !== username ||
        r.password_hash !== passwordHash ||
        r.token_hash !== tokenHash ||
        r.token != null
      ) {
        upd.run(username, passwordHash, tokenHash, r.id)
      }
    }
  }
}

function extractBearer(req: Request): string {
  const auth = req.headers.authorization
  if (typeof auth === 'string' && auth.toLowerCase().startsWith('bearer ')) {
    return auth.slice(7).trim()
  }
  return ''
}

export function resolveOperator(req: Request): FleetOp {
  const sessionId =
    (typeof req.headers['x-fleet-session'] === 'string' && req.headers['x-fleet-session']) ||
    extractBearer(req) ||
    (typeof req.query.session === 'string' && req.query.session) ||
    ''

  if (sessionId) {
    const now = Math.floor(Date.now() / 1000)
    const row = db
      .prepare(
        `SELECT o.id, o.name, o.role, s.session_id
         FROM fleet_sessions s
         JOIN fleet_operators o ON o.id = s.operator_id
         WHERE s.session_id = ? AND s.expires_at > ?`,
      )
      .get(sessionId, now) as { id: number; name: string; role: FleetRole; session_id: string } | undefined
    if (row) {
      return {
        id: row.id,
        name: row.name,
        role: row.role,
        token: sessionId,
        authenticated: true,
      }
    }
  }

  const apiTok =
    (typeof req.headers['x-fleet-token'] === 'string' && req.headers['x-fleet-token']) ||
    (typeof req.query.token === 'string' && req.query.token) ||
    ''
  if (apiTok) {
    const th = hashToken(apiTok)
    const row = db
      .prepare(`SELECT id, name, role FROM fleet_operators WHERE token_hash = ?`)
      .get(th) as { id: number; name: string; role: FleetRole } | undefined
    if (row) {
      return {
        id: row.id,
        name: row.name,
        role: row.role,
        token: apiTok,
        authenticated: true,
      }
    }
  }

  if (isOpenMode()) {
    return { name: 'anon', role: 'dispatcher', token: '', authenticated: false }
  }
  return { name: 'unauth', role: 'viewer', token: '', authenticated: false }
}

export function requireRole(min: FleetRole) {
  return (req: Request, res: Response, next: NextFunction) => {
    const op = resolveOperator(req)
    ;(req as Request & { fleetOp?: FleetOp }).fleetOp = op
    if (!op.authenticated && !isOpenMode()) {
      res.status(401).json({ error: 'auth requerida', hint: 'POST /api/fleet/ops/login' })
      return
    }
    if (ROLE_RANK[op.role] < ROLE_RANK[min]) {
      res.status(403).json({ error: 'rol insuficiente', role: op.role, need: min })
      return
    }
    next()
  }
}

export function canIssueCommand(role: FleetRole, command: string): boolean {
  if (!DISPATCH_CMDS.has(command) && command !== 'wipe') {
    return role === 'admin'
  }
  if (ADMIN_ONLY.has(command)) return role === 'admin'
  if (ROLE_RANK[role] < ROLE_RANK.dispatcher) return false
  return true
}

export function assertCanMutate(req: Request, res: Response, command?: string): FleetOp | null {
  const op = resolveOperator(req)
  if (!op.authenticated && !isOpenMode()) {
    res.status(401).json({ error: 'auth requerida', hint: 'login or x-fleet-token' })
    return null
  }
  if (command && !canIssueCommand(op.role, command)) {
    res.status(403).json({ error: 'rol insuficiente para este comando', role: op.role, command })
    return null
  }
  return op
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

const loginSchema = z.object({
  username: z.string().min(1).max(64),
  password: z.string().min(1).max(128),
})

fleetOpsRouter.post('/login', (req, res) => {
  const parsed = loginSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'usuario/clave requeridos' })
    return
  }
  const row = db
    .prepare(
      `SELECT id, name, role, password_hash FROM fleet_operators WHERE username = ? COLLATE NOCASE`,
    )
    .get(parsed.data.username) as
    | { id: number; name: string; role: FleetRole; password_hash: string | null }
    | undefined
  if (!row?.password_hash || !verifyPassword(parsed.data.password, row.password_hash)) {
    res.status(401).json({ error: 'credenciales inválidas' })
    return
  }
  const sessionId = newSessionId()
  const expires = Math.floor(Date.now() / 1000) + SESSION_TTL_S
  db.prepare(
    `INSERT INTO fleet_sessions (session_id, operator_id, expires_at) VALUES (?, ?, ?)`,
  ).run(sessionId, row.id, expires)
  res.json({
    ok: true,
    session: sessionId,
    expires_at: expires,
    name: row.name,
    role: row.role,
    username: parsed.data.username,
  })
})

fleetOpsRouter.post('/logout', (req, res) => {
  const op = resolveOperator(req)
  if (op.authenticated && op.token) {
    db.prepare(`DELETE FROM fleet_sessions WHERE session_id = ?`).run(op.token)
  }
  res.json({ ok: true })
})

fleetOpsRouter.get('/auth/config', (_req, res) => {
  res.json({
    open_mode: isOpenMode(),
    login_required: !isOpenMode(),
    session_ttl_s: SESSION_TTL_S,
  })
})

fleetOpsRouter.get('/operators', requireRole('admin'), (_req, res) => {
  const rows = db
    .prepare(
      `SELECT id, name, username, role, created_at,
              CASE WHEN token_hash IS NOT NULL THEN 1 ELSE 0 END AS has_api_token
       FROM fleet_operators ORDER BY id`,
    )
    .all()
  res.json({ operators: rows })
})

fleetOpsRouter.get('/me', (req, res) => {
  const op = resolveOperator(req)
  res.json({
    name: op.name,
    role: op.role,
    authenticated: op.authenticated,
    open_mode: isOpenMode(),
    can_dispatch: ROLE_RANK[op.role] >= ROLE_RANK.dispatcher && (op.authenticated || isOpenMode()),
    can_wipe: op.role === 'admin' && (op.authenticated || isOpenMode()),
    can_manage_ops: op.role === 'admin' && op.authenticated,
  })
})

const opSchema = z.object({
  name: z.string().min(1).max(80),
  username: z.string().min(2).max(64),
  role: z.enum(['viewer', 'dispatcher', 'admin']),
  password: z.string().min(6).max(128),
  token: z.string().min(8).max(64).optional(),
})

fleetOpsRouter.post('/operators', requireRole('admin'), (req, res) => {
  const parsed = opSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'payload inválido', details: parsed.error.flatten() })
    return
  }
  const apiToken = parsed.data.token || newApiToken(parsed.data.role)
  const info = db
    .prepare(
      `INSERT INTO fleet_operators (name, role, username, password_hash, token_hash, token)
       VALUES (?, ?, ?, ?, ?, NULL)`,
    )
    .run(
      parsed.data.name,
      parsed.data.role,
      parsed.data.username,
      hashPassword(parsed.data.password),
      hashToken(apiToken),
    )
  res.status(201).json({
    ok: true,
    id: Number(info.lastInsertRowid),
    token: apiToken,
    username: parsed.data.username,
  })
})

const pwSchema = z.object({
  password: z.string().min(6).max(128),
})

fleetOpsRouter.post('/password', (req, res) => {
  const op = resolveOperator(req)
  if (!op.authenticated || !op.id) {
    res.status(401).json({ error: 'auth requerida' })
    return
  }
  const parsed = pwSchema.safeParse(req.body)
  if (!parsed.success) {
    res.status(400).json({ error: 'password inválido' })
    return
  }
  db.prepare(`UPDATE fleet_operators SET password_hash = ? WHERE id = ?`).run(
    hashPassword(parsed.data.password),
    op.id,
  )
  res.json({ ok: true })
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

function csvCell(v: unknown): string {
  if (v == null) return ''
  const s = typeof v === 'number' && Number.isFinite(v) ? String(v) : String(v)
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

function toCsv(headers: string[], rows: Array<Array<unknown>>): string {
  const bom = '\uFEFF'
  const lines = [
    headers.map(csvCell).join(','),
    ...rows.map((r) => r.map(csvCell).join(',')),
  ]
  return bom + lines.join('\n') + '\n'
}

function sendCsv(res: Response, filename: string, headers: string[], rows: Array<Array<unknown>>) {
  const body = toCsv(headers, rows)
  res.setHeader('Content-Type', 'text/csv; charset=utf-8')
  res.setHeader('Content-Disposition', `attachment; filename="${filename}"`)
  res.setHeader('Cache-Control', 'no-store')
  res.send(body)
}

/** CSV export: kind=devices|commands|alerts|telemetry|summary */
fleetOpsRouter.get('/reports/export', (req, res) => {
  const op = resolveOperator(req)
  if (!isOpenMode() && !op.authenticated) {
    res.status(401).json({ error: 'auth requerida' })
    return
  }
  const kind = String(req.query.kind || 'devices').toLowerCase()
  const limit = Math.min(5_000, Math.max(1, Number(req.query.limit) || 1_000))
  const deviceId = typeof req.query.device_id === 'string' ? req.query.device_id : null
  const now = Math.floor(Date.now() / 1000)
  const day = now - 86_400
  const stamp = new Date().toISOString().slice(0, 10)

  if (kind === 'devices') {
    const rows = db
      .prepare(
        `SELECT device_id, name, pair_code, app_version, version_code, status,
                last_seen_at, last_lat, last_lng, last_speed_mps, reverse
         FROM fleet_devices ORDER BY last_seen_at DESC LIMIT ?`,
      )
      .all(limit) as Array<Record<string, unknown>>
    sendCsv(
      res,
      `fleet-devices-${stamp}.csv`,
      [
        'device_id',
        'name',
        'pair_code',
        'app_version',
        'version_code',
        'status',
        'last_seen_at',
        'last_lat',
        'last_lng',
        'last_speed_kmh',
        'reverse',
      ],
      rows.map((r) => [
        r.device_id,
        r.name,
        r.pair_code,
        r.app_version,
        r.version_code,
        r.status,
        r.last_seen_at,
        r.last_lat,
        r.last_lng,
        r.last_speed_mps != null ? Number(r.last_speed_mps) * 3.6 : '',
        r.reverse,
      ]),
    )
    return
  }

  if (kind === 'commands') {
    let sql = `SELECT id, device_id, command, status, created_at, acked_at, issued_by, payload
               FROM fleet_commands`
    const params: unknown[] = []
    if (deviceId) {
      sql += ` WHERE device_id = ?`
      params.push(deviceId)
    }
    sql += ` ORDER BY id DESC LIMIT ?`
    params.push(limit)
    const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
    sendCsv(
      res,
      `fleet-commands-${stamp}.csv`,
      ['id', 'device_id', 'command', 'status', 'created_at', 'acked_at', 'issued_by', 'payload'],
      rows.map((r) => [
        r.id,
        r.device_id,
        r.command,
        r.status,
        r.created_at,
        r.acked_at,
        r.issued_by,
        typeof r.payload === 'string' ? r.payload : '',
      ]),
    )
    return
  }

  if (kind === 'alerts') {
    let sql = `SELECT id, device_id, kind, severity, message, created_at, acked_at
               FROM fleet_alerts`
    const params: unknown[] = []
    if (deviceId) {
      sql += ` WHERE device_id = ?`
      params.push(deviceId)
    }
    sql += ` ORDER BY id DESC LIMIT ?`
    params.push(limit)
    const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
    sendCsv(
      res,
      `fleet-alerts-${stamp}.csv`,
      ['id', 'device_id', 'kind', 'severity', 'message', 'created_at', 'acked_at', 'open'],
      rows.map((r) => [
        r.id,
        r.device_id,
        r.kind,
        r.severity,
        r.message,
        r.created_at,
        r.acked_at,
        r.acked_at == null ? 1 : 0,
      ]),
    )
    return
  }

  if (kind === 'telemetry') {
    let sql = `SELECT id, device_id, ts, lat, lng, speed_mps FROM fleet_telemetry`
    const params: unknown[] = []
    if (deviceId) {
      sql += ` WHERE device_id = ? AND ts >= ?`
      params.push(deviceId, day)
    } else {
      sql += ` WHERE ts >= ?`
      params.push(day)
    }
    sql += ` ORDER BY ts DESC LIMIT ?`
    params.push(limit)
    const rows = db.prepare(sql).all(...params) as Array<Record<string, unknown>>
    sendCsv(
      res,
      `fleet-telemetry-${stamp}.csv`,
      ['id', 'device_id', 'ts', 'lat', 'lng', 'speed_kmh'],
      rows.map((r) => [
        r.id,
        r.device_id,
        r.ts,
        r.lat,
        r.lng,
        r.speed_mps != null ? Number(r.speed_mps) * 3.6 : '',
      ]),
    )
    return
  }

  if (kind === 'summary') {
    const devices = db.prepare(`SELECT COUNT(*) AS n FROM fleet_devices`).get() as { n: number }
    const online = db
      .prepare(`SELECT COUNT(*) AS n FROM fleet_devices WHERE last_seen_at >= ?`)
      .get(now - 120) as { n: number }
    const openAlerts = db
      .prepare(`SELECT COUNT(*) AS n FROM fleet_alerts WHERE acked_at IS NULL`)
      .get() as { n: number }
    const cmds = db
      .prepare(
        `SELECT status, COUNT(*) AS n FROM fleet_commands WHERE created_at >= ? GROUP BY status`,
      )
      .all(day) as Array<{ status: string; n: number }>
    const versions = db
      .prepare(
        `SELECT COALESCE(app_version,'?') AS app_version, COALESCE(version_code,0) AS version_code, COUNT(*) AS n
         FROM fleet_devices GROUP BY app_version, version_code`,
      )
      .all() as Array<{ app_version: string; version_code: number; n: number }>
    const speed = db
      .prepare(
        `SELECT AVG(speed_mps) AS avg_mps, MAX(speed_mps) AS max_mps, COUNT(*) AS samples
         FROM fleet_telemetry WHERE ts >= ? AND speed_mps IS NOT NULL`,
      )
      .get(day) as { avg_mps: number | null; max_mps: number | null; samples: number }

    const rows: Array<Array<unknown>> = [
      ['generated_at', now, ''],
      ['window_s', 86_400, ''],
      ['devices_total', devices.n, ''],
      ['devices_online', online.n, ''],
      ['devices_stale', devices.n - online.n, ''],
      ['open_alerts', openAlerts.n, ''],
      [
        'telem_avg_kmh',
        speed.avg_mps != null ? +(speed.avg_mps * 3.6).toFixed(2) : '',
        '',
      ],
      [
        'telem_max_kmh',
        speed.max_mps != null ? +(speed.max_mps * 3.6).toFixed(2) : '',
        '',
      ],
      ['telem_samples_24h', speed.samples, ''],
    ]
    for (const c of cmds) rows.push([`cmds_24h_${c.status}`, c.n, ''])
    for (const v of versions) {
      rows.push([`version_${v.app_version}`, v.version_code, v.n])
    }
    sendCsv(res, `fleet-summary-${stamp}.csv`, ['metric', 'value', 'extra'], rows)
    return
  }

  if (kind === 'drivers') {
    const rows = db
      .prepare(
        `SELECT d.id, d.code, d.name, d.phone, d.language, d.preferred_dest, d.active, d.created_at,
                (SELECT COUNT(*) FROM fleet_devices fd WHERE fd.driver_id = d.id) AS devices
         FROM fleet_drivers d ORDER BY d.code LIMIT ?`,
      )
      .all(limit) as Array<Record<string, unknown>>
    sendCsv(
      res,
      `fleet-drivers-${stamp}.csv`,
      [
        'id',
        'code',
        'name',
        'phone',
        'language',
        'preferred_dest',
        'active',
        'devices',
        'created_at',
      ],
      rows.map((r) => [
        r.id,
        r.code,
        r.name,
        r.phone,
        r.language,
        r.preferred_dest,
        r.active,
        r.devices,
        r.created_at,
      ]),
    )
    return
  }

  if (kind === 'shifts') {
    const rows = db
      .prepare(
        `SELECT s.id, s.device_id, s.driver_id, d.code AS driver_code, d.name AS driver_name,
                s.started_at, s.ended_at, s.start_odo_km, s.end_odo_km, s.distance_km, s.status,
                s.idle_sec, s.overspeed_sec, s.abs_events, s.high_throttle_sec, s.eco_score, s.eco_band
         FROM fleet_shifts s
         LEFT JOIN fleet_drivers d ON d.id = s.driver_id
         ORDER BY s.id DESC LIMIT ?`,
      )
      .all(limit) as Array<Record<string, unknown>>
    sendCsv(
      res,
      `fleet-shifts-${stamp}.csv`,
      [
        'id',
        'device_id',
        'driver_id',
        'driver_code',
        'driver_name',
        'started_at',
        'ended_at',
        'start_odo_km',
        'end_odo_km',
        'distance_km',
        'status',
        'idle_sec',
        'overspeed_sec',
        'abs_events',
        'high_throttle_sec',
        'eco_score',
        'eco_band',
      ],
      rows.map((r) => [
        r.id,
        r.device_id,
        r.driver_id,
        r.driver_code,
        r.driver_name,
        r.started_at,
        r.ended_at,
        r.start_odo_km,
        r.end_odo_km,
        r.distance_km,
        r.status,
        r.idle_sec,
        r.overspeed_sec,
        r.abs_events,
        r.high_throttle_sec,
        r.eco_score,
        r.eco_band,
      ]),
    )
    return
  }

  if (kind === 'maintenance') {
    const rows = db
      .prepare(
        `SELECT m.id, m.device_id, m.kind, m.label, m.interval_km, m.last_service_odo_km,
                m.warn_km, m.enabled, m.updated_at, fd.telemetry_json
         FROM fleet_maintenance m
         LEFT JOIN fleet_devices fd ON fd.device_id = m.device_id
         ORDER BY m.device_id, m.kind LIMIT ?`,
      )
      .all(limit) as Array<Record<string, unknown>>
    sendCsv(
      res,
      `fleet-maintenance-${stamp}.csv`,
      [
        'id',
        'device_id',
        'kind',
        'label',
        'interval_km',
        'last_service_odo_km',
        'warn_km',
        'enabled',
        'odo_km',
        'remaining_km',
        'band',
        'updated_at',
      ],
      rows.map((r) => {
        let odo: number | null = null
        if (typeof r.telemetry_json === 'string' && r.telemetry_json) {
          try {
            const j = JSON.parse(r.telemetry_json) as Record<string, unknown>
            if (typeof j.odometer_km === 'number') odo = j.odometer_km
          } catch {
            /* ignore */
          }
        }
        const last = Number(r.last_service_odo_km ?? 0)
        const interval = Number(r.interval_km ?? 0)
        const warn = Number(r.warn_km ?? 500)
        const dueAt = last + interval
        const remaining = odo != null ? dueAt - odo : null
        let band = 'ok'
        if (!Number(r.enabled)) band = 'off'
        else if (remaining != null && remaining <= 0) band = 'due'
        else if (remaining != null && remaining <= warn) band = 'warn'
        return [
          r.id,
          r.device_id,
          r.kind,
          r.label,
          r.interval_km,
          r.last_service_odo_km,
          r.warn_km,
          r.enabled,
          odo,
          remaining,
          band,
          r.updated_at,
        ]
      }),
    )
    return
  }

  res.status(400).json({
    error: 'kind inválido',
    kinds: [
      'devices',
      'commands',
      'alerts',
      'telemetry',
      'summary',
      'drivers',
      'shifts',
      'maintenance',
    ],
  })
})

/** Live fleet map payload: units + geofences + optional trails. */
fleetOpsRouter.get('/map', requireRole('viewer'), (req, res) => {
  const trailLimit = Math.min(100, Math.max(0, Number(req.query.trail) || 40))
  const now = Math.floor(Date.now() / 1000)
  const staleSec = Math.min(900, Math.max(30, Number(req.query.stale_sec) || 180))

  const rows = db
    .prepare(
      `SELECT fd.device_id, fd.name, fd.pair_code, fd.app_version, fd.version_code,
              fd.last_seen_at, fd.last_lat, fd.last_lng, fd.last_speed_mps, fd.reverse,
              fd.status, fd.telemetry_json, fd.driver_id,
              dr.code AS driver_code, dr.name AS driver_name
       FROM fleet_devices fd
       LEFT JOIN fleet_drivers dr ON dr.id = fd.driver_id
       ORDER BY fd.last_seen_at DESC`,
    )
    .all() as Array<Record<string, unknown>>

  const panicRows = db
    .prepare(
      `SELECT device_id, id, message, created_at FROM fleet_alerts
       WHERE kind = 'panic' AND acked_at IS NULL`,
    )
    .all() as Array<{ device_id: string; id: number; message: string; created_at: number }>
  const panicByDevice = new Map(panicRows.map((p) => [p.device_id, p]))

  const trailStmt =
    trailLimit > 0
      ? db.prepare(
          `SELECT lat, lng, ts, speed_mps FROM fleet_telemetry
           WHERE device_id = ? AND lat IS NOT NULL AND lng IS NOT NULL
           ORDER BY ts DESC LIMIT ?`,
        )
      : null

  const units = rows.map((r) => {
    const deviceId = String(r.device_id)
    const lat = r.last_lat != null ? Number(r.last_lat) : null
    const lng = r.last_lng != null ? Number(r.last_lng) : null
    const seen = r.last_seen_at != null ? Number(r.last_seen_at) : null
    const online = seen != null && now - seen <= staleSec
    const panic = panicByDevice.get(deviceId)
    let trail: Array<{ lat: number; lng: number; ts: number; speed_kmh: number | null }> = []
    if (trailStmt && lat != null && lng != null) {
      const samples = trailStmt.all(deviceId, trailLimit) as Array<{
        lat: number
        lng: number
        ts: number
        speed_mps: number | null
      }>
      trail = samples
        .reverse()
        .map((s) => ({
          lat: s.lat,
          lng: s.lng,
          ts: s.ts,
          speed_kmh: s.speed_mps != null ? s.speed_mps * 3.6 : null,
        }))
    }
    return {
      device_id: deviceId,
      name: r.name,
      pair_code: r.pair_code,
      app_version: r.app_version,
      version_code: r.version_code,
      last_seen_at: seen,
      lat,
      lng,
      speed_kmh: r.last_speed_mps != null ? Number(r.last_speed_mps) * 3.6 : null,
      reverse: Number(r.reverse) === 1,
      status: r.status,
      online,
      driver_code: r.driver_code ?? null,
      driver_name: r.driver_name ?? null,
      panic: panic
        ? { id: panic.id, message: panic.message, created_at: panic.created_at }
        : null,
      trail,
    }
  })

  const geofences = db
    .prepare(
      `SELECT id, name, lat, lng, radius_m, max_kmh, active FROM fleet_geofences WHERE active = 1 ORDER BY id`,
    )
    .all()

  const withPos = units.filter((u) => u.lat != null && u.lng != null)
  res.json({
    ok: true,
    server_time: now,
    stale_sec: staleSec,
    counts: {
      units: units.length,
      located: withPos.length,
      online: units.filter((u) => u.online).length,
      panic: panicRows.length,
    },
    units,
    geofences,
  })
})

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return { text: raw }
  }
}
