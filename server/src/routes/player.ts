import { Router } from 'express'
import { query } from '../db/pool.js'

export const playerRouter = Router()

const DEMO_ITEMS = [
  {
    id: 'demo-1',
    name: 'Bienvenido',
    mediaType: 'image' as const,
    url: 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1920&q=80',
    durationSec: 8,
  },
  {
    id: 'demo-2',
    name: 'Menú del día',
    mediaType: 'image' as const,
    url: 'https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=1920&q=80',
    durationSec: 8,
  },
  {
    id: 'demo-3',
    name: 'Promoción',
    mediaType: 'image' as const,
    url: 'https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?auto=format&fit=crop&w=1920&q=80',
    durationSec: 8,
  },
]

function normalizeCode(raw: string) {
  return raw.replace(/\D/g, '').slice(0, 8)
}

playerRouter.get('/:code', async (req, res) => {
  const code = normalizeCode(req.params.code || '')
  if (code.length < 6) {
    return res.status(400).json({ error: 'Código inválido' })
  }

  const screenResult = await query<{
    id: string
    name: string
    pair_code: string
    status: 'online' | 'offline'
    rotation_deg: number
  }>(
    `SELECT id, name, pair_code, status, COALESCE(rotation_deg, 0) AS rotation_deg
     FROM screens WHERE pair_code = $1 LIMIT 1`,
    [code],
  )

  const screen = screenResult.rows[0]
  if (!screen) {
    return res.json({ paired: false, code })
  }

  await query(
    `UPDATE screens SET status = 'online', last_seen_at = NOW(), updated_at = NOW() WHERE id = $1`,
    [screen.id],
  )

  const wallResult = await query<{
    group_id: string
    mode: 'group' | 'videowall'
    rows: number
    cols: number
    row_index: number
    col_index: number
    cycle_epoch: string
    playlist_id: string | null
  }>(
    `SELECT g.id AS group_id, g.mode, g.rows, g.cols, g.cycle_epoch, g.playlist_id,
            m.row_index, m.col_index
     FROM screen_group_members m
     JOIN screen_groups g ON g.id = m.group_id
     WHERE m.screen_id = $1
     LIMIT 1`,
    [screen.id],
  )
  const wallRow = wallResult.rows[0]

  const itemsResult = await query<{
    item_id: string
    id: string
    name: string
    media_type: 'image' | 'video'
    url: string
    duration_sec: number | null
    item_duration: number | null
    sort_order: number
  }>(
    `SELECT pi.id AS item_id, m.id, m.name, m.media_type, m.url, m.duration_sec,
            pi.duration_sec AS item_duration, pi.sort_order
     FROM screen_playlists sp
     JOIN playlists p ON p.id = sp.playlist_id AND p.is_active = TRUE
     JOIN playlist_items pi ON pi.playlist_id = p.id
     JOIN media_assets m ON m.id = pi.media_id
     WHERE sp.screen_id = $1
       AND (sp.starts_at IS NULL OR sp.starts_at <= NOW())
       AND (sp.ends_at IS NULL OR sp.ends_at >= NOW())
       AND (
         sp.daypart_start IS NULL OR sp.daypart_end IS NULL
         OR (
           CASE
             WHEN sp.daypart_start <= sp.daypart_end
               THEN LOCALTIME BETWEEN sp.daypart_start AND sp.daypart_end
             ELSE LOCALTIME >= sp.daypart_start OR LOCALTIME <= sp.daypart_end
           END
         )
       )
     ORDER BY pi.sort_order ASC, pi.id ASC`,
    [screen.id],
  )

  const items =
    itemsResult.rows.length > 0
      ? itemsResult.rows.map((row) => ({
          id: row.item_id || row.id,
          name: row.name,
          mediaType: row.media_type,
          url: row.url,
          durationSec: row.item_duration || row.duration_sec || 10,
        }))
      : DEMO_ITEMS

  const serverTime = Date.now()
  const wall = wallRow
    ? {
        groupId: wallRow.group_id,
        mode: wallRow.mode,
        rows: wallRow.rows,
        cols: wallRow.cols,
        row: wallRow.row_index,
        col: wallRow.col_index,
        serverTime,
        cycleEpoch: new Date(wallRow.cycle_epoch).getTime(),
      }
    : null

  return res.json({
    paired: true,
    code,
    screen: {
      id: screen.id,
      name: screen.name,
      status: 'online',
      rotationDeg: screen.rotation_deg || 0,
    },
    items,
    serverTime,
    wall,
  })
})

playerRouter.post('/:code/heartbeat', async (req, res) => {
  const code = normalizeCode(req.params.code || '')
  if (code.length < 6) {
    return res.status(400).json({ error: 'Código inválido' })
  }

  const result = await query(
    `UPDATE screens
     SET status = 'online', last_seen_at = NOW(), updated_at = NOW()
     WHERE pair_code = $1
     RETURNING id`,
    [code],
  )

  if (!result.rowCount) {
    return res.status(404).json({ error: 'Pantalla no emparejada' })
  }

  return res.json({ ok: true, serverTime: Date.now() })
})
