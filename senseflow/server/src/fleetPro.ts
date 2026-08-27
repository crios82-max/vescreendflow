import { db } from './db.js'

export type GeoFence = {
  id: number
  name: string
  lat: number
  lng: number
  radius_m: number
  active: number
}

export type FleetAlert = {
  id: number
  device_id: string
  kind: string
  severity: string
  message: string
  payload: string | null
  created_at: number
  acked_at: number | null
}

function haversineM(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const R = 6371000
  const toR = (d: number) => (d * Math.PI) / 180
  const dLat = toR(bLat - aLat)
  const dLng = toR(bLng - aLng)
  const x =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toR(aLat)) * Math.cos(toR(bLat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(x))
}

/** Avoid flooding: skip if same kind for device in last `cooldownSec`. */
function recentlyAlerted(deviceId: string, kind: string, cooldownSec: number): boolean {
  const since = Math.floor(Date.now() / 1000) - cooldownSec
  const row = db
    .prepare(
      `SELECT id FROM fleet_alerts WHERE device_id = ? AND kind = ? AND created_at >= ? LIMIT 1`,
    )
    .get(deviceId, kind, since)
  return row != null
}

function insertAlert(
  deviceId: string,
  kind: string,
  severity: string,
  message: string,
  payload: Record<string, unknown> | null,
) {
  const now = Math.floor(Date.now() / 1000)
  db.prepare(
    `INSERT INTO fleet_alerts (device_id, kind, severity, message, payload, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).run(deviceId, kind, severity, message, payload ? JSON.stringify(payload) : null, now)
}

export function recordTelemetrySample(
  deviceId: string,
  lat: number | null | undefined,
  lng: number | null | undefined,
  speedMps: number | null | undefined,
  signals: Record<string, unknown> | null | undefined,
) {
  const now = Math.floor(Date.now() / 1000)
  // Keep history lean: skip if last sample < 25s ago
  const last = db
    .prepare(`SELECT ts FROM fleet_telemetry WHERE device_id = ? ORDER BY ts DESC LIMIT 1`)
    .get(deviceId) as { ts: number } | undefined
  if (last && now - last.ts < 25) return

  db.prepare(
    `INSERT INTO fleet_telemetry (device_id, ts, lat, lng, speed_mps, telemetry_json)
     VALUES (?, ?, ?, ?, ?, ?)`,
  ).run(
    deviceId,
    now,
    lat ?? null,
    lng ?? null,
    speedMps ?? null,
    signals ? JSON.stringify(signals) : null,
  )

  // Cap per device
  db.prepare(
    `DELETE FROM fleet_telemetry WHERE device_id = ? AND id NOT IN (
       SELECT id FROM fleet_telemetry WHERE device_id = ? ORDER BY ts DESC LIMIT 500
     )`,
  ).run(deviceId, deviceId)
}

export function evaluateFleetAlerts(
  deviceId: string,
  lat: number | null | undefined,
  lng: number | null | undefined,
  signals: Record<string, unknown> | null | undefined,
): string[] {
  const raised: string[] = []

  if (typeof lat === 'number' && typeof lng === 'number') {
    const fences = db
      .prepare(`SELECT id, name, lat, lng, radius_m FROM fleet_geofences WHERE active = 1`)
      .all() as Array<{ id: number; name: string; lat: number; lng: number; radius_m: number }>
    for (const f of fences) {
      const d = haversineM(lat, lng, f.lat, f.lng)
      if (d <= f.radius_m) {
        const kind = `geofence_enter:${f.id}`
        if (!recentlyAlerted(deviceId, kind, 300)) {
          insertAlert(
            deviceId,
            kind,
            'info',
            `Entró a geofence «${f.name}» (${Math.round(d)} m)`,
            { geofence_id: f.id, distance_m: Math.round(d) },
          )
          raised.push(kind)
        }
      }
    }
  }

  if (signals) {
    if (signals.abs_active === true && !recentlyAlerted(deviceId, 'abs', 120)) {
      insertAlert(deviceId, 'abs', 'warn', 'ABS activo', { abs_active: true })
      raised.push('abs')
    }
    const tpms = signals.tpms as Record<string, unknown> | undefined
    if (tpms && tpms.low === true && !recentlyAlerted(deviceId, 'tpms_low', 300)) {
      insertAlert(deviceId, 'tpms_low', 'warn', 'TPMS presión baja', tpms)
      raised.push('tpms_low')
    }
    const soc = signals.battery_soc_pct
    if (typeof soc === 'number' && soc < 15 && !recentlyAlerted(deviceId, 'soc_low', 600)) {
      insertAlert(deviceId, 'soc_low', 'warn', `SOC bajo (${Math.round(soc)}%)`, { battery_soc_pct: soc })
      raised.push('soc_low')
    }
    const fuel = signals.fuel_pct
    if (typeof fuel === 'number' && fuel < 15 && !recentlyAlerted(deviceId, 'fuel_low', 600)) {
      insertAlert(deviceId, 'fuel_low', 'warn', `Combustible bajo (${Math.round(fuel)}%)`, {
        fuel_pct: fuel,
      })
      raised.push('fuel_low')
    }
    const rangeKm = signals.range_km
    if (typeof rangeKm === 'number' && rangeKm < 25 && !recentlyAlerted(deviceId, 'range_low', 600)) {
      insertAlert(deviceId, 'range_low', 'warn', `Autonomía baja (${Math.round(rangeKm)} km)`, {
        range_km: rangeKm,
      })
      raised.push('range_low')
    }
  }

  return raised
}

export function openAlertsForDevice(deviceId: string, limit = 20): FleetAlert[] {
  return db
    .prepare(
      `SELECT id, device_id, kind, severity, message, payload, created_at, acked_at
       FROM fleet_alerts
       WHERE device_id = ? AND acked_at IS NULL
       ORDER BY id DESC LIMIT ?`,
    )
    .all(deviceId, limit) as FleetAlert[]
}
