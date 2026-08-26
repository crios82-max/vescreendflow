import path from 'path'
import { fileURLToPath } from 'url'
import fs from 'fs'
import express from 'express'
import cors from 'cors'
import { apiRouter } from './routes.js'
import { fleetRouter } from './fleet.js'
import { ensureFleetOpsTables, fleetOpsRouter } from './fleetOps.js'
import {
  ensureFleetDriversTables,
  fleetDriversRouter,
  mountDriverOpsRoutes,
} from './fleetDrivers.js'
import { navRouter } from './nav.js'
import { db } from './db.js'

ensureFleetOpsTables()
ensureFleetDriversTables()
mountDriverOpsRoutes(fleetOpsRouter)

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const webDir = path.join(rootDir, 'web')
const otaDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../ota')
const port = Number(process.env.PORT || 4100)

fs.mkdirSync(otaDir, { recursive: true })

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
app.use('/api/fleet/drivers', fleetDriversRouter)
app.use('/api/fleet/ops', fleetOpsRouter)
app.use('/api/nav', navRouter)

// Fleet OTA APK hosting (publish-ota.sh drops files here)
app.use('/ota', express.static(otaDir, { fallthrough: true, index: false }))

const dbcDir = path.join(rootDir, 'dbc')
fs.mkdirSync(dbcDir, { recursive: true })
app.use('/dbc', express.static(dbcDir, { fallthrough: true, index: false }))

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
  console.log(`OTA files → http://127.0.0.1:${port}/ota/  (${otaDir})`)
})
