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
export function recentlyAlerted(deviceId: string, kind: string, cooldownSec: number): boolean {
  const since = Math.floor(Date.now() / 1000) - cooldownSec
  const row = db
    .prepare(
      `SELECT id FROM fleet_alerts WHERE device_id = ? AND kind = ? AND created_at >= ? LIMIT 1`,
    )
    .get(deviceId, kind, since)
  return row != null
}

export function insertAlert(
  deviceId: string,
  kind: string,
  severity: string,
  message: string,
  payload: Record<string, unknown> | null,
): number {
  const now = Math.floor(Date.now() / 1000)
  const info = db
    .prepare(
      `INSERT INTO fleet_alerts (device_id, kind, severity, message, payload, created_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
    )
    .run(deviceId, kind, severity, message, payload ? JSON.stringify(payload) : null, now)
  return Number(info.lastInsertRowid)
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
    const idleSec = signals.idle_sec
    const ignition = typeof signals.ignition === 'string' ? signals.ignition : ''
    const ignOn = ignition === 'on' || ignition === 'acc' || ignition === 'start'
    if (
      typeof idleSec === 'number' &&
      idleSec >= 300 &&
      ignOn &&
      !recentlyAlerted(deviceId, 'idle_alert', 600)
    ) {
      const mins = Math.round(idleSec / 60)
      insertAlert(
        deviceId,
        'idle_alert',
        'warn',
        `Ralentí prolongado (~${mins} min)`,
        { idle_sec: idleSec, ignition },
      )
      raised.push('idle_alert')
    } else if (
      typeof idleSec === 'number' &&
      idleSec >= 120 &&
      idleSec < 300 &&
      ignOn &&
      !recentlyAlerted(deviceId, 'idle_warn', 600)
    ) {
      const mins = Math.round(idleSec / 60)
      insertAlert(deviceId, 'idle_warn', 'info', `Ralentí (~${mins} min)`, {
        idle_sec: idleSec,
        ignition,
      })
      raised.push('idle_warn')
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

export function openPanicForDevice(deviceId: string): FleetAlert | null {
  const row = db
    .prepare(
      `SELECT id, device_id, kind, severity, message, payload, created_at, acked_at
       FROM fleet_alerts
       WHERE device_id = ? AND kind = 'panic' AND acked_at IS NULL
       ORDER BY id DESC LIMIT 1`,
    )
    .get(deviceId) as FleetAlert | undefined
  return row ?? null
}

/** Raise SOS. Dedupes while an open panic exists. Returns alert id. */
export function raisePanic(
  deviceId: string,
  input: {
    lat?: number | null
    lng?: number | null
    note?: string | null
    source?: string | null
    driver_code?: string | null
    driver_name?: string | null
  } = {},
): { id: number; deduped: boolean } {
  const existing = openPanicForDevice(deviceId)
  if (existing) {
    return { id: existing.id, deduped: true }
  }
  const note = (input.note || '').trim().slice(0, 200)
  const who =
    input.driver_name || input.driver_code
      ? ` (${[input.driver_code, input.driver_name].filter(Boolean).join(' · ')})`
      : ''
  const id = insertAlert(
    deviceId,
    'panic',
    'critical',
    `SOS — pánico conductor${who}${note ? `: ${note}` : ''}`,
    {
      lat: input.lat ?? null,
      lng: input.lng ?? null,
      note: note || null,
      source: input.source || 'device',
      driver_code: input.driver_code || null,
      driver_name: input.driver_name || null,
    },
  )
  return { id, deduped: false }
}

export function ackPanicsForDevice(deviceId: string): number {
  const now = Math.floor(Date.now() / 1000)
  const info = db
    .prepare(
      `UPDATE fleet_alerts SET acked_at = ? WHERE device_id = ? AND kind = 'panic' AND acked_at IS NULL`,
    )
    .run(now, deviceId)
  return info.changes
}
