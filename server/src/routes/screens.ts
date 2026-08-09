import { Router } from 'express'
import { z } from 'zod'
import { query } from '../db/pool.js'
import { requireAuth } from '../middleware/auth.js'

export const screensRouter = Router()

screensRouter.use(requireAuth)

const timeHm = z
  .string()
  .regex(/^([01]\d|2[0-3]):[0-5]\d$/)
  .nullable()
  .optional()

function offlineIfStale(status: 'online' | 'offline', lastSeen: string | null) {
  if (!lastSeen) return 'offline' as const
  const age = Date.now() - new Date(lastSeen).getTime()
  if (age > 90_000) return 'offline' as const
  return status
}

screensRouter.get('/', async (req, res) => {
  const result = await query<{
    id: string
    name: string
    pair_code: string
    status: 'online' | 'offline'
    location: string | null
    last_seen_at: string | null
    rotation_deg: number
    playlist: string | null
    playlist_id: string | null
    daypart_start: string | null
    daypart_end: string | null
  }>(
    `SELECT s.id, s.name, s.pair_code, s.status, s.location, s.last_seen_at, s.rotation_deg,
            COALESCE(sp_info.name, 'Sin asignar') AS playlist,
            sp_info.playlist_id,
            sp_info.daypart_start::text,
            sp_info.daypart_end::text
     FROM screens s
     LEFT JOIN LATERAL (
       SELECT p.id AS playlist_id, p.name, sp.daypart_start, sp.daypart_end
       FROM screen_playlists sp
       JOIN playlists p ON p.id = sp.playlist_id
       WHERE sp.screen_id = s.id
       ORDER BY p.created_at ASC
       LIMIT 1
     ) sp_info ON TRUE
     WHERE s.user_id = $1
     ORDER BY s.created_at ASC`,
    [req.user!.id],
  )

  return res.json({
    screens: result.rows.map((row) => ({
      id: row.id,
      name: row.name,
      code: row.pair_code,
      status: offlineIfStale(row.status, row.last_seen_at),
      location: row.location,
      lastSeenAt: row.last_seen_at,
      rotationDeg: row.rotation_deg || 0,
      playlist: row.playlist,
      playlistId: row.playlist_id,
      daypartStart: row.daypart_start ? String(row.daypart_start).slice(0, 5) : null,
      daypartEnd: row.daypart_end ? String(row.daypart_end).slice(0, 5) : null,
    })),
  })
})

const createSchema = z.object({
  name: z.string().min(1).optional(),
  code: z.string().min(6).max(8),
  location: z.string().optional(),
  playlistId: z.string().uuid().optional(),
})

screensRouter.post('/', async (req, res) => {
  const parsed = createSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos de pantalla inválidos' })
  }

  const code = parsed.data.code.trim()
  const count = await query<{ count: string }>(
    'SELECT COUNT(*)::text AS count FROM screens WHERE user_id = $1',
    [req.user!.id],
  )
  const nextIndex = Number(count.rows[0].count) + 1
  const name = parsed.data.name?.trim() || `Pantalla ${nextIndex}`

  try {
    const result = await query<{
      id: string
      name: string
      pair_code: string
      status: 'online' | 'offline'
      last_seen_at: string
      rotation_deg: number
    }>(
      `INSERT INTO screens (user_id, name, pair_code, status, location, last_seen_at)
       VALUES ($1, $2, $3, 'online', $4, NOW())
       RETURNING id, name, pair_code, status, last_seen_at, rotation_deg`,
      [req.user!.id, name, code, parsed.data.location || null],
    )

    const screen = result.rows[0]

    const playlist = await query<{ id: string; name: string }>(
      `SELECT id, name FROM playlists
       WHERE user_id = $1 AND ($2::uuid IS NULL OR id = $2)
       ORDER BY CASE WHEN id = $2 THEN 0 ELSE 1 END, created_at ASC
       LIMIT 1`,
      [req.user!.id, parsed.data.playlistId || null],
    )

    if (!playlist.rows[0] && parsed.data.playlistId) {
      return res.status(404).json({ error: 'Playlist no encontrada' })
    }

    if (playlist.rows[0]) {
      await query(
        `INSERT INTO screen_playlists (screen_id, playlist_id)
         VALUES ($1, $2)
         ON CONFLICT DO NOTHING`,
        [screen.id, playlist.rows[0].id],
      )
    }

    return res.status(201).json({
      screen: {
        id: screen.id,
        name: screen.name,
        code: screen.pair_code,
        status: screen.status,
        lastSeenAt: screen.last_seen_at,
        rotationDeg: screen.rotation_deg || 0,
        playlist: playlist.rows[0]?.name || 'Sin asignar',
        playlistId: playlist.rows[0]?.id || null,
        daypartStart: null,
        daypartEnd: null,
      },
    })
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : ''
    if (message.includes('screens_pair_code_key')) {
      return res.status(409).json({ error: 'Este código de pantalla ya está en uso' })
    }
    throw err
  }
})

const assignSchema = z.object({
  playlistId: z.string().uuid().nullable(),
  daypartStart: timeHm,
  daypartEnd: timeHm,
})

screensRouter.put('/:id/playlist', async (req, res) => {
  const parsed = assignSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos inválidos' })
  }

  const screen = await query<{ id: string }>(
    `SELECT id FROM screens WHERE id = $1 AND user_id = $2`,
    [req.params.id, req.user!.id],
  )
  if (!screen.rowCount) {
    return res.status(404).json({ error: 'Pantalla no encontrada' })
  }

  await query(`DELETE FROM screen_playlists WHERE screen_id = $1`, [req.params.id])

  if (!parsed.data.playlistId) {
    return res.json({
      screen: {
        id: req.params.id,
        playlist: 'Sin asignar',
        playlistId: null,
        daypartStart: null,
        daypartEnd: null,
      },
    })
  }

  const playlist = await query<{ id: string; name: string }>(
    `SELECT id, name FROM playlists WHERE id = $1 AND user_id = $2`,
    [parsed.data.playlistId, req.user!.id],
  )
  if (!playlist.rowCount) {
    return res.status(404).json({ error: 'Playlist no encontrada' })
  }

  const start = parsed.data.daypartStart ?? null
  const end = parsed.data.daypartEnd ?? null

  await query(
    `INSERT INTO screen_playlists (screen_id, playlist_id, daypart_start, daypart_end)
     VALUES ($1, $2, $3::time, $4::time)`,
    [req.params.id, playlist.rows[0].id, start, end],
  )

  return res.json({
    screen: {
      id: req.params.id,
      playlist: playlist.rows[0].name,
      playlistId: playlist.rows[0].id,
      daypartStart: start,
      daypartEnd: end,
    },
  })
})

const updateSchema = z.object({
  name: z.string().min(1).max(120).optional(),
  location: z.string().max(160).nullable().optional(),
  rotationDeg: z.union([z.literal(0), z.literal(90), z.literal(180), z.literal(270)]).optional(),
})

screensRouter.patch('/:id', async (req, res) => {
  const parsed = updateSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos inválidos' })
  }

  const owned = await query<{
    id: string
    name: string
    location: string | null
    rotation_deg: number
  }>(`SELECT id, name, location, rotation_deg FROM screens WHERE id = $1 AND user_id = $2`, [
    req.params.id,
    req.user!.id,
  ])
  if (!owned.rowCount) {
    return res.status(404).json({ error: 'Pantalla no encontrada' })
  }

  const nextName = parsed.data.name?.trim() || owned.rows[0].name
  const nextLocation =
    parsed.data.location === undefined ? owned.rows[0].location : parsed.data.location
  const nextRotation =
    parsed.data.rotationDeg === undefined
      ? owned.rows[0].rotation_deg
      : parsed.data.rotationDeg

  const result = await query<{
    id: string
    name: string
    pair_code: string
    status: 'online' | 'offline'
    location: string | null
    last_seen_at: string | null
    rotation_deg: number
  }>(
    `UPDATE screens
     SET name = $1, location = $2, rotation_deg = $3, updated_at = NOW()
     WHERE id = $4 AND user_id = $5
     RETURNING id, name, pair_code, status, location, last_seen_at, rotation_deg`,
    [nextName, nextLocation, nextRotation, req.params.id, req.user!.id],
  )

  const row = result.rows[0]
  return res.json({
    screen: {
      id: row.id,
      name: row.name,
      code: row.pair_code,
      status: offlineIfStale(row.status, row.last_seen_at),
      location: row.location,
      lastSeenAt: row.last_seen_at,
      rotationDeg: row.rotation_deg,
    },
  })
})

screensRouter.delete('/:id', async (req, res) => {
  const result = await query(
    'DELETE FROM screens WHERE id = $1 AND user_id = $2',
    [req.params.id, req.user!.id],
  )
  if (!result.rowCount) {
    return res.status(404).json({ error: 'Pantalla no encontrada' })
  }
  return res.status(204).send()
})
