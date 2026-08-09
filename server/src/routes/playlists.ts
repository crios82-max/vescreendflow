import { Router } from 'express'
import { z } from 'zod'
import { query } from '../db/pool.js'
import { requireAuth } from '../middleware/auth.js'

export const playlistsRouter = Router()

playlistsRouter.use(requireAuth)

playlistsRouter.get('/', async (req, res) => {
  const result = await query<{
    id: string
    name: string
    is_active: boolean
    items: string
  }>(
    `SELECT p.id, p.name, p.is_active,
            (SELECT COUNT(*)::text FROM playlist_items pi WHERE pi.playlist_id = p.id) AS items
     FROM playlists p
     WHERE p.user_id = $1
     ORDER BY p.created_at ASC`,
    [req.user!.id],
  )

  return res.json({
    playlists: result.rows.map((row) => ({
      id: row.id,
      name: row.name,
      isActive: row.is_active,
      items: Number(row.items),
    })),
  })
})

const createSchema = z.object({
  name: z.string().min(1),
})

playlistsRouter.post('/', async (req, res) => {
  const parsed = createSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos de playlist inválidos' })
  }

  const result = await query<{ id: string; name: string; is_active: boolean }>(
    `INSERT INTO playlists (user_id, name)
     VALUES ($1, $2)
     RETURNING id, name, is_active`,
    [req.user!.id, parsed.data.name.trim()],
  )

  const playlist = result.rows[0]
  return res.status(201).json({
    playlist: {
      id: playlist.id,
      name: playlist.name,
      isActive: playlist.is_active,
      items: 0,
    },
  })
})

playlistsRouter.delete('/:id', async (req, res) => {
  const result = await query(
    'DELETE FROM playlists WHERE id = $1 AND user_id = $2',
    [req.params.id, req.user!.id],
  )
  if (!result.rowCount) {
    return res.status(404).json({ error: 'Playlist no encontrada' })
  }
  return res.status(204).send()
})

async function ownedPlaylist(playlistId: string, userId: string) {
  const result = await query(`SELECT id FROM playlists WHERE id = $1 AND user_id = $2`, [
    playlistId,
    userId,
  ])
  return Boolean(result.rowCount)
}

playlistsRouter.get('/:id/items', async (req, res) => {
  if (!(await ownedPlaylist(req.params.id, req.user!.id))) {
    return res.status(404).json({ error: 'Playlist no encontrada' })
  }

  const result = await query<{
    item_id: string
    media_id: string
    name: string
    media_type: 'image' | 'video'
    url: string
    duration_sec: number | null
    media_duration: number
    sort_order: number
  }>(
    `SELECT pi.id AS item_id, m.id AS media_id, m.name, m.media_type, m.url,
            pi.duration_sec, m.duration_sec AS media_duration, pi.sort_order
     FROM playlist_items pi
     JOIN media_assets m ON m.id = pi.media_id
     WHERE pi.playlist_id = $1
     ORDER BY pi.sort_order ASC`,
    [req.params.id],
  )

  return res.json({
    items: result.rows.map((row) => ({
      id: row.item_id,
      mediaId: row.media_id,
      name: row.name,
      mediaType: row.media_type,
      url: row.url,
      durationSec: row.duration_sec || row.media_duration || 10,
      sortOrder: row.sort_order,
    })),
  })
})

const addItemSchema = z.object({
  mediaId: z.string().uuid(),
  durationSec: z.number().int().positive().optional(),
})

playlistsRouter.post('/:id/items', async (req, res) => {
  const parsed = addItemSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos inválidos' })
  }

  if (!(await ownedPlaylist(req.params.id, req.user!.id))) {
    return res.status(404).json({ error: 'Playlist no encontrada' })
  }

  const media = await query<{ id: string; duration_sec: number }>(
    `SELECT id, duration_sec FROM media_assets WHERE id = $1 AND user_id = $2`,
    [parsed.data.mediaId, req.user!.id],
  )
  if (!media.rowCount) {
    return res.status(404).json({ error: 'Medio no encontrado' })
  }

  const existing = await query(
    `SELECT id FROM playlist_items WHERE playlist_id = $1 AND media_id = $2 LIMIT 1`,
    [req.params.id, parsed.data.mediaId],
  )
  if (existing.rowCount) {
    return res.status(409).json({ error: 'Ese archivo ya está en la playlist' })
  }

  const order = await query<{ n: string }>(
    `SELECT COALESCE(MAX(sort_order), -1)::text AS n FROM playlist_items WHERE playlist_id = $1`,
    [req.params.id],
  )

  const duration = parsed.data.durationSec || media.rows[0].duration_sec
  const inserted = await query<{
    id: string
    sort_order: number
  }>(
    `INSERT INTO playlist_items (playlist_id, media_id, sort_order, duration_sec)
     VALUES ($1, $2, $3, $4)
     RETURNING id, sort_order`,
    [req.params.id, parsed.data.mediaId, Number(order.rows[0].n) + 1, duration],
  )

  return res.status(201).json({
    item: {
      id: inserted.rows[0].id,
      mediaId: parsed.data.mediaId,
      durationSec: duration,
      sortOrder: inserted.rows[0].sort_order,
    },
  })
})

playlistsRouter.delete('/:id/items/:itemId', async (req, res) => {
  if (!(await ownedPlaylist(req.params.id, req.user!.id))) {
    return res.status(404).json({ error: 'Playlist no encontrada' })
  }

  const result = await query(
    `DELETE FROM playlist_items WHERE id = $1 AND playlist_id = $2`,
    [req.params.itemId, req.params.id],
  )
  if (!result.rowCount) {
    return res.status(404).json({ error: 'Elemento no encontrado' })
  }
  return res.status(204).send()
})
