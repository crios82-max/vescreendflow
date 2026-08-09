import { Router } from 'express'
import multer from 'multer'
import path from 'path'
import fs from 'fs'
import { fileURLToPath } from 'url'
import { z } from 'zod'
import { query } from '../db/pool.js'
import { requireAuth } from '../middleware/auth.js'

export const mediaRouter = Router()

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const uploadsDir = path.resolve(__dirname, '../../../uploads')

fs.mkdirSync(uploadsDir, { recursive: true })

const MAX_VIDEO_BYTES = 15 * 1024 * 1024
const MAX_IMAGE_BYTES = 15 * 1024 * 1024

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, uploadsDir),
  filename: (_req, file, cb) => {
    const safe = file.originalname.replace(/[^a-zA-Z0-9._-]/g, '_')
    cb(null, `${Date.now()}-${safe}`)
  },
})

function isMp4(file: Express.Multer.File) {
  const mime = (file.mimetype || '').toLowerCase()
  const name = file.originalname || ''
  return mime === 'video/mp4' || mime === 'application/mp4' || /\.mp4$/i.test(name)
}

function isAllowedImage(file: Express.Multer.File) {
  const mime = (file.mimetype || '').toLowerCase()
  const name = file.originalname || ''
  return (
    ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp'].includes(mime) ||
    /\.(jpe?g|png|gif|webp|bmp)$/i.test(name)
  )
}

const upload = multer({
  storage,
  limits: { fileSize: MAX_VIDEO_BYTES },
  fileFilter: (_req, file, cb) => {
    if (isAllowedImage(file)) {
      cb(null, true)
      return
    }
    if (isMp4(file)) {
      cb(null, true)
      return
    }
    cb(new Error('Videos solo en MP4 (máx. 15 MB). Imágenes: JPG, PNG, WebP o GIF.'))
  },
})

mediaRouter.use(requireAuth)

function publicUrl(filename: string) {
  const base = process.env.PUBLIC_API_URL || 'https://api.vescreenflow.com'
  return `${base}/uploads/${filename}`
}

mediaRouter.get('/', async (req, res) => {
  const result = await query<{
    id: string
    name: string
    media_type: 'image' | 'video'
    url: string
    duration_sec: number
    created_at: string
  }>(
    `SELECT id, name, media_type, url, duration_sec, created_at
     FROM media_assets
     WHERE user_id = $1
     ORDER BY created_at DESC`,
    [req.user!.id],
  )

  return res.json({
    media: result.rows.map((row) => ({
      id: row.id,
      name: row.name,
      mediaType: row.media_type,
      url: row.url,
      durationSec: row.duration_sec,
      createdAt: row.created_at,
    })),
  })
})

mediaRouter.post('/', (req, res) => {
  upload.single('file')(req, res, async (err) => {
    if (err) {
      if (err instanceof multer.MulterError && err.code === 'LIMIT_FILE_SIZE') {
        return res.status(400).json({ error: 'El archivo supera el límite de 15 MB' })
      }
      return res.status(400).json({ error: err.message || 'Error al subir archivo' })
    }
    if (!req.file) {
      return res.status(400).json({ error: 'Archivo requerido' })
    }

    const file = req.file
    const video = isMp4(file)
    const image = isAllowedImage(file)

    if (!video && !image) {
      fs.promises.unlink(file.path).catch(() => undefined)
      return res.status(400).json({
        error: 'Videos solo en MP4 (máx. 15 MB). Imágenes: JPG, PNG, WebP o GIF.',
      })
    }

    if (video && file.size > MAX_VIDEO_BYTES) {
      fs.promises.unlink(file.path).catch(() => undefined)
      return res.status(400).json({ error: 'Cada video MP4 puede pesar máximo 15 MB' })
    }

    if (image && file.size > MAX_IMAGE_BYTES) {
      fs.promises.unlink(file.path).catch(() => undefined)
      return res.status(400).json({ error: 'Cada imagen puede pesar máximo 15 MB' })
    }

    const mediaType: 'image' | 'video' = video ? 'video' : 'image'
    const durationSec = Number(req.body.durationSec || (mediaType === 'video' ? 15 : 8))
    const name = String(req.body.name || file.originalname).slice(0, 120)
    const url = publicUrl(file.filename)
    const playlistId = req.body.playlistId ? String(req.body.playlistId) : null

    try {
      const result = await query<{
        id: string
        name: string
        media_type: 'image' | 'video'
        url: string
        duration_sec: number
      }>(
        `INSERT INTO media_assets (user_id, name, media_type, url, duration_sec)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING id, name, media_type, url, duration_sec`,
        [req.user!.id, name, mediaType, url, durationSec],
      )

      const media = result.rows[0]

      if (playlistId) {
        const owned = await query(
          `SELECT id FROM playlists WHERE id = $1 AND user_id = $2`,
          [playlistId, req.user!.id],
        )
        if (owned.rowCount) {
          const order = await query<{ n: string }>(
            `SELECT COALESCE(MAX(sort_order), -1)::text AS n FROM playlist_items WHERE playlist_id = $1`,
            [playlistId],
          )
          await query(
            `INSERT INTO playlist_items (playlist_id, media_id, sort_order, duration_sec)
             VALUES ($1, $2, $3, $4)`,
            [playlistId, media.id, Number(order.rows[0].n) + 1, durationSec],
          )
        }
      }

      return res.status(201).json({
        media: {
          id: media.id,
          name: media.name,
          mediaType: media.media_type,
          url: media.url,
          durationSec: media.duration_sec,
        },
      })
    } catch (e) {
      console.error(e)
      fs.promises.unlink(file.path).catch(() => undefined)
      return res.status(500).json({ error: 'No se pudo guardar el medio' })
    }
  })
})

const addToPlaylistSchema = z.object({
  playlistId: z.string().uuid(),
  durationSec: z.number().int().positive().optional(),
})

mediaRouter.post('/:id/add-to-playlist', async (req, res) => {
  const parsed = addToPlaylistSchema.safeParse(req.body)
  if (!parsed.success) {
    return res.status(400).json({ error: 'Datos inválidos' })
  }

  const media = await query<{ id: string; duration_sec: number }>(
    `SELECT id, duration_sec FROM media_assets WHERE id = $1 AND user_id = $2`,
    [req.params.id, req.user!.id],
  )
  if (!media.rowCount) {
    return res.status(404).json({ error: 'Medio no encontrado' })
  }

  const playlist = await query(
    `SELECT id FROM playlists WHERE id = $1 AND user_id = $2`,
    [parsed.data.playlistId, req.user!.id],
  )
  if (!playlist.rowCount) {
    return res.status(404).json({ error: 'Playlist no encontrada' })
  }

  const order = await query<{ n: string }>(
    `SELECT COALESCE(MAX(sort_order), -1)::text AS n FROM playlist_items WHERE playlist_id = $1`,
    [parsed.data.playlistId],
  )

  await query(
    `INSERT INTO playlist_items (playlist_id, media_id, sort_order, duration_sec)
     VALUES ($1, $2, $3, $4)`,
    [
      parsed.data.playlistId,
      media.rows[0].id,
      Number(order.rows[0].n) + 1,
      parsed.data.durationSec || media.rows[0].duration_sec,
    ],
  )

  return res.status(201).json({ ok: true })
})

mediaRouter.delete('/:id', async (req, res) => {
  const result = await query<{ url: string }>(
    `DELETE FROM media_assets WHERE id = $1 AND user_id = $2 RETURNING url`,
    [req.params.id, req.user!.id],
  )
  if (!result.rowCount) {
    return res.status(404).json({ error: 'Medio no encontrado' })
  }

  const url = result.rows[0].url
  const filename = url.split('/uploads/')[1]
  if (filename) {
    const full = path.join(uploadsDir, filename)
    fs.promises.unlink(full).catch(() => undefined)
  }

  return res.status(204).send()
})
