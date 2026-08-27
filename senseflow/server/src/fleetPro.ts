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

export type SpeedZone = {
  id: number
  name: string
  max_kmh: number
  distance_m: number
}

/** Most restrictive active fence with max_kmh containing the point. */
export function activeSpeedZone(
  lat: number | null | undefined,
  lng: number | null | undefined,
): SpeedZone | null {
  if (typeof lat !== 'number' || typeof lng !== 'number') return null
  const fences = db
    .prepare(
      `SELECT id, name, lat, lng, radius_m, max_kmh FROM fleet_geofences
       WHERE active = 1 AND max_kmh IS NOT NULL AND max_kmh > 0`,
    )
    .all() as Array<{
    id: number
    name: string
    lat: number
    lng: number
    radius_m: number
    max_kmh: number
  }>
  let best: SpeedZone | null = null
  for (const f of fences) {
    const d = haversineM(lat, lng, f.lat, f.lng)
    if (d > f.radius_m) continue
    const zone: SpeedZone = {
      id: f.id,
      name: f.name,
      max_kmh: Math.round(Number(f.max_kmh)),
      distance_m: Math.round(d),
    }
    if (!best || zone.max_kmh < best.max_kmh) best = zone
  }
  return best
}

export function evaluateFleetAlerts(
  deviceId: string,
  lat: number | null | undefined,
  lng: number | null | undefined,
  signals: Record<string, unknown> | null | undefined,
  speedMps?: number | null,
): string[] {
  const raised: string[] = []

  if (typeof lat === 'number' && typeof lng === 'number') {
    const fences = db
      .prepare(
        `SELECT id, name, lat, lng, radius_m, max_kmh FROM fleet_geofences WHERE active = 1`,
      )
      .all() as Array<{
      id: number
      name: string
      lat: number
      lng: number
      radius_m: number
      max_kmh: number | null
    }>
    for (const f of fences) {
      const d = haversineM(lat, lng, f.lat, f.lng)
      if (d <= f.radius_m) {
        const kind = `geofence_enter:${f.id}`
        if (!recentlyAlerted(deviceId, kind, 300)) {
          const lim =
            f.max_kmh != null && Number(f.max_kmh) > 0
              ? ` · límite ${Math.round(Number(f.max_kmh))} km/h`
              : ''
          insertAlert(
            deviceId,
            kind,
            'info',
            `Entró a geofence «${f.name}» (${Math.round(d)} m)${lim}`,
            {
              geofence_id: f.id,
              distance_m: Math.round(d),
              max_kmh: f.max_kmh != null ? Number(f.max_kmh) : null,
            },
          )
          raised.push(kind)
        }
        if (f.max_kmh != null && Number(f.max_kmh) > 0) {
          const speedFromSignals =
            typeof signals?.speed_mps === 'number' ? (signals.speed_mps as number) : undefined
          const spdMps = speedMps ?? speedFromSignals
          if (typeof spdMps === 'number') {
            const kmh = spdMps * 3.6
            const lim = Number(f.max_kmh)
            if (kmh > lim + 2 && !recentlyAlerted(deviceId, `geofence_speed:${f.id}`, 120)) {
              insertAlert(
                deviceId,
                `geofence_speed:${f.id}`,
                'warn',
                `Exceso en zona «${f.name}»: ${Math.round(kmh)} > ${Math.round(lim)} km/h`,
                {
                  geofence_id: f.id,
                  speed_kmh: Math.round(kmh),
                  max_kmh: lim,
                },
              )
              raised.push(`geofence_speed:${f.id}`)
            }
          }
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
    if (signals.mil === true && !recentlyAlerted(deviceId, 'mil_on', 600)) {
      insertAlert(deviceId, 'mil_on', 'warn', 'Luz de motor (MIL) encendida', {
        mil: true,
        dtc_count: signals.dtc_count ?? null,
      })
      raised.push('mil_on')
    }
    const uss = signals.uss as Record<string, unknown> | undefined
    if (uss && (signals.reverse === true || signals.gear === 'R')) {
      const vals = [uss.rear_l_m, uss.rear_c_m, uss.rear_r_m]
        .map((v) => (typeof v === 'number' ? v : null))
        .filter((v): v is number => v != null && v > 0)
      if (vals.length) {
        const closest = Math.min(...vals)
        if (closest <= 0.6 && !recentlyAlerted(deviceId, 'parking_crit', 90)) {
          insertAlert(
            deviceId,
            'parking_crit',
            'warn',
            `PDC crítico · ${closest.toFixed(1)} m atrás`,
            { closest_m: closest, uss },
          )
          raised.push('parking_crit')
        } else if (closest <= 1.5 && !recentlyAlerted(deviceId, 'parking_near', 120)) {
          insertAlert(
            deviceId,
            'parking_near',
            'info',
            `PDC cerca · ${closest.toFixed(1)} m atrás`,
            { closest_m: closest, uss },
          )
          raised.push('parking_near')
        }
      }
    }
    const doors = signals.doors as Record<string, unknown> | undefined
    if (doors) {
      const parts: string[] = []
      if (doors.fl === true) parts.push('FL')
      if (doors.fr === true) parts.push('FR')
      if (doors.rl === true) parts.push('RL')
      if (doors.rr === true) parts.push('RR')
      if (doors.trunk === true) parts.push('maletero')
      if (doors.hood === true) parts.push('capó')
      if (parts.length) {
        const which = parts.join(', ')
        const speedFromSignals =
          typeof signals.speed_mps === 'number'
            ? (signals.speed_mps as number)
            : typeof signals.speed_kmh === 'number'
              ? (signals.speed_kmh as number) / 3.6
              : undefined
        const spdMps = speedMps ?? speedFromSignals
        const kmh = typeof spdMps === 'number' ? spdMps * 3.6 : null
        const moving =
          (typeof kmh === 'number' && kmh >= 5) ||
          signals.reverse === true ||
          signals.gear === 'R'
        if (moving && !recentlyAlerted(deviceId, 'door_moving', 90)) {
          insertAlert(
            deviceId,
            'door_moving',
            'critical',
            `Puerta abierta en movimiento (${which}${typeof kmh === 'number' ? ` · ${Math.round(kmh)} km/h` : ''})`,
            { doors, speed_kmh: kmh != null ? Math.round(kmh) : null },
          )
          raised.push('door_moving')
        } else if (!moving && !recentlyAlerted(deviceId, 'door_ajar', 180)) {
          insertAlert(deviceId, 'door_ajar', 'warn', `Puerta abierta (${which})`, { doors })
          raised.push('door_ajar')
        }
      }
    }
    const dtcs = Array.isArray(signals.dtcs) ? signals.dtcs : []
    for (const raw of dtcs) {
      if (!raw || typeof raw !== 'object') continue
      const row = raw as Record<string, unknown>
      const code = typeof row.code === 'string' ? row.code.trim().toUpperCase() : ''
      if (!/^[PCBU][0-9A-F]{4}$/i.test(code)) continue
      const kind = `dtc:${code}`
      if (recentlyAlerted(deviceId, kind, 1800)) continue
      const status = typeof row.status === 'string' ? row.status : 'stored'
      insertAlert(deviceId, kind, 'warn', `DTC ${code} (${status})`, {
        code,
        status,
      })
      raised.push(kind)
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

    // Shift fatigue — duration from client, or open shift started_at
    let shiftDurSec: number | null =
      typeof signals.shift_duration_sec === 'number' ? (signals.shift_duration_sec as number) : null
    if (shiftDurSec == null) {
      const open = db
        .prepare(
          `SELECT started_at FROM fleet_shifts WHERE device_id = ? AND status = 'open' LIMIT 1`,
        )
        .get(deviceId) as { started_at: number } | undefined
      if (open && typeof open.started_at === 'number') {
        shiftDurSec = Math.max(0, Math.floor(Date.now() / 1000) - open.started_at)
      }
    }
    if (typeof shiftDurSec === 'number' && shiftDurSec > 0) {
      const warnSec =
        typeof signals.shift_warn_sec === 'number' ? (signals.shift_warn_sec as number) : 4 * 3600
      const alertSec =
        typeof signals.shift_alert_sec === 'number' ? (signals.shift_alert_sec as number) : 8 * 3600
      const hours = shiftDurSec / 3600
      const hLabel = hours >= 1 ? `${hours.toFixed(1)} h` : `${Math.round(shiftDurSec / 60)} min`
      if (shiftDurSec >= alertSec && !recentlyAlerted(deviceId, 'shift_fatigue', 900)) {
        insertAlert(
          deviceId,
          'shift_fatigue',
          'critical',
          `Turno prolongado (~${hLabel}) — descanso`,
          { shift_duration_sec: shiftDurSec },
        )
        raised.push('shift_fatigue')
      } else if (
        shiftDurSec >= warnSec &&
        shiftDurSec < alertSec &&
        !recentlyAlerted(deviceId, 'shift_warn', 900)
      ) {
        insertAlert(deviceId, 'shift_warn', 'warn', `Turno largo (~${hLabel})`, {
          shift_duration_sec: shiftDurSec,
        })
        raised.push('shift_warn')
      }
    }

    // Cabin overtemp — hvac.cabin_c or top-level cabin_c
    const hvac = signals.hvac as Record<string, unknown> | undefined
    let cabinC: number | null =
      typeof hvac?.cabin_c === 'number'
        ? (hvac.cabin_c as number)
        : typeof signals.cabin_c === 'number'
          ? (signals.cabin_c as number)
          : null
    if (typeof cabinC === 'number') {
      const warnC = typeof signals.cabin_warn_c === 'number' ? (signals.cabin_warn_c as number) : 32
      const alertC =
        typeof signals.cabin_alert_c === 'number' ? (signals.cabin_alert_c as number) : 38
      if (cabinC >= alertC && !recentlyAlerted(deviceId, 'cabin_overtemp', 300)) {
        insertAlert(
          deviceId,
          'cabin_overtemp',
          'critical',
          `Cabina crítica · ${Math.round(cabinC)} °C`,
          { cabin_c: cabinC, alert_c: alertC },
        )
        raised.push('cabin_overtemp')
      } else if (
        cabinC >= warnC &&
        cabinC < alertC &&
        !recentlyAlerted(deviceId, 'cabin_warn', 300)
      ) {
        insertAlert(deviceId, 'cabin_warn', 'warn', `Cabina caliente · ${Math.round(cabinC)} °C`, {
          cabin_c: cabinC,
          warn_c: warnC,
        })
        raised.push('cabin_warn')
      }
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

/** Merge dashcam clip metadata into an open panic alert payload. */
export function attachPanicClip(
  deviceId: string,
  input: {
    alertId?: number | null
    clipUrl: string
    kind?: string | null
    camera?: string | null
    durationSec?: number | null
    bytes?: number | null
    sim?: boolean | null
  },
): FleetAlert | null {
  let alert: FleetAlert | null = null
  if (input.alertId != null) {
    const row = db
      .prepare(
        `SELECT id, device_id, kind, severity, message, payload, created_at, acked_at
         FROM fleet_alerts WHERE id = ? AND device_id = ? AND kind = 'panic' LIMIT 1`,
      )
      .get(input.alertId, deviceId) as FleetAlert | undefined
    alert = row ?? null
  }
  if (!alert) alert = openPanicForDevice(deviceId)
  if (!alert) return null

  let prev: Record<string, unknown> = {}
  if (typeof alert.payload === 'string' && alert.payload) {
    try {
      prev = JSON.parse(alert.payload) as Record<string, unknown>
    } catch {
      prev = {}
    }
  }
  const next = {
    ...prev,
    clip_url: input.clipUrl,
    clip_kind: input.kind || 'jpeg',
    clip_camera: input.camera || null,
    clip_duration_sec: input.durationSec ?? null,
    clip_bytes: input.bytes ?? null,
    clip_sim: input.sim === true,
    clip_at: Math.floor(Date.now() / 1000),
  }
  db.prepare(`UPDATE fleet_alerts SET payload = ? WHERE id = ?`).run(JSON.stringify(next), alert.id)
  return {
    ...alert,
    payload: JSON.stringify(next),
  }
}

export function panicClipUrlFromAlert(alert: FleetAlert | null): string | null {
  if (!alert?.payload) return null
  try {
    const p = JSON.parse(alert.payload) as Record<string, unknown>
    return typeof p.clip_url === 'string' ? p.clip_url : null
  } catch {
    return null
  }
}
