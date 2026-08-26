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
  'nav_dest',
  'lock_task',
  'apply_kiosk',
  'run_diag',
  'set_dbc',
  'fm_tune',
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

function safeJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return { text: raw }
  }
}
