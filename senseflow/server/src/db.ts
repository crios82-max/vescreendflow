import Database from 'better-sqlite3'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const dataDir = path.join(rootDir, 'data')
const dbPath = process.env.SENSEFLOW_DB || path.join(dataDir, 'senseflow.sqlite')

fs.mkdirSync(path.dirname(dbPath), { recursive: true })

export const db = new Database(dbPath)
db.pragma('journal_mode = WAL')
db.pragma('foreign_keys = ON')

db.exec(`
  CREATE TABLE IF NOT EXISTS pings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    accuracy_m REAL,
    speed_mps REAL,
    activity TEXT NOT NULL CHECK (activity IN ('IN_VEHICLE','ON_FOOT','STILL','UNKNOWN')),
    geohash TEXT NOT NULL,
    device_bucket TEXT NOT NULL,
    ts INTEGER NOT NULL,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
  );

  CREATE INDEX IF NOT EXISTS idx_pings_ts ON pings(ts);
  CREATE INDEX IF NOT EXISTS idx_pings_geohash ON pings(geohash);
  CREATE INDEX IF NOT EXISTS idx_pings_activity_ts ON pings(activity, ts);
`)

export type Activity = 'IN_VEHICLE' | 'ON_FOOT' | 'STILL' | 'UNKNOWN'

export type PingRow = {
  id: number
  lat: number
  lng: number
  accuracy_m: number | null
  speed_mps: number | null
  activity: Activity
  geohash: string
  device_bucket: string
  ts: number
  created_at: number
}
