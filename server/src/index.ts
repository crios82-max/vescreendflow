import dotenv from 'dotenv'
import path from 'path'
import { fileURLToPath } from 'url'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
dotenv.config({ path: path.join(rootDir, '.env') })

import express from 'express'
import cors from 'cors'
import { authRouter } from './routes/auth.js'
import { screensRouter } from './routes/screens.js'
import { playlistsRouter } from './routes/playlists.js'
import { playerRouter } from './routes/player.js'
import { mediaRouter } from './routes/media.js'
import { groupsRouter } from './routes/groups.js'
import { pool } from './db/pool.js'

const app = express()
const port = Number(process.env.PORT || 4000)
const uploadsDir = path.join(rootDir, 'uploads')

app.use(
  cors({
    origin: [
      'http://127.0.0.1:5173',
      'http://localhost:5173',
      'https://vescreenflow.com',
      'https://www.vescreenflow.com',
      'https://vescreenflow.pages.dev',
      /^https:\/\/[a-z0-9-]+\.vescreenflow\.pages\.dev$/,
      process.env.CORS_ORIGIN,
    ].filter(Boolean) as (string | RegExp)[],
  }),
)
app.use(express.json())
app.use('/uploads', express.static(uploadsDir))

app.get('/', (_req, res) => {
  res.type('text').send('vescreenflow API OK — usa /api/health')
})

app.get('/api/health', async (_req, res) => {
  try {
    await pool.query('SELECT 1')
    res.json({ ok: true, db: true })
  } catch (err) {
    console.error('DB health check failed:', err)
    res.status(500).json({ ok: false, db: false })
  }
})

app.use('/api/auth', authRouter)
app.use('/api/screens', screensRouter)
app.use('/api/playlists', playlistsRouter)
app.use('/api/player', playerRouter)
app.use('/api/media', mediaRouter)
app.use('/api/groups', groupsRouter)

app.use(
  (
    err: unknown,
    _req: express.Request,
    res: express.Response,
    _next: express.NextFunction,
  ) => {
    console.error(err)
    res.status(500).json({ error: 'Error interno del servidor' })
  },
)

app.listen(port, '0.0.0.0', () => {
  console.log(`vescreenflow API on http://127.0.0.1:${port}`)
  console.log(`DATABASE_URL=${process.env.DATABASE_URL ? 'set' : 'missing'}`)
  console.log(`Uploads: ${uploadsDir}`)
})
