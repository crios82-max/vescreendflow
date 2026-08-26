import path from 'path'
import { fileURLToPath } from 'url'
import express from 'express'
import cors from 'cors'
import { apiRouter } from './routes.js'
import { fleetRouter } from './fleet.js'
import { navRouter } from './nav.js'
import { db } from './db.js'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const webDir = path.join(rootDir, 'web')
const port = Number(process.env.PORT || 4100)

const app = express()

app.use(
  cors({
    origin: true,
  }),
)
app.use(express.json({ limit: '256kb' }))

app.get('/api/health', (_req, res) => {
  const row = db.prepare('SELECT COUNT(*) AS n FROM pings').get() as { n: number }
  res.json({ ok: true, service: 'senseflow', pings: row.n })
})

app.use('/api', apiRouter)
app.use('/api/fleet', fleetRouter)
app.use('/api/nav', navRouter)
app.use(express.static(webDir))

app.get('/', (_req, res) => {
  res.sendFile(path.join(webDir, 'index.html'))
})

app.use(
  (
    err: unknown,
    _req: express.Request,
    res: express.Response,
    _next: express.NextFunction,
  ) => {
    console.error(err)
    res.status(500).json({ error: 'Error interno' })
  },
)

app.listen(port, '0.0.0.0', () => {
  console.log(`SenseFlow API + mapa → http://127.0.0.1:${port}`)
})
