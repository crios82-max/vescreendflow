import { Router } from 'express'
import { z } from 'zod'
import { query } from '../db/pool.js'
import { requireAuth } from '../middleware/auth.js'

export const groupsRouter = Router()

groupsRouter.use(requireAuth)

async function ownedGroup(groupId: string, userId: string) {
  const result = await query(`SELECT id FROM screen_groups WHERE id = $1 AND user_id = $2`, [
    groupId,
    userId,
  ])
  return Boolean(result.rowCount)
}

async function propagatePlaylist(groupId: string, playlistId: string | null, userId: string) {
  const members = await query<{ screen_id: string }>(
    `SELECT m.screen_id
     FROM screen_group_members m
     JOIN screens s ON s.id = m.screen_id
     WHERE m.group_id = $1 AND s.user_id = $2`,
    [groupId, userId],
  )

  for (const row of members.rows) {
    await query(`DELETE FROM screen_playlists WHERE screen_id = $1`, [row.screen_id])
    if (playlistId) {
      await query(
        `INSERT INTO screen_playlists (screen_id, playlist_id) VALUES ($1, $2)
         ON CONFLICT DO NOTHING`,
        [row.screen_id, playlistId],
      )
    }
  }
}

groupsRouter.get('/', async (req, res) => {
  const groups = await query<{
    id: string
    name: string
    mode: 'group' | 'videowall'
    rows: number
    cols: number
    playlist_id: string | null
    playlist_name: string | null
    cycle_epoch: string
  }>(
    `SELECT g.id, g.name, g.mode, g.rows, g.cols, g.playlist_id, g.cycle_epoch,
            p.name AS playlist_name
     FROM screen_groups g
     LEFT JOIN playlists p ON p.id = g.playlist_id
     WHERE g.user_id = $1
     ORDER BY g.created_at ASC`,
    [req.user!.id],
  )

  const members = await query<{
    group_id: string
    screen_id: string
    row_index: number
    col_index: number
    screen_name: string
    pair_code: string
  }>(
    `SELECT m.group_id, m.screen_id, m.row_index, m.col_index, s.name AS screen_name, s.pair_code
     FROM screen_group_members m
     JOIN screen_groups g ON g.id = m.group_id
     JOIN screens s ON s.id = m.screen_id
     WHERE g.user_id = $1`,
    [req.user!.id],
  )

  const byGroup = new Map<string, typeof members.rows>()
  for (const row of members.rows) {
    const list = byGroup.get(row.group_id) || []
    list.push(row)
    byGroup.set(row.group_id, list)
  }

  return res.json({
    groups: groups.rows.map((g) => ({
      id: g.id,
      name: g.name,
      mode: g.mode,
      rows: g.rows,
      cols: g.cols,
      playlistId: g.playlist_id,
      playlistName: g.playlist_name,
      cycleEpoch: g.cycle_epoch,
      members: (byGroup.get(g.id) || []).map((m) => ({
        screenId: m.screen_id,
        screenName: m.screen_name,
        code: m.pair_code,
        row: m.row_index,
        col: m.col_index,
      })),
    })),
  })
})

const createSchema = z.object({
  name: z.string().min(1).max(120),
  mode: z.enum(['group', 'videowall']).default('group'),
  rows: z.number().int().min(1).max(8).default(1),
  cols: z.number().int().min(1).max(8).default(1),
  playlistId: z.string().uuid().nullable().optional(),
})

groupsRouter.post('/', async (req, res) => {
  const parsed = createSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos de grupo inválidos' })
  }

  const { name, mode, rows, cols, playlistId } = parsed.data
  if (playlistId) {
    const pl = await query(`SELECT id FROM playlists WHERE id = $1 AND user_id = $2`, [
      playlistId,
      req.user!.id,
    ])
    if (!pl.rowCount) return res.status(404).json({ error: 'Playlist no encontrada' })
  }

  const result = await query<{
    id: string
    name: string
    mode: 'group' | 'videowall'
    rows: number
    cols: number
    playlist_id: string | null
    cycle_epoch: string
  }>(
    `INSERT INTO screen_groups (user_id, name, mode, rows, cols, playlist_id)
     VALUES ($1, $2, $3, $4, $5, $6)
     RETURNING id, name, mode, rows, cols, playlist_id, cycle_epoch`,
    [req.user!.id, name.trim(), mode, rows, cols, playlistId || null],
  )

  const g = result.rows[0]
  return res.status(201).json({
    group: {
      id: g.id,
      name: g.name,
      mode: g.mode,
      rows: g.rows,
      cols: g.cols,
      playlistId: g.playlist_id,
      cycleEpoch: g.cycle_epoch,
      members: [],
    },
  })
})

const patchSchema = z.object({
  name: z.string().min(1).max(120).optional(),
  mode: z.enum(['group', 'videowall']).optional(),
  rows: z.number().int().min(1).max(8).optional(),
  cols: z.number().int().min(1).max(8).optional(),
  playlistId: z.string().uuid().nullable().optional(),
  resetCycle: z.boolean().optional(),
})

groupsRouter.patch('/:id', async (req, res) => {
  const parsed = patchSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos inválidos' })
  }
  if (!(await ownedGroup(req.params.id, req.user!.id))) {
    return res.status(404).json({ error: 'Grupo no encontrado' })
  }

  const current = await query<{
    name: string
    mode: 'group' | 'videowall'
    rows: number
    cols: number
    playlist_id: string | null
  }>(`SELECT name, mode, rows, cols, playlist_id FROM screen_groups WHERE id = $1`, [
    req.params.id,
  ])

  const cur = current.rows[0]
  const next = {
    name: parsed.data.name?.trim() || cur.name,
    mode: parsed.data.mode || cur.mode,
    rows: parsed.data.rows ?? cur.rows,
    cols: parsed.data.cols ?? cur.cols,
    playlistId:
      parsed.data.playlistId === undefined ? cur.playlist_id : parsed.data.playlistId,
  }

  if (next.playlistId) {
    const pl = await query(`SELECT id FROM playlists WHERE id = $1 AND user_id = $2`, [
      next.playlistId,
      req.user!.id,
    ])
    if (!pl.rowCount) return res.status(404).json({ error: 'Playlist no encontrada' })
  }

  const result = await query<{
    id: string
    name: string
    mode: 'group' | 'videowall'
    rows: number
    cols: number
    playlist_id: string | null
    cycle_epoch: string
  }>(
    `UPDATE screen_groups
     SET name = $1, mode = $2, rows = $3, cols = $4, playlist_id = $5,
         cycle_epoch = CASE WHEN $6 THEN NOW() ELSE cycle_epoch END,
         updated_at = NOW()
     WHERE id = $7
     RETURNING id, name, mode, rows, cols, playlist_id, cycle_epoch`,
    [
      next.name,
      next.mode,
      next.rows,
      next.cols,
      next.playlistId,
      Boolean(parsed.data.resetCycle),
      req.params.id,
    ],
  )

  if (parsed.data.playlistId !== undefined) {
    await propagatePlaylist(req.params.id, next.playlistId, req.user!.id)
  }

  const g = result.rows[0]
  return res.json({
    group: {
      id: g.id,
      name: g.name,
      mode: g.mode,
      rows: g.rows,
      cols: g.cols,
      playlistId: g.playlist_id,
      cycleEpoch: g.cycle_epoch,
    },
  })
})

const membersSchema = z.object({
  members: z.array(
    z.object({
      screenId: z.string().uuid(),
      row: z.number().int().min(0).max(7),
      col: z.number().int().min(0).max(7),
    }),
  ),
})

groupsRouter.put('/:id/members', async (req, res) => {
  const parsed = membersSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos de miembros inválidos' })
  }
  if (!(await ownedGroup(req.params.id, req.user!.id))) {
    return res.status(404).json({ error: 'Grupo no encontrado' })
  }

  const group = await query<{
    rows: number
    cols: number
    playlist_id: string | null
  }>(`SELECT rows, cols, playlist_id FROM screen_groups WHERE id = $1`, [req.params.id])
  const g = group.rows[0]

  for (const m of parsed.data.members) {
    if (m.row >= g.rows || m.col >= g.cols) {
      return res.status(400).json({ error: `Celda fuera de layout (${g.rows}x${g.cols})` })
    }
    const owned = await query(`SELECT id FROM screens WHERE id = $1 AND user_id = $2`, [
      m.screenId,
      req.user!.id,
    ])
    if (!owned.rowCount) {
      return res.status(404).json({ error: 'Pantalla no encontrada' })
    }
  }

  await query(`DELETE FROM screen_group_members WHERE group_id = $1`, [req.params.id])

  for (const m of parsed.data.members) {
    await query(`DELETE FROM screen_group_members WHERE screen_id = $1`, [m.screenId])
    await query(
      `INSERT INTO screen_group_members (group_id, screen_id, row_index, col_index)
       VALUES ($1, $2, $3, $4)`,
      [req.params.id, m.screenId, m.row, m.col],
    )
  }

  await propagatePlaylist(req.params.id, g.playlist_id, req.user!.id)

  return res.json({ ok: true, count: parsed.data.members.length })
})

groupsRouter.delete('/:id', async (req, res) => {
  const result = await query(`DELETE FROM screen_groups WHERE id = $1 AND user_id = $2`, [
    req.params.id,
    req.user!.id,
  ])
  if (!result.rowCount) {
    return res.status(404).json({ error: 'Grupo no encontrado' })
  }
  return res.status(204).send()
})
