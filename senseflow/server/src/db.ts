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

  CREATE TABLE IF NOT EXISTS fleet_devices (
    device_id TEXT PRIMARY KEY,
    pair_code TEXT,
    name TEXT,
    app_version TEXT,
    version_code INTEGER,
    last_seen_at INTEGER,
    last_lat REAL,
    last_lng REAL,
    last_speed_mps REAL,
    reverse INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'online',
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
  );

  CREATE INDEX IF NOT EXISTS idx_fleet_pair ON fleet_devices(pair_code);
  CREATE INDEX IF NOT EXISTS idx_fleet_seen ON fleet_devices(last_seen_at);

  CREATE TABLE IF NOT EXISTS ota_releases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version_name TEXT NOT NULL,
    version_code INTEGER NOT NULL UNIQUE,
    apk_url TEXT NOT NULL,
    notes TEXT,
    created_at INTEGER NOT NULL DEFAULT (unixepoch())
  );

  CREATE TABLE IF NOT EXISTS fleet_commands (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT NOT NULL,
    command TEXT NOT NULL CHECK (command IN ('restart','lock','message','wipe','ota')),
    payload TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','acked','done','failed')),
    created_at INTEGER NOT NULL DEFAULT (unixepoch()),
    acked_at INTEGER,
    FOREIGN KEY (device_id) REFERENCES fleet_devices(device_id)
  );

  CREATE INDEX IF NOT EXISTS idx_fleet_cmd_pending ON fleet_commands(device_id, status);
`)

// Seed a placeholder OTA row if empty
const otaCount = db.prepare('SELECT COUNT(*) AS n FROM ota_releases').get() as { n: number }
if (otaCount.n === 0) {
  db.prepare(
    `INSERT INTO ota_releases (version_name, version_code, apk_url, notes)
     VALUES ('0.3.0', 3, 'https://example.com/veplayer-0.3.0.apk', 'VePlayer OS MVP — reemplaza apk_url en producción')`,
  ).run()
}

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
