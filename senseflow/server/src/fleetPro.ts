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
    const nowSec = Math.floor(Date.now() / 1000)
    const prevRows = db
      .prepare(`SELECT geofence_id FROM fleet_geofence_presence WHERE device_id = ?`)
      .all(deviceId) as Array<{ geofence_id: number }>
    const prevInside = new Set(prevRows.map((r) => r.geofence_id))
    const nowInside = new Set<number>()

    for (const f of fences) {
      const d = haversineM(lat, lng, f.lat, f.lng)
      if (d <= f.radius_m) {
        nowInside.add(f.id)
        if (!prevInside.has(f.id)) {
          db.prepare(
            `INSERT OR REPLACE INTO fleet_geofence_presence (device_id, geofence_id, entered_at)
             VALUES (?, ?, ?)`,
          ).run(deviceId, f.id, nowSec)
          const kind = `geofence_enter:${f.id}`
          if (!recentlyAlerted(deviceId, kind, 60)) {
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

    for (const gid of prevInside) {
      if (nowInside.has(gid)) continue
      db.prepare(
        `DELETE FROM fleet_geofence_presence WHERE device_id = ? AND geofence_id = ?`,
      ).run(deviceId, gid)
      const fence = fences.find((f) => f.id === gid)
      const name = fence?.name ?? `#${gid}`
      const kind = `geofence_exit:${gid}`
      if (!recentlyAlerted(deviceId, kind, 60)) {
        insertAlert(deviceId, kind, 'info', `Salió de geofence «${name}»`, {
          geofence_id: gid,
        })
        raised.push(kind)
      }
    }
  }

  if (signals) {
    if (signals.abs_active === true || (signals.abs as Record<string, unknown> | undefined)?.active === true) {
      const absObj = signals.abs as Record<string, unknown> | undefined
      const activeSec =
        typeof signals.abs_active_sec === 'number'
          ? (signals.abs_active_sec as number)
          : typeof absObj?.active_for_sec === 'number'
            ? (absObj.active_for_sec as number)
            : 0
      const events =
        typeof signals.abs_events === 'number'
          ? (signals.abs_events as number)
          : typeof absObj?.events === 'number'
            ? (absObj.events as number)
            : 0
      const warnSec =
        typeof signals.abs_warn_sec === 'number' ? (signals.abs_warn_sec as number) : 0.5
      const alertSec =
        typeof signals.abs_alert_sec === 'number' ? (signals.abs_alert_sec as number) : 2
      const alertEvents =
        typeof signals.abs_alert_events === 'number' ? (signals.abs_alert_events as number) : 3
      const bandFromClient =
        typeof absObj?.band === 'string' ? (absObj.band as string) : null
      const band =
        bandFromClient === 'alert' || bandFromClient === 'warn'
          ? bandFromClient
          : activeSec >= alertSec || events >= alertEvents
            ? 'alert'
            : activeSec >= warnSec || signals.abs_active === true
              ? 'warn'
              : 'ok'
      if (band === 'alert' && !recentlyAlerted(deviceId, 'abs_alert', 120)) {
        insertAlert(
          deviceId,
          'abs_alert',
          'critical',
          `ABS crítico` +
            (activeSec > 0 ? ` · ${activeSec.toFixed(1)}s` : '') +
            (events > 0 ? ` · ×${events}` : ''),
          { abs_active: true, abs_active_sec: activeSec, abs_events: events, abs: absObj ?? null },
        )
        raised.push('abs_alert')
      } else if (band === 'warn' && !recentlyAlerted(deviceId, 'abs_warn', 120)) {
        insertAlert(deviceId, 'abs_warn', 'warn', 'ABS activo', {
          abs_active: true,
          abs_active_sec: activeSec,
          abs_events: events,
        })
        raised.push('abs_warn')
        // legacy alias for older dashboards
        if (!recentlyAlerted(deviceId, 'abs', 120)) {
          raised.push('abs')
        }
      }
    }
    const tpms = signals.tpms as Record<string, unknown> | undefined
    if (tpms) {
      const warnPsi =
        typeof signals.tpms_warn_psi === 'number' ? (signals.tpms_warn_psi as number) : 28
      const alertPsi =
        typeof signals.tpms_alert_psi === 'number' ? (signals.tpms_alert_psi as number) : 24
      const wheels: { id: string; psi: number }[] = []
      for (const [key, id] of [
        ['fl_psi', 'FL'],
        ['fr_psi', 'FR'],
        ['rl_psi', 'RL'],
        ['rr_psi', 'RR'],
      ] as const) {
        const v = tpms[key]
        if (typeof v === 'number') wheels.push({ id, psi: v })
      }
      const lowAlert = wheels.filter((w) => w.psi < alertPsi)
      const lowWarn = wheels.filter((w) => w.psi < warnPsi && w.psi >= alertPsi)
      const minPsi =
        wheels.length > 0 ? Math.min(...wheels.map((w) => w.psi)) : null
      if (lowAlert.length > 0 && !recentlyAlerted(deviceId, 'tpms_alert', 300)) {
        const ids = lowAlert.map((w) => w.id).join('·')
        insertAlert(
          deviceId,
          'tpms_alert',
          'critical',
          `TPMS crítico · ${ids} · ${Math.round(minPsi ?? 0)} psi`,
          {
            ...tpms,
            low_wheels: lowAlert.map((w) => w.id),
            min_psi: minPsi,
            alert_psi: alertPsi,
          },
        )
        raised.push('tpms_alert')
      } else if (lowWarn.length > 0 && !recentlyAlerted(deviceId, 'tpms_warn', 300)) {
        const ids = lowWarn.map((w) => w.id).join('·')
        insertAlert(
          deviceId,
          'tpms_warn',
          'warn',
          `TPMS bajo · ${ids} · ${Math.round(minPsi ?? 0)} psi`,
          {
            ...tpms,
            low_wheels: lowWarn.map((w) => w.id),
            min_psi: minPsi,
            warn_psi: warnPsi,
          },
        )
        raised.push('tpms_warn')
      } else if (
        tpms.low === true &&
        wheels.length === 0 &&
        !recentlyAlerted(deviceId, 'tpms_low', 300)
      ) {
        // Legacy flag without per-wheel psi
        insertAlert(deviceId, 'tpms_low', 'warn', 'TPMS presión baja', tpms)
        raised.push('tpms_low')
      }
    }
    if (signals.mil === true && !recentlyAlerted(deviceId, 'mil_on', 600)) {
      insertAlert(deviceId, 'mil_on', 'warn', 'Luz de motor (MIL) encendida', {
        mil: true,
        dtc_count: signals.dtc_count ?? null,
      })
      raised.push('mil_on')
    }

    // Distance with MIL on (OBD PID 0121)
    const milDistObj = signals.mil_dist as Record<string, unknown> | undefined
    const milOn =
      signals.mil === true || milDistObj?.mil_on === true
    let milDistKm: number | null =
      typeof milDistObj?.distance_km === 'number'
        ? (milDistObj.distance_km as number)
        : typeof signals.mil_distance_km === 'number'
          ? (signals.mil_distance_km as number)
          : null
    if (milOn && typeof milDistKm === 'number') {
      const warnKm =
        typeof signals.mil_dist_warn_km === 'number'
          ? (signals.mil_dist_warn_km as number)
          : 50
      const alertKm =
        typeof signals.mil_dist_alert_km === 'number'
          ? (signals.mil_dist_alert_km as number)
          : 100
      if (milDistKm >= alertKm && !recentlyAlerted(deviceId, 'mil_dist_alert', 600)) {
        insertAlert(
          deviceId,
          'mil_dist_alert',
          'critical',
          `MIL activa · ${Math.round(milDistKm)} km`,
          {
            mil_distance_km: milDistKm,
            alert_km: alertKm,
            mil_dist: milDistObj ?? null,
          },
        )
        raised.push('mil_dist_alert')
      } else if (
        milDistKm >= warnKm &&
        milDistKm < alertKm &&
        !recentlyAlerted(deviceId, 'mil_dist_warn', 600)
      ) {
        insertAlert(
          deviceId,
          'mil_dist_warn',
          'warn',
          `MIL · ${Math.round(milDistKm)} km recorridos`,
          {
            mil_distance_km: milDistKm,
            warn_km: warnKm,
            mil_dist: milDistObj ?? null,
          },
        )
        raised.push('mil_dist_warn')
      }
    }

    // Distance since DTC clear (OBD PID 0131)
    const clearObj = signals.dist_since_clear as Record<string, unknown> | undefined
    const faultActive =
      signals.mil === true ||
      clearObj?.fault_active === true ||
      (typeof signals.dtc_count === 'number' && (signals.dtc_count as number) > 0)
    let clearKm: number | null =
      typeof clearObj?.distance_km === 'number'
        ? (clearObj.distance_km as number)
        : typeof signals.dist_since_clear_km === 'number'
          ? (signals.dist_since_clear_km as number)
          : null
    if (faultActive && typeof clearKm === 'number') {
      const warnKm =
        typeof signals.dist_clear_warn_km === 'number'
          ? (signals.dist_clear_warn_km as number)
          : 100
      const alertKm =
        typeof signals.dist_clear_alert_km === 'number'
          ? (signals.dist_clear_alert_km as number)
          : 200
      if (clearKm >= alertKm && !recentlyAlerted(deviceId, 'dist_clear_alert', 600)) {
        insertAlert(
          deviceId,
          'dist_clear_alert',
          'critical',
          `Falla sin reparar · ${Math.round(clearKm)} km desde clear`,
          {
            dist_since_clear_km: clearKm,
            alert_km: alertKm,
            dist_since_clear: clearObj ?? null,
          },
        )
        raised.push('dist_clear_alert')
      } else if (
        clearKm >= warnKm &&
        clearKm < alertKm &&
        !recentlyAlerted(deviceId, 'dist_clear_warn', 600)
      ) {
        insertAlert(
          deviceId,
          'dist_clear_warn',
          'warn',
          `Desde clear · ${Math.round(clearKm)} km con falla`,
          {
            dist_since_clear_km: clearKm,
            warn_km: warnKm,
            dist_since_clear: clearObj ?? null,
          },
        )
        raised.push('dist_clear_warn')
      }
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

    // Seatbelt — unlatched while moving
    if (signals.seatbelt_driver === false) {
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
      if (moving && !recentlyAlerted(deviceId, 'seatbelt_alert', 120)) {
        insertAlert(
          deviceId,
          'seatbelt_alert',
          'critical',
          `Cinturón desabrochado en movimiento${typeof kmh === 'number' ? ` · ${Math.round(kmh)} km/h` : ''}`,
          { seatbelt_driver: false, speed_kmh: kmh != null ? Math.round(kmh) : null },
        )
        raised.push('seatbelt_alert')
      } else if (!moving && !recentlyAlerted(deviceId, 'seatbelt_warn', 180)) {
        insertAlert(deviceId, 'seatbelt_warn', 'warn', 'Cinturón desabrochado', {
          seatbelt_driver: false,
        })
        raised.push('seatbelt_warn')
      }
    }

    // Harsh brake / accel from client sample
    const harsh = signals.harsh as Record<string, unknown> | undefined
    if (harsh && typeof harsh.band === 'string') {
      const band = harsh.band as string
      if (
        (band === 'brake_alert' || band === 'accel_alert') &&
        !recentlyAlerted(deviceId, band, 120)
      ) {
        const kindLabel = band.startsWith('brake') ? 'Frenada brusca' : 'Aceleración brusca'
        const mag =
          typeof harsh.accel_kmh_s === 'number'
            ? Math.abs(harsh.accel_kmh_s as number)
            : null
        insertAlert(
          deviceId,
          band,
          'critical',
          `${kindLabel}${mag != null ? ` · ${Math.round(mag)} km/h/s` : ''}${harsh.abs === true ? ' · ABS' : ''}`,
          { harsh },
        )
        raised.push(band)
      } else if (
        (band === 'brake_warn' || band === 'accel_warn') &&
        !recentlyAlerted(deviceId, band, 120)
      ) {
        const kindLabel = band.startsWith('brake') ? 'Frenada fuerte' : 'Aceleración fuerte'
        insertAlert(deviceId, band, 'warn', kindLabel, { harsh })
        raised.push(band)
      }
    }

    // Impact / collision candidate
    const impact = signals.impact as Record<string, unknown> | undefined
    if (impact && typeof impact.band === 'string') {
      const band = impact.band as string
      const decel =
        typeof impact.decel_kmh_s === 'number' ? (impact.decel_kmh_s as number) : null
      const yaw = typeof impact.yaw_deg_s === 'number' ? (impact.yaw_deg_s as number) : null
      const kind = typeof impact.kind === 'string' ? (impact.kind as string) : ''
      if (band === 'alert' && !recentlyAlerted(deviceId, 'impact_alert', 180)) {
        insertAlert(
          deviceId,
          'impact_alert',
          'critical',
          `Posible impacto${kind ? ` · ${kind}` : ''}` +
            (decel != null ? ` · ${Math.round(decel)} km/h/s` : '') +
            (yaw != null && kind === 'yaw' ? ` · yaw ${Math.round(yaw)}°/s` : ''),
          { impact },
        )
        raised.push('impact_alert')
      } else if (band === 'warn' && !recentlyAlerted(deviceId, 'impact_warn', 180)) {
        insertAlert(
          deviceId,
          'impact_warn',
          'warn',
          `Maniobra extrema / posible golpe` +
            (decel != null ? ` · ${Math.round(decel)} km/h/s` : ''),
          { impact },
        )
        raised.push('impact_warn')
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

    // Continuous driving rest break
    const restDriveSec =
      typeof signals.rest_drive_sec === 'number' ? (signals.rest_drive_sec as number) : null
    if (typeof restDriveSec === 'number' && restDriveSec > 0) {
      const restWarn =
        typeof signals.rest_warn_sec === 'number' ? (signals.rest_warn_sec as number) : 2 * 3600
      const restAlert =
        typeof signals.rest_alert_sec === 'number' ? (signals.rest_alert_sec as number) : 2.5 * 3600
      const hours = restDriveSec / 3600
      const hLabel = hours >= 1 ? `${hours.toFixed(1)} h` : `${Math.round(restDriveSec / 60)} min`
      if (restDriveSec >= restAlert && !recentlyAlerted(deviceId, 'rest_break', 900)) {
        insertAlert(
          deviceId,
          'rest_break',
          'critical',
          `Descanso obligatorio · ${hLabel} al volante`,
          { rest_drive_sec: restDriveSec, alert_sec: restAlert },
        )
        raised.push('rest_break')
      } else if (
        restDriveSec >= restWarn &&
        restDriveSec < restAlert &&
        !recentlyAlerted(deviceId, 'rest_warn', 900)
      ) {
        insertAlert(
          deviceId,
          'rest_warn',
          'warn',
          `Pausa recomendada · ${hLabel} conduciendo`,
          { rest_drive_sec: restDriveSec, warn_sec: restWarn },
        )
        raised.push('rest_warn')
      }
    }

    // Route deviation / off-route
    const routeDev = signals.route_dev as Record<string, unknown> | undefined
    let routeOffM: number | null =
      typeof routeDev?.distance_m === 'number'
        ? (routeDev.distance_m as number)
        : typeof signals.route_off_m === 'number'
          ? (signals.route_off_m as number)
          : null
    if (typeof routeOffM === 'number' && routeOffM > 0) {
      const warnM =
        typeof signals.route_warn_m === 'number'
          ? (signals.route_warn_m as number)
          : 80
      const alertM =
        typeof signals.route_alert_m === 'number'
          ? (signals.route_alert_m as number)
          : 150
      const bandFromClient =
        typeof routeDev?.band === 'string' ? (routeDev.band as string) : null
      const band =
        bandFromClient === 'alert' || bandFromClient === 'warn' || bandFromClient === 'ok'
          ? bandFromClient
          : routeOffM >= alertM
            ? 'alert'
            : routeOffM >= warnM
              ? 'warn'
              : 'ok'
      if (band === 'alert' && !recentlyAlerted(deviceId, 'route_deviate', 180)) {
        insertAlert(
          deviceId,
          'route_deviate',
          'critical',
          `Fuera de ruta · ${Math.round(routeOffM)} m`,
          { route_off_m: routeOffM, alert_m: alertM, route_dev: routeDev ?? null },
        )
        raised.push('route_deviate')
      } else if (
        band === 'warn' &&
        !recentlyAlerted(deviceId, 'route_warn', 180)
      ) {
        insertAlert(
          deviceId,
          'route_warn',
          'warn',
          `Desvío de ruta · ${Math.round(routeOffM)} m`,
          { route_off_m: routeOffM, warn_m: warnM, route_dev: routeDev ?? null },
        )
        raised.push('route_warn')
      }
    }

    // Driver safety scorecard
    const driverScore = signals.driver_score as Record<string, unknown> | undefined
    let scoreVal: number | null =
      typeof driverScore?.score === 'number'
        ? (driverScore.score as number)
        : typeof signals.driver_score_value === 'number'
          ? (signals.driver_score_value as number)
          : null
    if (typeof scoreVal === 'number' && driverScore?.active !== false) {
      const warnScore =
        typeof signals.driver_score_warn === 'number'
          ? (signals.driver_score_warn as number)
          : 70
      const alertScore =
        typeof signals.driver_score_alert === 'number'
          ? (signals.driver_score_alert as number)
          : 50
      if (scoreVal <= alertScore && !recentlyAlerted(deviceId, 'score_alert', 300)) {
        insertAlert(
          deviceId,
          'score_alert',
          'critical',
          `Puntaje bajo · ${Math.round(scoreVal)}`,
          { driver_score: driverScore ?? { score: scoreVal }, alert: alertScore },
        )
        raised.push('score_alert')
      } else if (
        scoreVal < warnScore &&
        scoreVal > alertScore &&
        !recentlyAlerted(deviceId, 'score_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'score_warn',
          'warn',
          `Puntaje en baja · ${Math.round(scoreVal)}`,
          { driver_score: driverScore ?? { score: scoreVal }, warn: warnScore },
        )
        raised.push('score_warn')
      }
    }

    // Live eco score (shift efficiency)
    const ecoLive = signals.eco_live as Record<string, unknown> | undefined
    let ecoScoreVal: number | null =
      typeof ecoLive?.score === 'number'
        ? (ecoLive.score as number)
        : typeof signals.eco_score === 'number'
          ? (signals.eco_score as number)
          : null
    if (typeof ecoScoreVal === 'number' && ecoLive?.active !== false) {
      const warnScore =
        typeof signals.eco_warn_score === 'number' ? (signals.eco_warn_score as number) : 70
      const alertScore =
        typeof signals.eco_alert_score === 'number' ? (signals.eco_alert_score as number) : 50
      if (ecoScoreVal <= alertScore && !recentlyAlerted(deviceId, 'eco_alert', 300)) {
        insertAlert(
          deviceId,
          'eco_alert',
          'critical',
          `Eco bajo · ${Math.round(ecoScoreVal)}`,
          { eco_score: ecoScoreVal, eco_live: ecoLive ?? null, alert: alertScore },
        )
        raised.push('eco_alert')
      } else if (
        ecoScoreVal < warnScore &&
        ecoScoreVal > alertScore &&
        !recentlyAlerted(deviceId, 'eco_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'eco_warn',
          'warn',
          `Eco en baja · ${Math.round(ecoScoreVal)}`,
          { eco_score: ecoScoreVal, eco_live: ecoLive ?? null, warn: warnScore },
        )
        raised.push('eco_warn')
      }
    }

    // Engine runtime since start (OBD PID 011F)
    const engRt =
      signals.engine_runtime as Record<string, unknown> | undefined
    let runtimeSec: number | null =
      typeof engRt?.runtime_sec === 'number'
        ? (engRt.runtime_sec as number)
        : typeof signals.runtime_sec === 'number'
          ? (signals.runtime_sec as number)
          : null
    if (typeof runtimeSec === 'number') {
      const warnSec =
        typeof signals.runtime_warn_sec === 'number'
          ? (signals.runtime_warn_sec as number)
          : 2 * 3600
      const alertSec =
        typeof signals.runtime_alert_sec === 'number'
          ? (signals.runtime_alert_sec as number)
          : 4 * 3600
      if (runtimeSec >= alertSec && !recentlyAlerted(deviceId, 'runtime_alert', 600)) {
        const h = (runtimeSec / 3600).toFixed(1)
        insertAlert(
          deviceId,
          'runtime_alert',
          'critical',
          `Motor encendido · ${h} h`,
          { runtime_sec: runtimeSec, alert_sec: alertSec, engine_runtime: engRt ?? null },
        )
        raised.push('runtime_alert')
      } else if (
        runtimeSec >= warnSec &&
        runtimeSec < alertSec &&
        !recentlyAlerted(deviceId, 'runtime_warn', 600)
      ) {
        const h = (runtimeSec / 3600).toFixed(1)
        insertAlert(
          deviceId,
          'runtime_warn',
          'warn',
          `Tiempo de motor · ${h} h`,
          { runtime_sec: runtimeSec, warn_sec: warnSec, engine_runtime: engRt ?? null },
        )
        raised.push('runtime_warn')
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

    // Outdoor ice / frost
    const outdoorC =
      typeof signals.outdoor_temp_c === 'number'
        ? (signals.outdoor_temp_c as number)
        : typeof signals.outdoor_c === 'number'
          ? (signals.outdoor_c as number)
          : null
    if (typeof outdoorC === 'number') {
      const warnC =
        typeof signals.ice_warn_c === 'number' ? (signals.ice_warn_c as number) : 3
      const alertC =
        typeof signals.ice_alert_c === 'number' ? (signals.ice_alert_c as number) : 0
      if (outdoorC <= alertC && !recentlyAlerted(deviceId, 'ice_alert', 300)) {
        insertAlert(
          deviceId,
          'ice_alert',
          'critical',
          `Riesgo de hielo · ${Math.round(outdoorC)} °C`,
          { outdoor_temp_c: outdoorC, alert_c: alertC },
        )
        raised.push('ice_alert')
      } else if (
        outdoorC <= warnC &&
        outdoorC > alertC &&
        !recentlyAlerted(deviceId, 'ice_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'ice_warn',
          'warn',
          `Posible escarcha · ${Math.round(outdoorC)} °C`,
          { outdoor_temp_c: outdoorC, warn_c: warnC },
        )
        raised.push('ice_warn')
      }
    }

    // Coolant overheat
    const coolantC =
      typeof signals.coolant_c === 'number' ? (signals.coolant_c as number) : null
    if (typeof coolantC === 'number') {
      const warnC =
        typeof signals.coolant_warn_c === 'number' ? (signals.coolant_warn_c as number) : 105
      const alertC =
        typeof signals.coolant_alert_c === 'number' ? (signals.coolant_alert_c as number) : 115
      if (coolantC >= alertC && !recentlyAlerted(deviceId, 'coolant_overheat', 300)) {
        insertAlert(
          deviceId,
          'coolant_overheat',
          'critical',
          `Motor crítico · refrigerante ${Math.round(coolantC)} °C`,
          { coolant_c: coolantC, alert_c: alertC },
        )
        raised.push('coolant_overheat')
      } else if (
        coolantC >= warnC &&
        coolantC < alertC &&
        !recentlyAlerted(deviceId, 'coolant_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'coolant_warn',
          'warn',
          `Motor caliente · refrigerante ${Math.round(coolantC)} °C`,
          { coolant_c: coolantC, warn_c: warnC },
        )
        raised.push('coolant_warn')
      }
    }

    // Engine oil temperature (OBD PID 015C)
    const oilObj = signals.oil_temp as Record<string, unknown> | undefined
    const oilTempC =
      typeof oilObj?.oil_temp_c === 'number'
        ? (oilObj.oil_temp_c as number)
        : typeof signals.oil_temp_c === 'number'
          ? (signals.oil_temp_c as number)
          : null
    if (typeof oilTempC === 'number') {
      const oilWarnC =
        typeof signals.oil_temp_warn_c === 'number'
          ? (signals.oil_temp_warn_c as number)
          : 120
      const oilAlertC =
        typeof signals.oil_temp_alert_c === 'number'
          ? (signals.oil_temp_alert_c as number)
          : 130
      if (oilTempC >= oilAlertC && !recentlyAlerted(deviceId, 'oil_alert', 300)) {
        insertAlert(
          deviceId,
          'oil_alert',
          'critical',
          `Aceite crítico · ${Math.round(oilTempC)} °C`,
          { oil_temp_c: oilTempC, alert_c: oilAlertC, oil_temp: oilObj ?? null },
        )
        raised.push('oil_alert')
      } else if (
        oilTempC >= oilWarnC &&
        oilTempC < oilAlertC &&
        !recentlyAlerted(deviceId, 'oil_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'oil_warn',
          'warn',
          `Aceite caliente · ${Math.round(oilTempC)} °C`,
          { oil_temp_c: oilTempC, warn_c: oilWarnC, oil_temp: oilObj ?? null },
        )
        raised.push('oil_warn')
      }
    }

    // Catalyst temperature (OBD PID 0134)
    const catObj = signals.catalyst_temp as Record<string, unknown> | undefined
    const catalystTempC =
      typeof catObj?.catalyst_temp_c === 'number'
        ? (catObj.catalyst_temp_c as number)
        : typeof signals.catalyst_temp_c === 'number'
          ? (signals.catalyst_temp_c as number)
          : null
    if (typeof catalystTempC === 'number') {
      const catWarnC =
        typeof signals.catalyst_warn_c === 'number'
          ? (signals.catalyst_warn_c as number)
          : 750
      const catAlertC =
        typeof signals.catalyst_alert_c === 'number'
          ? (signals.catalyst_alert_c as number)
          : 850
      if (catalystTempC >= catAlertC && !recentlyAlerted(deviceId, 'catalyst_alert', 300)) {
        insertAlert(
          deviceId,
          'catalyst_alert',
          'critical',
          `Catalizador crítico · ${Math.round(catalystTempC)} °C`,
          {
            catalyst_temp_c: catalystTempC,
            alert_c: catAlertC,
            catalyst_temp: catObj ?? null,
          },
        )
        raised.push('catalyst_alert')
      } else if (
        catalystTempC >= catWarnC &&
        catalystTempC < catAlertC &&
        !recentlyAlerted(deviceId, 'catalyst_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'catalyst_warn',
          'warn',
          `Catalizador caliente · ${Math.round(catalystTempC)} °C`,
          {
            catalyst_temp_c: catalystTempC,
            warn_c: catWarnC,
            catalyst_temp: catObj ?? null,
          },
        )
        raised.push('catalyst_warn')
      }
    }

    // Intake air temperature (OBD PID 010F)
    const intakeObj = signals.intake_air as Record<string, unknown> | undefined
    const intakeAirC =
      typeof intakeObj?.intake_air_c === 'number'
        ? (intakeObj.intake_air_c as number)
        : typeof signals.intake_air_c === 'number'
          ? (signals.intake_air_c as number)
          : null
    if (typeof intakeAirC === 'number') {
      const iatWarnC =
        typeof signals.intake_air_warn_c === 'number'
          ? (signals.intake_air_warn_c as number)
          : 50
      const iatAlertC =
        typeof signals.intake_air_alert_c === 'number'
          ? (signals.intake_air_alert_c as number)
          : 60
      if (intakeAirC >= iatAlertC && !recentlyAlerted(deviceId, 'intake_alert', 300)) {
        insertAlert(
          deviceId,
          'intake_alert',
          'critical',
          `Admisión crítica · ${Math.round(intakeAirC)} °C`,
          {
            intake_air_c: intakeAirC,
            alert_c: iatAlertC,
            intake_air: intakeObj ?? null,
          },
        )
        raised.push('intake_alert')
      } else if (
        intakeAirC >= iatWarnC &&
        intakeAirC < iatAlertC &&
        !recentlyAlerted(deviceId, 'intake_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'intake_warn',
          'warn',
          `Admisión caliente · ${Math.round(intakeAirC)} °C`,
          {
            intake_air_c: intakeAirC,
            warn_c: iatWarnC,
            intake_air: intakeObj ?? null,
          },
        )
        raised.push('intake_warn')
      }
    }

    // Engine fuel rate (OBD PID 015E)
    const fuelRateObj = signals.fuel_rate as Record<string, unknown> | undefined
    let fuelRateLph: number | null =
      typeof fuelRateObj?.fuel_rate_lph === 'number'
        ? (fuelRateObj.fuel_rate_lph as number)
        : typeof signals.fuel_rate_lph === 'number'
          ? (signals.fuel_rate_lph as number)
          : typeof signals.fuel_rate_gps === 'number'
            ? ((signals.fuel_rate_gps as number) * 3600) / 740
            : null
    const fuelRateSpeedKmh =
      typeof fuelRateObj?.speed_kmh === 'number'
        ? (fuelRateObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelRateLph === 'number') {
      const warnLph =
        typeof signals.fuel_rate_warn_lph === 'number'
          ? (signals.fuel_rate_warn_lph as number)
          : 55
      const alertLph =
        typeof signals.fuel_rate_alert_lph === 'number'
          ? (signals.fuel_rate_alert_lph as number)
          : 80
      const frMinSpd =
        typeof signals.fuel_rate_speed_min_kmh === 'number'
          ? (signals.fuel_rate_speed_min_kmh as number)
          : 20
      const frSpdOk = typeof fuelRateSpeedKmh === 'number' && fuelRateSpeedKmh >= frMinSpd
      if (
        frSpdOk &&
        fuelRateLph >= alertLph &&
        !recentlyAlerted(deviceId, 'fuel_rate_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'fuel_rate_alert',
          'critical',
          `Consumo crítico · ${Math.round(fuelRateLph)} L/h`,
          {
            fuel_rate_lph: fuelRateLph,
            alert_lph: alertLph,
            fuel_rate: fuelRateObj ?? null,
          },
        )
        raised.push('fuel_rate_alert')
      } else if (
        frSpdOk &&
        fuelRateLph >= warnLph &&
        fuelRateLph < alertLph &&
        !recentlyAlerted(deviceId, 'fuel_rate_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'fuel_rate_warn',
          'warn',
          `Consumo alto · ${Math.round(fuelRateLph)} L/h`,
          {
            fuel_rate_lph: fuelRateLph,
            warn_lph: warnLph,
            fuel_rate: fuelRateObj ?? null,
          },
        )
        raised.push('fuel_rate_warn')
      }
    }

    // Mass air flow (OBD PID 0110)
    const mafObj = signals.maf_airflow as Record<string, unknown> | undefined
    let mafGps: number | null =
      typeof mafObj?.maf_gps === 'number'
        ? (mafObj.maf_gps as number)
        : typeof signals.maf_gps === 'number'
          ? (signals.maf_gps as number)
          : null
    const mafSpeedKmh =
      typeof mafObj?.speed_kmh === 'number'
        ? (mafObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof mafGps === 'number') {
      const warnMafGps =
        typeof signals.maf_warn_gps === 'number'
          ? (signals.maf_warn_gps as number)
          : 80
      const alertMafGps =
        typeof signals.maf_alert_gps === 'number'
          ? (signals.maf_alert_gps as number)
          : 110
      const mafMinSpd =
        typeof signals.maf_speed_min_kmh === 'number'
          ? (signals.maf_speed_min_kmh as number)
          : 20
      const mafSpdOk = typeof mafSpeedKmh === 'number' && mafSpeedKmh >= mafMinSpd
      if (
        mafSpdOk &&
        mafGps >= alertMafGps &&
        !recentlyAlerted(deviceId, 'maf_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'maf_alert',
          'critical',
          `MAF crítico · ${Math.round(mafGps)} g/s`,
          {
            maf_gps: mafGps,
            alert_gps: alertMafGps,
            maf_airflow: mafObj ?? null,
          },
        )
        raised.push('maf_alert')
      } else if (
        mafSpdOk &&
        mafGps >= warnMafGps &&
        mafGps < alertMafGps &&
        !recentlyAlerted(deviceId, 'maf_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'maf_warn',
          'warn',
          `MAF alto · ${Math.round(mafGps)} g/s`,
          {
            maf_gps: mafGps,
            warn_gps: warnMafGps,
            maf_airflow: mafObj ?? null,
          },
        )
        raised.push('maf_warn')
      }
    }

    // Fuel rail pressure (OBD PID 010A) — low pressure
    const fuelPressObj = signals.fuel_pressure as Record<string, unknown> | undefined
    let fuelPressKpa: number | null =
      typeof fuelPressObj?.pressure_kpa === 'number'
        ? (fuelPressObj.pressure_kpa as number)
        : typeof signals.fuel_pressure_kpa === 'number'
          ? (signals.fuel_pressure_kpa as number)
          : null
    const fuelPressSpeedKmh =
      typeof fuelPressObj?.speed_kmh === 'number'
        ? (fuelPressObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelPressKpa === 'number') {
      const warnPressKpa =
        typeof signals.fuel_press_warn_kpa === 'number'
          ? (signals.fuel_press_warn_kpa as number)
          : 280
      const alertPressKpa =
        typeof signals.fuel_press_alert_kpa === 'number'
          ? (signals.fuel_press_alert_kpa as number)
          : 220
      const pressMinSpd =
        typeof signals.fuel_press_speed_min_kmh === 'number'
          ? (signals.fuel_press_speed_min_kmh as number)
          : 20
      const pressSpdOk =
        typeof fuelPressSpeedKmh === 'number' && fuelPressSpeedKmh >= pressMinSpd
      if (
        pressSpdOk &&
        fuelPressKpa <= alertPressKpa &&
        !recentlyAlerted(deviceId, 'fuel_press_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'fuel_press_alert',
          'critical',
          `Presión combustible crítica · ${Math.round(fuelPressKpa)} kPa`,
          {
            fuel_pressure_kpa: fuelPressKpa,
            alert_kpa: alertPressKpa,
            fuel_pressure: fuelPressObj ?? null,
          },
        )
        raised.push('fuel_press_alert')
      } else if (
        pressSpdOk &&
        fuelPressKpa <= warnPressKpa &&
        fuelPressKpa > alertPressKpa &&
        !recentlyAlerted(deviceId, 'fuel_press_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'fuel_press_warn',
          'warn',
          `Presión combustible baja · ${Math.round(fuelPressKpa)} kPa`,
          {
            fuel_pressure_kpa: fuelPressKpa,
            warn_kpa: warnPressKpa,
            fuel_pressure: fuelPressObj ?? null,
          },
        )
        raised.push('fuel_press_warn')
      }
    }

    // Barometric pressure (OBD PID 0133) — out of range
    const baroObj = signals.barometric as Record<string, unknown> | undefined
    let baroKpa: number | null =
      typeof baroObj?.baro_kpa === 'number'
        ? (baroObj.baro_kpa as number)
        : typeof signals.baro_kpa === 'number'
          ? (signals.baro_kpa as number)
          : null
    const baroSpeedKmh =
      typeof baroObj?.speed_kmh === 'number'
        ? (baroObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof baroKpa === 'number') {
      const warnLow =
        typeof signals.baro_warn_low_kpa === 'number'
          ? (signals.baro_warn_low_kpa as number)
          : 88
      const alertLow =
        typeof signals.baro_alert_low_kpa === 'number'
          ? (signals.baro_alert_low_kpa as number)
          : 82
      const warnHigh =
        typeof signals.baro_warn_high_kpa === 'number'
          ? (signals.baro_warn_high_kpa as number)
          : 108
      const alertHigh =
        typeof signals.baro_alert_high_kpa === 'number'
          ? (signals.baro_alert_high_kpa as number)
          : 112
      const baroMinSpd =
        typeof signals.baro_speed_min_kmh === 'number'
          ? (signals.baro_speed_min_kmh as number)
          : 20
      const baroSpdOk =
        typeof baroSpeedKmh === 'number' && baroSpeedKmh >= baroMinSpd
      if (
        baroSpdOk &&
        (baroKpa <= alertLow || baroKpa >= alertHigh) &&
        !recentlyAlerted(deviceId, 'baro_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'baro_alert',
          'critical',
          `Barométrica crítica · ${Math.round(baroKpa)} kPa`,
          {
            baro_kpa: baroKpa,
            barometric: baroObj ?? null,
          },
        )
        raised.push('baro_alert')
      } else if (
        baroSpdOk &&
        (baroKpa <= warnLow || baroKpa >= warnHigh) &&
        baroKpa > alertLow &&
        baroKpa < alertHigh &&
        !recentlyAlerted(deviceId, 'baro_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'baro_warn',
          'warn',
          `Barométrica fuera de rango · ${Math.round(baroKpa)} kPa`,
          {
            baro_kpa: baroKpa,
            barometric: baroObj ?? null,
          },
        )
        raised.push('baro_warn')
      }
    }

    // Timing advance (OBD PID 010E) — high advance
    const timingObj = signals.timing_advance as Record<string, unknown> | undefined
    let timingDeg: number | null =
      typeof timingObj?.timing_deg === 'number'
        ? (timingObj.timing_deg as number)
        : typeof signals.timing_advance_deg === 'number'
          ? (signals.timing_advance_deg as number)
          : null
    const timingSpeedKmh =
      typeof timingObj?.speed_kmh === 'number'
        ? (timingObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const timingRpm =
      typeof timingObj?.rpm === 'number'
        ? (timingObj.rpm as number)
        : typeof signals.rpm === 'number'
          ? (signals.rpm as number)
          : null
    if (typeof timingDeg === 'number') {
      const warnDeg =
        typeof signals.timing_warn_deg === 'number'
          ? (signals.timing_warn_deg as number)
          : 38
      const alertDeg =
        typeof signals.timing_alert_deg === 'number'
          ? (signals.timing_alert_deg as number)
          : 45
      const timingMinSpd =
        typeof signals.timing_speed_min_kmh === 'number'
          ? (signals.timing_speed_min_kmh as number)
          : 20
      const timingRpmMin =
        typeof signals.timing_rpm_min === 'number'
          ? (signals.timing_rpm_min as number)
          : 800
      const timingSpdOk =
        typeof timingSpeedKmh === 'number' && timingSpeedKmh >= timingMinSpd
      const timingRpmOk =
        typeof timingRpm !== 'number' || timingRpm >= timingRpmMin
      if (
        timingSpdOk &&
        timingRpmOk &&
        timingDeg >= alertDeg &&
        !recentlyAlerted(deviceId, 'timing_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'timing_alert',
          'critical',
          `Timing crítico · ${Math.round(timingDeg)}°`,
          {
            timing_deg: timingDeg,
            timing_advance: timingObj ?? null,
          },
        )
        raised.push('timing_alert')
      } else if (
        timingSpdOk &&
        timingRpmOk &&
        timingDeg >= warnDeg &&
        timingDeg < alertDeg &&
        !recentlyAlerted(deviceId, 'timing_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'timing_warn',
          'warn',
          `Timing alto · ${Math.round(timingDeg)}°`,
          {
            timing_deg: timingDeg,
            timing_advance: timingObj ?? null,
          },
        )
        raised.push('timing_warn')
      }
    }

    // O2 voltage B1S1 (OBD PID 014A) — stuck lean/rich
    const o2Obj = signals.o2_voltage as Record<string, unknown> | undefined
    let o2Volts: number | null =
      typeof o2Obj?.o2_volts === 'number'
        ? (o2Obj.o2_volts as number)
        : typeof signals.o2_b1s1_volts === 'number'
          ? (signals.o2_b1s1_volts as number)
          : null
    const o2SpeedKmh =
      typeof o2Obj?.speed_kmh === 'number'
        ? (o2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const o2Rpm =
      typeof o2Obj?.rpm === 'number'
        ? (o2Obj.rpm as number)
        : typeof signals.rpm === 'number'
          ? (signals.rpm as number)
          : null
    if (typeof o2Volts === 'number') {
      const warnLowV =
        typeof signals.o2_warn_low_v === 'number'
          ? (signals.o2_warn_low_v as number)
          : 0.1
      const alertLowV =
        typeof signals.o2_alert_low_v === 'number'
          ? (signals.o2_alert_low_v as number)
          : 0.06
      const warnHighV =
        typeof signals.o2_warn_high_v === 'number'
          ? (signals.o2_warn_high_v as number)
          : 0.88
      const alertHighV =
        typeof signals.o2_alert_high_v === 'number'
          ? (signals.o2_alert_high_v as number)
          : 0.95
      const o2MinSpd =
        typeof signals.o2_speed_min_kmh === 'number'
          ? (signals.o2_speed_min_kmh as number)
          : 20
      const o2RpmMin =
        typeof signals.o2_rpm_min === 'number'
          ? (signals.o2_rpm_min as number)
          : 800
      const o2SpdOk =
        typeof o2SpeedKmh === 'number' && o2SpeedKmh >= o2MinSpd
      const o2RpmOk = typeof o2Rpm !== 'number' || o2Rpm >= o2RpmMin
      if (
        o2SpdOk &&
        o2RpmOk &&
        (o2Volts <= alertLowV || o2Volts >= alertHighV) &&
        !recentlyAlerted(deviceId, 'o2_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'o2_alert',
          'critical',
          `O2 crítico · ${o2Volts.toFixed(2)} V`,
          {
            o2_volts: o2Volts,
            o2_voltage: o2Obj ?? null,
          },
        )
        raised.push('o2_alert')
      } else if (
        o2SpdOk &&
        o2RpmOk &&
        (o2Volts <= warnLowV || o2Volts >= warnHighV) &&
        o2Volts > alertLowV &&
        o2Volts < alertHighV &&
        !recentlyAlerted(deviceId, 'o2_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'o2_warn',
          'warn',
          `O2 fuera de rango · ${o2Volts.toFixed(2)} V`,
          {
            o2_volts: o2Volts,
            o2_voltage: o2Obj ?? null,
          },
        )
        raised.push('o2_warn')
      }
    }

    // Absolute load (OBD PID 0143)
    const absLoadObj = signals.absolute_load as Record<string, unknown> | undefined
    let absLoadPct: number | null =
      typeof absLoadObj?.load_pct === 'number'
        ? (absLoadObj.load_pct as number)
        : typeof signals.absolute_load_pct === 'number'
          ? (signals.absolute_load_pct as number)
          : null
    const absLoadSpeed =
      typeof absLoadObj?.speed_kmh === 'number'
        ? (absLoadObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof absLoadPct === 'number') {
      const warnPct =
        typeof signals.abs_load_warn_pct === 'number'
          ? (signals.abs_load_warn_pct as number)
          : 85
      const alertPct =
        typeof signals.abs_load_alert_pct === 'number'
          ? (signals.abs_load_alert_pct as number)
          : 95
      const minSpd =
        typeof signals.abs_load_speed_min_kmh === 'number'
          ? (signals.abs_load_speed_min_kmh as number)
          : 20
      const spdOk = typeof absLoadSpeed === 'number' && absLoadSpeed >= minSpd
      if (spdOk && absLoadPct >= alertPct && !recentlyAlerted(deviceId, 'abs_load_alert', 120)) {
        insertAlert(deviceId, 'abs_load_alert', 'critical', `Carga absoluta crítica · ${Math.round(absLoadPct)}%`, {
          absolute_load_pct: absLoadPct,
          absolute_load: absLoadObj ?? null,
        })
        raised.push('abs_load_alert')
      } else if (
        spdOk &&
        absLoadPct >= warnPct &&
        absLoadPct < alertPct &&
        !recentlyAlerted(deviceId, 'abs_load_warn', 120)
      ) {
        insertAlert(deviceId, 'abs_load_warn', 'warn', `Carga absoluta alta · ${Math.round(absLoadPct)}%`, {
          absolute_load_pct: absLoadPct,
          absolute_load: absLoadObj ?? null,
        })
        raised.push('abs_load_warn')
      }
    }

    // Relative throttle (OBD PID 0145)
    const relThrObj = signals.relative_throttle as Record<string, unknown> | undefined
    let relThrPct: number | null =
      typeof relThrObj?.throttle_pct === 'number'
        ? (relThrObj.throttle_pct as number)
        : typeof signals.relative_throttle_pct === 'number'
          ? (signals.relative_throttle_pct as number)
          : null
    const relThrSpeed =
      typeof relThrObj?.speed_kmh === 'number'
        ? (relThrObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof relThrPct === 'number') {
      const warnPct =
        typeof signals.rel_thr_warn_pct === 'number'
          ? (signals.rel_thr_warn_pct as number)
          : 75
      const alertPct =
        typeof signals.rel_thr_alert_pct === 'number'
          ? (signals.rel_thr_alert_pct as number)
          : 90
      const minSpd =
        typeof signals.rel_thr_speed_min_kmh === 'number'
          ? (signals.rel_thr_speed_min_kmh as number)
          : 20
      const spdOk = typeof relThrSpeed === 'number' && relThrSpeed >= minSpd
      if (spdOk && relThrPct >= alertPct && !recentlyAlerted(deviceId, 'rel_thr_alert', 120)) {
        insertAlert(deviceId, 'rel_thr_alert', 'critical', `Acelerador relativo crítico · ${Math.round(relThrPct)}%`, {
          relative_throttle_pct: relThrPct,
          relative_throttle: relThrObj ?? null,
        })
        raised.push('rel_thr_alert')
      } else if (
        spdOk &&
        relThrPct >= warnPct &&
        relThrPct < alertPct &&
        !recentlyAlerted(deviceId, 'rel_thr_warn', 120)
      ) {
        insertAlert(deviceId, 'rel_thr_warn', 'warn', `Acelerador relativo alto · ${Math.round(relThrPct)}%`, {
          relative_throttle_pct: relThrPct,
          relative_throttle: relThrObj ?? null,
        })
        raised.push('rel_thr_warn')
      }
    }

    // Accel pedal D (OBD PID 0149)
    const pedalObj = signals.accel_pedal as Record<string, unknown> | undefined
    let pedalPct: number | null =
      typeof pedalObj?.pedal_pct === 'number'
        ? (pedalObj.pedal_pct as number)
        : typeof signals.accel_pedal_pct === 'number'
          ? (signals.accel_pedal_pct as number)
          : null
    const pedalSpeed =
      typeof pedalObj?.speed_kmh === 'number'
        ? (pedalObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof pedalPct === 'number') {
      const warnPct =
        typeof signals.accel_pedal_warn_pct === 'number'
          ? (signals.accel_pedal_warn_pct as number)
          : 80
      const alertPct =
        typeof signals.accel_pedal_alert_pct === 'number'
          ? (signals.accel_pedal_alert_pct as number)
          : 92
      const minSpd =
        typeof signals.accel_pedal_speed_min_kmh === 'number'
          ? (signals.accel_pedal_speed_min_kmh as number)
          : 20
      const spdOk = typeof pedalSpeed === 'number' && pedalSpeed >= minSpd
      if (spdOk && pedalPct >= alertPct && !recentlyAlerted(deviceId, 'accel_pedal_alert', 120)) {
        insertAlert(deviceId, 'accel_pedal_alert', 'critical', `Pedal crítico · ${Math.round(pedalPct)}%`, {
          accel_pedal_pct: pedalPct,
          accel_pedal: pedalObj ?? null,
        })
        raised.push('accel_pedal_alert')
      } else if (
        spdOk &&
        pedalPct >= warnPct &&
        pedalPct < alertPct &&
        !recentlyAlerted(deviceId, 'accel_pedal_warn', 120)
      ) {
        insertAlert(deviceId, 'accel_pedal_warn', 'warn', `Pedal alto · ${Math.round(pedalPct)}%`, {
          accel_pedal_pct: pedalPct,
          accel_pedal: pedalObj ?? null,
        })
        raised.push('accel_pedal_warn')
      }
    }

    // O2 B1S2 (OBD PID 014B)
    const o2B2Obj = signals.o2_b2_voltage as Record<string, unknown> | undefined
    let o2B2Volts: number | null =
      typeof o2B2Obj?.o2_volts === 'number'
        ? (o2B2Obj.o2_volts as number)
        : typeof signals.o2_b1s2_volts === 'number'
          ? (signals.o2_b1s2_volts as number)
          : null
    const o2B2Speed =
      typeof o2B2Obj?.speed_kmh === 'number'
        ? (o2B2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const o2B2Rpm =
      typeof o2B2Obj?.rpm === 'number'
        ? (o2B2Obj.rpm as number)
        : typeof signals.rpm === 'number'
          ? (signals.rpm as number)
          : null
    if (typeof o2B2Volts === 'number') {
      const warnLowV =
        typeof signals.o2_b2_warn_low_v === 'number'
          ? (signals.o2_b2_warn_low_v as number)
          : 0.1
      const alertLowV =
        typeof signals.o2_b2_alert_low_v === 'number'
          ? (signals.o2_b2_alert_low_v as number)
          : 0.06
      const warnHighV =
        typeof signals.o2_b2_warn_high_v === 'number'
          ? (signals.o2_b2_warn_high_v as number)
          : 0.88
      const alertHighV =
        typeof signals.o2_b2_alert_high_v === 'number'
          ? (signals.o2_b2_alert_high_v as number)
          : 0.95
      const minSpd =
        typeof signals.o2_b2_speed_min_kmh === 'number'
          ? (signals.o2_b2_speed_min_kmh as number)
          : 20
      const rpmMin =
        typeof signals.o2_b2_rpm_min === 'number'
          ? (signals.o2_b2_rpm_min as number)
          : 800
      const spdOk = typeof o2B2Speed === 'number' && o2B2Speed >= minSpd
      const rpmOk = typeof o2B2Rpm !== 'number' || o2B2Rpm >= rpmMin
      if (
        spdOk &&
        rpmOk &&
        (o2B2Volts <= alertLowV || o2B2Volts >= alertHighV) &&
        !recentlyAlerted(deviceId, 'o2_b2_alert', 120)
      ) {
        insertAlert(deviceId, 'o2_b2_alert', 'critical', `O2 B1S2 crítico · ${o2B2Volts.toFixed(2)} V`, {
          o2_b1s2_volts: o2B2Volts,
          o2_b2_voltage: o2B2Obj ?? null,
        })
        raised.push('o2_b2_alert')
      } else if (
        spdOk &&
        rpmOk &&
        (o2B2Volts <= warnLowV || o2B2Volts >= warnHighV) &&
        o2B2Volts > alertLowV &&
        o2B2Volts < alertHighV &&
        !recentlyAlerted(deviceId, 'o2_b2_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_b2_warn', 'warn', `O2 B1S2 fuera de rango · ${o2B2Volts.toFixed(2)} V`, {
          o2_b1s2_volts: o2B2Volts,
          o2_b2_voltage: o2B2Obj ?? null,
        })
        raised.push('o2_b2_warn')
      }
    }

    // EGR error (OBD PID 014D)
    const egrObj = signals.egr_error as Record<string, unknown> | undefined
    let egrPct: number | null =
      typeof egrObj?.error_pct === 'number'
        ? (egrObj.error_pct as number)
        : typeof signals.egr_error_pct === 'number'
          ? (signals.egr_error_pct as number)
          : null
    const egrSpeed =
      typeof egrObj?.speed_kmh === 'number'
        ? (egrObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof egrPct === 'number') {
      const warnPct =
        typeof signals.egr_warn_pct === 'number'
          ? (signals.egr_warn_pct as number)
          : 15
      const alertPct =
        typeof signals.egr_alert_pct === 'number'
          ? (signals.egr_alert_pct as number)
          : 25
      const minSpd =
        typeof signals.egr_speed_min_kmh === 'number'
          ? (signals.egr_speed_min_kmh as number)
          : 20
      const spdOk = typeof egrSpeed === 'number' && egrSpeed >= minSpd
      const absEgr = Math.abs(egrPct)
      if (spdOk && absEgr >= alertPct && !recentlyAlerted(deviceId, 'egr_error_alert', 120)) {
        insertAlert(deviceId, 'egr_error_alert', 'critical', `EGR crítico · ${Math.round(egrPct)}%`, {
          egr_error_pct: egrPct,
          egr_error: egrObj ?? null,
        })
        raised.push('egr_error_alert')
      } else if (
        spdOk &&
        absEgr >= warnPct &&
        absEgr < alertPct &&
        !recentlyAlerted(deviceId, 'egr_error_warn', 120)
      ) {
        insertAlert(deviceId, 'egr_error_warn', 'warn', `EGR fuera de rango · ${Math.round(egrPct)}%`, {
          egr_error_pct: egrPct,
          egr_error: egrObj ?? null,
        })
        raised.push('egr_error_warn')
      }
    }

    // Equivalence ratio (OBD PID 0144)
    const equivObj = signals.equiv_ratio_state as Record<string, unknown> | undefined
    let equivRatio: number | null =
      typeof equivObj?.ratio === 'number'
        ? (equivObj.ratio as number)
        : typeof signals.equiv_ratio === 'number'
          ? (signals.equiv_ratio as number)
          : null
    const equivSpeed =
      typeof equivObj?.speed_kmh === 'number'
        ? (equivObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const equivRpm =
      typeof equivObj?.rpm === 'number'
        ? (equivObj.rpm as number)
        : typeof signals.rpm === 'number'
          ? (signals.rpm as number)
          : null
    if (typeof equivRatio === 'number') {
      const wLo = typeof signals.equiv_warn_low === 'number' ? (signals.equiv_warn_low as number) : 0.88
      const aLo = typeof signals.equiv_alert_low === 'number' ? (signals.equiv_alert_low as number) : 0.8
      const wHi = typeof signals.equiv_warn_high === 'number' ? (signals.equiv_warn_high as number) : 1.12
      const aHi = typeof signals.equiv_alert_high === 'number' ? (signals.equiv_alert_high as number) : 1.2
      const minSpd = typeof signals.equiv_speed_min_kmh === 'number' ? (signals.equiv_speed_min_kmh as number) : 20
      const rpmMin = typeof signals.equiv_rpm_min === 'number' ? (signals.equiv_rpm_min as number) : 800
      const spdOk = typeof equivSpeed === 'number' && equivSpeed >= minSpd
      const rpmOk = typeof equivRpm !== 'number' || equivRpm >= rpmMin
      if (spdOk && rpmOk && (equivRatio <= aLo || equivRatio >= aHi) && !recentlyAlerted(deviceId, 'equiv_alert', 120)) {
        insertAlert(deviceId, 'equiv_alert', 'critical', `Lambda crítica · ${equivRatio.toFixed(2)}`, {
          equiv_ratio: equivRatio,
          equiv_ratio_state: equivObj ?? null,
        })
        raised.push('equiv_alert')
      } else if (
        spdOk &&
        rpmOk &&
        (equivRatio <= wLo || equivRatio >= wHi) &&
        equivRatio > aLo &&
        equivRatio < aHi &&
        !recentlyAlerted(deviceId, 'equiv_warn', 120)
      ) {
        insertAlert(deviceId, 'equiv_warn', 'warn', `Lambda fuera de rango · ${equivRatio.toFixed(2)}`, {
          equiv_ratio: equivRatio,
          equiv_ratio_state: equivObj ?? null,
        })
        raised.push('equiv_warn')
      }
    }

    // Evap purge (OBD PID 014E)
    const evapPurObj = signals.evap_purge as Record<string, unknown> | undefined
    let evapPurPct: number | null =
      typeof evapPurObj?.purge_pct === 'number'
        ? (evapPurObj.purge_pct as number)
        : typeof signals.evap_purge_pct === 'number'
          ? (signals.evap_purge_pct as number)
          : null
    const evapPurSpeed =
      typeof evapPurObj?.speed_kmh === 'number'
        ? (evapPurObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof evapPurPct === 'number') {
      const warnPct = typeof signals.evap_purge_warn_pct === 'number' ? (signals.evap_purge_warn_pct as number) : 55
      const alertPct = typeof signals.evap_purge_alert_pct === 'number' ? (signals.evap_purge_alert_pct as number) : 75
      const minSpd = typeof signals.evap_purge_speed_min_kmh === 'number' ? (signals.evap_purge_speed_min_kmh as number) : 20
      const spdOk = typeof evapPurSpeed === 'number' && evapPurSpeed >= minSpd
      if (spdOk && evapPurPct >= alertPct && !recentlyAlerted(deviceId, 'evap_purge_alert', 120)) {
        insertAlert(deviceId, 'evap_purge_alert', 'critical', `Purga evaporativo crítica · ${Math.round(evapPurPct)}%`, {
          evap_purge_pct: evapPurPct,
          evap_purge: evapPurObj ?? null,
        })
        raised.push('evap_purge_alert')
      } else if (
        spdOk &&
        evapPurPct >= warnPct &&
        evapPurPct < alertPct &&
        !recentlyAlerted(deviceId, 'evap_purge_warn', 120)
      ) {
        insertAlert(deviceId, 'evap_purge_warn', 'warn', `Purga evaporativo alta · ${Math.round(evapPurPct)}%`, {
          evap_purge_pct: evapPurPct,
          evap_purge: evapPurObj ?? null,
        })
        raised.push('evap_purge_warn')
      }
    }

    // Ethanol % (OBD PID 0152)
    const ethObj = signals.ethanol as Record<string, unknown> | undefined
    let ethPct: number | null =
      typeof ethObj?.ethanol_pct === 'number'
        ? (ethObj.ethanol_pct as number)
        : typeof signals.ethanol_pct === 'number'
          ? (signals.ethanol_pct as number)
          : null
    const ethSpeed =
      typeof ethObj?.speed_kmh === 'number'
        ? (ethObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof ethPct === 'number') {
      const warnPct = typeof signals.ethanol_warn_pct === 'number' ? (signals.ethanol_warn_pct as number) : 70
      const alertPct = typeof signals.ethanol_alert_pct === 'number' ? (signals.ethanol_alert_pct as number) : 85
      const minSpd = typeof signals.ethanol_speed_min_kmh === 'number' ? (signals.ethanol_speed_min_kmh as number) : 20
      const spdOk = typeof ethSpeed === 'number' && ethSpeed >= minSpd
      if (spdOk && ethPct >= alertPct && !recentlyAlerted(deviceId, 'ethanol_alert', 120)) {
        insertAlert(deviceId, 'ethanol_alert', 'critical', `Etanol crítico · ${Math.round(ethPct)}%`, {
          ethanol_pct: ethPct,
          ethanol: ethObj ?? null,
        })
        raised.push('ethanol_alert')
      } else if (
        spdOk &&
        ethPct >= warnPct &&
        ethPct < alertPct &&
        !recentlyAlerted(deviceId, 'ethanol_warn', 120)
      ) {
        insertAlert(deviceId, 'ethanol_warn', 'warn', `Etanol alto · ${Math.round(ethPct)}%`, {
          ethanol_pct: ethPct,
          ethanol: ethObj ?? null,
        })
        raised.push('ethanol_warn')
      }
    }

    // Evap vapor Pa (OBD PID 0153)
    const vapObj = signals.evap_vapor as Record<string, unknown> | undefined
    let vapPa: number | null =
      typeof vapObj?.pressure_pa === 'number'
        ? (vapObj.pressure_pa as number)
        : typeof signals.evap_vapor_pa === 'number'
          ? (signals.evap_vapor_pa as number)
          : null
    const vapSpeed =
      typeof vapObj?.speed_kmh === 'number'
        ? (vapObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof vapPa === 'number') {
      const warnPa = typeof signals.evap_vapor_warn_pa === 'number' ? (signals.evap_vapor_warn_pa as number) : 5000
      const alertPa = typeof signals.evap_vapor_alert_pa === 'number' ? (signals.evap_vapor_alert_pa as number) : 8000
      const minSpd = typeof signals.evap_vapor_speed_min_kmh === 'number' ? (signals.evap_vapor_speed_min_kmh as number) : 20
      const spdOk = typeof vapSpeed === 'number' && vapSpeed >= minSpd
      const absPa = Math.abs(vapPa)
      if (spdOk && absPa >= alertPa && !recentlyAlerted(deviceId, 'evap_vapor_alert', 120)) {
        insertAlert(deviceId, 'evap_vapor_alert', 'critical', `Vapor evaporativo crítico · ${Math.round(vapPa)} Pa`, {
          evap_vapor_pa: vapPa,
          evap_vapor: vapObj ?? null,
        })
        raised.push('evap_vapor_alert')
      } else if (
        spdOk &&
        absPa >= warnPa &&
        absPa < alertPa &&
        !recentlyAlerted(deviceId, 'evap_vapor_warn', 120)
      ) {
        insertAlert(deviceId, 'evap_vapor_warn', 'warn', `Vapor evaporativo alto · ${Math.round(vapPa)} Pa`, {
          evap_vapor_pa: vapPa,
          evap_vapor: vapObj ?? null,
        })
        raised.push('evap_vapor_warn')
      }
    }

    // Fuel rail abs kPa (OBD PID 0159)
    const railObj = signals.fuel_rail_abs as Record<string, unknown> | undefined
    let railKpa: number | null =
      typeof railObj?.pressure_kpa === 'number'
        ? (railObj.pressure_kpa as number)
        : typeof signals.fuel_rail_abs_kpa === 'number'
          ? (signals.fuel_rail_abs_kpa as number)
          : null
    const railSpeed =
      typeof railObj?.speed_kmh === 'number'
        ? (railObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof railKpa === 'number') {
      const warnKpa = typeof signals.rail_abs_warn_kpa === 'number' ? (signals.rail_abs_warn_kpa as number) : 8000
      const alertKpa = typeof signals.rail_abs_alert_kpa === 'number' ? (signals.rail_abs_alert_kpa as number) : 6000
      const minSpd = typeof signals.rail_abs_speed_min_kmh === 'number' ? (signals.rail_abs_speed_min_kmh as number) : 20
      const spdOk = typeof railSpeed === 'number' && railSpeed >= minSpd
      if (spdOk && railKpa <= alertKpa && !recentlyAlerted(deviceId, 'rail_abs_alert', 120)) {
        insertAlert(deviceId, 'rail_abs_alert', 'critical', `Rail abs crítico · ${Math.round(railKpa)} kPa`, {
          fuel_rail_abs_kpa: railKpa,
          fuel_rail_abs: railObj ?? null,
        })
        raised.push('rail_abs_alert')
      } else if (
        spdOk &&
        railKpa <= warnKpa &&
        railKpa > alertKpa &&
        !recentlyAlerted(deviceId, 'rail_abs_warn', 120)
      ) {
        insertAlert(deviceId, 'rail_abs_warn', 'warn', `Rail abs bajo · ${Math.round(railKpa)} kPa`, {
          fuel_rail_abs_kpa: railKpa,
          fuel_rail_abs: railObj ?? null,
        })
        raised.push('rail_abs_warn')
      }
    }

    // Commanded EGR % (OBD PID 014C)
    const egrCmdObj = signals.egr_cmd as Record<string, unknown> | undefined
    let egrCmdPct: number | null =
      typeof egrCmdObj?.egr_pct === 'number'
        ? (egrCmdObj.egr_pct as number)
        : typeof signals.egr_cmd_pct === 'number'
          ? (signals.egr_cmd_pct as number)
          : null
    const egrCmdSpeed =
      typeof egrCmdObj?.speed_kmh === 'number'
        ? (egrCmdObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof egrCmdPct === 'number') {
      const warnPct = typeof signals.egr_cmd_warn_pct === 'number' ? (signals.egr_cmd_warn_pct as number) : 50
      const alertPct = typeof signals.egr_cmd_alert_pct === 'number' ? (signals.egr_cmd_alert_pct as number) : 70
      const minSpd = typeof signals.egr_cmd_speed_min_kmh === 'number' ? (signals.egr_cmd_speed_min_kmh as number) : 20
      const spdOk = typeof egrCmdSpeed === 'number' && egrCmdSpeed >= minSpd
      if (spdOk && egrCmdPct >= alertPct && !recentlyAlerted(deviceId, 'egr_cmd_alert', 120)) {
        insertAlert(deviceId, 'egr_cmd_alert', 'critical', `EGR comandado crítico · ${Math.round(egrCmdPct)}%`, {
          egr_cmd_pct: egrCmdPct,
          egr_cmd: egrCmdObj ?? null,
        })
        raised.push('egr_cmd_alert')
      } else if (
        spdOk &&
        egrCmdPct >= warnPct &&
        egrCmdPct < alertPct &&
        !recentlyAlerted(deviceId, 'egr_cmd_warn', 120)
      ) {
        insertAlert(deviceId, 'egr_cmd_warn', 'warn', `EGR comandado alto · ${Math.round(egrCmdPct)}%`, {
          egr_cmd_pct: egrCmdPct,
          egr_cmd: egrCmdObj ?? null,
        })
        raised.push('egr_cmd_warn')
      }
    }

    // Relative accel pedal % (OBD PID 015A)
    const relApedObj = signals.rel_aped as Record<string, unknown> | undefined
    let relApedPct: number | null =
      typeof relApedObj?.pedal_pct === 'number'
        ? (relApedObj.pedal_pct as number)
        : typeof signals.rel_accel_pedal_pct === 'number'
          ? (signals.rel_accel_pedal_pct as number)
          : null
    const relApedSpeed =
      typeof relApedObj?.speed_kmh === 'number'
        ? (relApedObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof relApedPct === 'number') {
      const warnPct = typeof signals.rel_aped_warn_pct === 'number' ? (signals.rel_aped_warn_pct as number) : 78
      const alertPct = typeof signals.rel_aped_alert_pct === 'number' ? (signals.rel_aped_alert_pct as number) : 90
      const minSpd = typeof signals.rel_aped_speed_min_kmh === 'number' ? (signals.rel_aped_speed_min_kmh as number) : 20
      const spdOk = typeof relApedSpeed === 'number' && relApedSpeed >= minSpd
      if (spdOk && relApedPct >= alertPct && !recentlyAlerted(deviceId, 'rel_aped_alert', 120)) {
        insertAlert(deviceId, 'rel_aped_alert', 'critical', `Pedal relativo crítico · ${Math.round(relApedPct)}%`, {
          rel_accel_pedal_pct: relApedPct,
          rel_aped: relApedObj ?? null,
        })
        raised.push('rel_aped_alert')
      } else if (
        spdOk &&
        relApedPct >= warnPct &&
        relApedPct < alertPct &&
        !recentlyAlerted(deviceId, 'rel_aped_warn', 120)
      ) {
        insertAlert(deviceId, 'rel_aped_warn', 'warn', `Pedal relativo alto · ${Math.round(relApedPct)}%`, {
          rel_accel_pedal_pct: relApedPct,
          rel_aped: relApedObj ?? null,
        })
        raised.push('rel_aped_warn')
      }
    }

    // Driver demand torque % (OBD PID 0161)
    const drvTorqueObj = signals.drv_torque as Record<string, unknown> | undefined
    let drvTorquePct: number | null =
      typeof drvTorqueObj?.torque_pct === 'number'
        ? (drvTorqueObj.torque_pct as number)
        : typeof signals.driver_torque_pct === 'number'
          ? (signals.driver_torque_pct as number)
          : null
    const drvTorqueSpeed =
      typeof drvTorqueObj?.speed_kmh === 'number'
        ? (drvTorqueObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof drvTorquePct === 'number') {
      const warnPct = typeof signals.drv_torque_warn_pct === 'number' ? (signals.drv_torque_warn_pct as number) : 40
      const alertPct = typeof signals.drv_torque_alert_pct === 'number' ? (signals.drv_torque_alert_pct as number) : 55
      const minSpd = typeof signals.drv_torque_speed_min_kmh === 'number' ? (signals.drv_torque_speed_min_kmh as number) : 20
      const spdOk = typeof drvTorqueSpeed === 'number' && drvTorqueSpeed >= minSpd
      const absT = Math.abs(drvTorquePct)
      if (spdOk && absT >= alertPct && !recentlyAlerted(deviceId, 'drv_torque_alert', 120)) {
        insertAlert(deviceId, 'drv_torque_alert', 'critical', `Demanda torque crítica · ${Math.round(drvTorquePct)}%`, {
          driver_torque_pct: drvTorquePct,
          drv_torque: drvTorqueObj ?? null,
        })
        raised.push('drv_torque_alert')
      } else if (
        spdOk &&
        absT >= warnPct &&
        absT < alertPct &&
        !recentlyAlerted(deviceId, 'drv_torque_warn', 120)
      ) {
        insertAlert(deviceId, 'drv_torque_warn', 'warn', `Demanda torque alta · ${Math.round(drvTorquePct)}%`, {
          driver_torque_pct: drvTorquePct,
          drv_torque: drvTorqueObj ?? null,
        })
        raised.push('drv_torque_warn')
      }
    }

    // Actual engine torque % (OBD PID 0162)
    const actTorqueObj = signals.act_torque as Record<string, unknown> | undefined
    let actTorquePct: number | null =
      typeof actTorqueObj?.torque_pct === 'number'
        ? (actTorqueObj.torque_pct as number)
        : typeof signals.actual_torque_pct === 'number'
          ? (signals.actual_torque_pct as number)
          : null
    const actTorqueSpeed =
      typeof actTorqueObj?.speed_kmh === 'number'
        ? (actTorqueObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof actTorquePct === 'number') {
      const warnPct = typeof signals.act_torque_warn_pct === 'number' ? (signals.act_torque_warn_pct as number) : 40
      const alertPct = typeof signals.act_torque_alert_pct === 'number' ? (signals.act_torque_alert_pct as number) : 55
      const minSpd = typeof signals.act_torque_speed_min_kmh === 'number' ? (signals.act_torque_speed_min_kmh as number) : 20
      const spdOk = typeof actTorqueSpeed === 'number' && actTorqueSpeed >= minSpd
      const absT = Math.abs(actTorquePct)
      if (spdOk && absT >= alertPct && !recentlyAlerted(deviceId, 'act_torque_alert', 120)) {
        insertAlert(deviceId, 'act_torque_alert', 'critical', `Torque real crítico · ${Math.round(actTorquePct)}%`, {
          actual_torque_pct: actTorquePct,
          act_torque: actTorqueObj ?? null,
        })
        raised.push('act_torque_alert')
      } else if (
        spdOk &&
        absT >= warnPct &&
        absT < alertPct &&
        !recentlyAlerted(deviceId, 'act_torque_warn', 120)
      ) {
        insertAlert(deviceId, 'act_torque_warn', 'warn', `Torque real alto · ${Math.round(actTorquePct)}%`, {
          actual_torque_pct: actTorquePct,
          act_torque: actTorqueObj ?? null,
        })
        raised.push('act_torque_warn')
      }
    }

    // Catalyst temp bank 2 °C (OBD PID 0170)
    const catB2Obj = signals.catalyst_b2 as Record<string, unknown> | undefined
    const catalystB2TempC =
      typeof catB2Obj?.catalyst_temp_c === 'number'
        ? (catB2Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2_temp_c === 'number'
          ? (signals.catalyst_b2_temp_c as number)
          : null
    if (typeof catalystB2TempC === 'number') {
      const catB2WarnC = typeof signals.cat_b2_warn_c === 'number' ? (signals.cat_b2_warn_c as number) : 750
      const catB2AlertC = typeof signals.cat_b2_alert_c === 'number' ? (signals.cat_b2_alert_c as number) : 850
      if (catalystB2TempC >= catB2AlertC && !recentlyAlerted(deviceId, 'cat_b2_alert', 300)) {
        insertAlert(
          deviceId,
          'cat_b2_alert',
          'critical',
          `Catalizador B2 crítico · ${Math.round(catalystB2TempC)} °C`,
          {
            catalyst_b2_temp_c: catalystB2TempC,
            alert_c: catB2AlertC,
            catalyst_b2: catB2Obj ?? null,
          },
        )
        raised.push('cat_b2_alert')
      } else if (
        catalystB2TempC >= catB2WarnC &&
        catalystB2TempC < catB2AlertC &&
        !recentlyAlerted(deviceId, 'cat_b2_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'cat_b2_warn',
          'warn',
          `Catalizador B2 caliente · ${Math.round(catalystB2TempC)} °C`,
          {
            catalyst_b2_temp_c: catalystB2TempC,
            warn_c: catB2WarnC,
            catalyst_b2: catB2Obj ?? null,
          },
        )
        raised.push('cat_b2_warn')
      }
    }

    // Catalyst temp B1S2 (OBD PID 0171)
    const catB1s2Obj = signals.catalyst_b1s2 as Record<string, unknown> | undefined
    const catalystB1s2TempC =
      typeof catB1s2Obj?.catalyst_temp_c === 'number'
        ? (catB1s2Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s2_temp_c === 'number'
          ? (signals.catalyst_b1s2_temp_c as number)
          : null
    if (typeof catalystB1s2TempC === 'number') {
      const warnC = typeof signals.cat_b1s2_warn_c === 'number' ? (signals.cat_b1s2_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s2_alert_c === 'number' ? (signals.cat_b1s2_alert_c as number) : 850
      if (catalystB1s2TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s2_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s2_alert', 'critical', `Catalizador B1S2 crítico · ${Math.round(catalystB1s2TempC)} °C`, {
          catalyst_b1s2_temp_c: catalystB1s2TempC,
          catalyst_b1s2: catB1s2Obj ?? null,
        })
        raised.push('cat_b1s2_alert')
      } else if (
        catalystB1s2TempC >= warnC &&
        catalystB1s2TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s2_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s2_warn', 'warn', `Catalizador B1S2 caliente · ${Math.round(catalystB1s2TempC)} °C`, {
          catalyst_b1s2_temp_c: catalystB1s2TempC,
          catalyst_b1s2: catB1s2Obj ?? null,
        })
        raised.push('cat_b1s2_warn')
      }
    }

    // Catalyst temp B2S2 (OBD PID 0172)
    const catB2s2Obj = signals.catalyst_b2s2 as Record<string, unknown> | undefined
    const catalystB2s2TempC =
      typeof catB2s2Obj?.catalyst_temp_c === 'number'
        ? (catB2s2Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s2_temp_c === 'number'
          ? (signals.catalyst_b2s2_temp_c as number)
          : null
    if (typeof catalystB2s2TempC === 'number') {
      const warnC = typeof signals.cat_b2s2_warn_c === 'number' ? (signals.cat_b2s2_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s2_alert_c === 'number' ? (signals.cat_b2s2_alert_c as number) : 850
      if (catalystB2s2TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s2_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s2_alert', 'critical', `Catalizador B2S2 crítico · ${Math.round(catalystB2s2TempC)} °C`, {
          catalyst_b2s2_temp_c: catalystB2s2TempC,
          catalyst_b2s2: catB2s2Obj ?? null,
        })
        raised.push('cat_b2s2_alert')
      } else if (
        catalystB2s2TempC >= warnC &&
        catalystB2s2TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s2_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s2_warn', 'warn', `Catalizador B2S2 caliente · ${Math.round(catalystB2s2TempC)} °C`, {
          catalyst_b2s2_temp_c: catalystB2s2TempC,
          catalyst_b2s2: catB2s2Obj ?? null,
        })
        raised.push('cat_b2s2_warn')
      }
    }

    // Catalyst temp B1S3 (OBD PID 0173)
    const catB1s3Obj = signals.catalyst_b1s3 as Record<string, unknown> | undefined
    const catalystB1s3TempC =
      typeof catB1s3Obj?.catalyst_temp_c === 'number'
        ? (catB1s3Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s3_temp_c === 'number'
          ? (signals.catalyst_b1s3_temp_c as number)
          : null
    if (typeof catalystB1s3TempC === 'number') {
      const warnC = typeof signals.cat_b1s3_warn_c === 'number' ? (signals.cat_b1s3_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s3_alert_c === 'number' ? (signals.cat_b1s3_alert_c as number) : 850
      if (catalystB1s3TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s3_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s3_alert', 'critical', `Catalizador B1S3 crítico · ${Math.round(catalystB1s3TempC)} °C`, {
          catalyst_b1s3_temp_c: catalystB1s3TempC,
          catalyst_b1s3: catB1s3Obj ?? null,
        })
        raised.push('cat_b1s3_alert')
      } else if (
        catalystB1s3TempC >= warnC &&
        catalystB1s3TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s3_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s3_warn', 'warn', `Catalizador B1S3 caliente · ${Math.round(catalystB1s3TempC)} °C`, {
          catalyst_b1s3_temp_c: catalystB1s3TempC,
          catalyst_b1s3: catB1s3Obj ?? null,
        })
        raised.push('cat_b1s3_warn')
      }
    }

    // Catalyst temp B2S3 (OBD PID 0174)
    const catB2s3Obj = signals.catalyst_b2s3 as Record<string, unknown> | undefined
    const catalystB2s3TempC =
      typeof catB2s3Obj?.catalyst_temp_c === 'number'
        ? (catB2s3Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s3_temp_c === 'number'
          ? (signals.catalyst_b2s3_temp_c as number)
          : null
    if (typeof catalystB2s3TempC === 'number') {
      const warnC = typeof signals.cat_b2s3_warn_c === 'number' ? (signals.cat_b2s3_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s3_alert_c === 'number' ? (signals.cat_b2s3_alert_c as number) : 850
      if (catalystB2s3TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s3_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s3_alert', 'critical', `Catalizador B2S3 crítico · ${Math.round(catalystB2s3TempC)} °C`, {
          catalyst_b2s3_temp_c: catalystB2s3TempC,
          catalyst_b2s3: catB2s3Obj ?? null,
        })
        raised.push('cat_b2s3_alert')
      } else if (
        catalystB2s3TempC >= warnC &&
        catalystB2s3TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s3_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s3_warn', 'warn', `Catalizador B2S3 caliente · ${Math.round(catalystB2s3TempC)} °C`, {
          catalyst_b2s3_temp_c: catalystB2s3TempC,
          catalyst_b2s3: catB2s3Obj ?? null,
        })
        raised.push('cat_b2s3_warn')
      }
    }

    // Catalyst temp B1S4 (OBD PID 0175)
    const catB1s4Obj = signals.catalyst_b1s4 as Record<string, unknown> | undefined
    const catalystB1s4TempC =
      typeof catB1s4Obj?.catalyst_temp_c === 'number'
        ? (catB1s4Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s4_temp_c === 'number'
          ? (signals.catalyst_b1s4_temp_c as number)
          : null
    if (typeof catalystB1s4TempC === 'number') {
      const warnC = typeof signals.cat_b1s4_warn_c === 'number' ? (signals.cat_b1s4_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s4_alert_c === 'number' ? (signals.cat_b1s4_alert_c as number) : 850
      if (catalystB1s4TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s4_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s4_alert', 'critical', `Catalizador B1S4 crítico · ${Math.round(catalystB1s4TempC)} °C`, {
          catalyst_b1s4_temp_c: catalystB1s4TempC,
          catalyst_b1s4: catB1s4Obj ?? null,
        })
        raised.push('cat_b1s4_alert')
      } else if (
        catalystB1s4TempC >= warnC &&
        catalystB1s4TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s4_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s4_warn', 'warn', `Catalizador B1S4 caliente · ${Math.round(catalystB1s4TempC)} °C`, {
          catalyst_b1s4_temp_c: catalystB1s4TempC,
          catalyst_b1s4: catB1s4Obj ?? null,
        })
        raised.push('cat_b1s4_warn')
      }
    }

    // Catalyst temp B2S4 (OBD PID 0176)
    const catB2s4Obj = signals.catalyst_b2s4 as Record<string, unknown> | undefined
    const catalystB2s4TempC =
      typeof catB2s4Obj?.catalyst_temp_c === 'number'
        ? (catB2s4Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s4_temp_c === 'number'
          ? (signals.catalyst_b2s4_temp_c as number)
          : null
    if (typeof catalystB2s4TempC === 'number') {
      const warnC = typeof signals.cat_b2s4_warn_c === 'number' ? (signals.cat_b2s4_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s4_alert_c === 'number' ? (signals.cat_b2s4_alert_c as number) : 850
      if (catalystB2s4TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s4_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s4_alert', 'critical', `Catalizador B2S4 crítico · ${Math.round(catalystB2s4TempC)} °C`, {
          catalyst_b2s4_temp_c: catalystB2s4TempC,
          catalyst_b2s4: catB2s4Obj ?? null,
        })
        raised.push('cat_b2s4_alert')
      } else if (
        catalystB2s4TempC >= warnC &&
        catalystB2s4TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s4_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s4_warn', 'warn', `Catalizador B2S4 caliente · ${Math.round(catalystB2s4TempC)} °C`, {
          catalyst_b2s4_temp_c: catalystB2s4TempC,
          catalyst_b2s4: catB2s4Obj ?? null,
        })
        raised.push('cat_b2s4_warn')
      }
    }

    // STFT secondary O2 B1 (OBD PID 0155)
    const stft2B1Obj = signals.stft2_b1 as Record<string, unknown> | undefined
    let stft2B1Pct: number | null =
      typeof stft2B1Obj?.trim_pct === 'number'
        ? (stft2B1Obj.trim_pct as number)
        : typeof signals.fuel_trim_stft2_b1_pct === 'number'
          ? (signals.fuel_trim_stft2_b1_pct as number)
          : null
    const stft2B1Speed =
      typeof stft2B1Obj?.speed_kmh === 'number'
        ? (stft2B1Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof stft2B1Pct === 'number') {
      const warnPct = typeof signals.stft2_b1_warn_pct === 'number' ? (signals.stft2_b1_warn_pct as number) : 12
      const alertPct = typeof signals.stft2_b1_alert_pct === 'number' ? (signals.stft2_b1_alert_pct as number) : 20
      const minSpd = typeof signals.stft2_b1_speed_min_kmh === 'number' ? (signals.stft2_b1_speed_min_kmh as number) : 20
      const spdOk = typeof stft2B1Speed === 'number' && stft2B1Speed >= minSpd
      const absT = Math.abs(stft2B1Pct)
      if (spdOk && absT >= alertPct && !recentlyAlerted(deviceId, 'stft2_b1_alert', 120)) {
        insertAlert(deviceId, 'stft2_b1_alert', 'critical', `STFT O2 sec B1 crítico · ${Math.round(stft2B1Pct)}%`, {
          fuel_trim_stft2_b1_pct: stft2B1Pct,
          stft2_b1: stft2B1Obj ?? null,
        })
        raised.push('stft2_b1_alert')
      } else if (
        spdOk &&
        absT >= warnPct &&
        absT < alertPct &&
        !recentlyAlerted(deviceId, 'stft2_b1_warn', 120)
      ) {
        insertAlert(deviceId, 'stft2_b1_warn', 'warn', `STFT O2 sec B1 alto · ${Math.round(stft2B1Pct)}%`, {
          fuel_trim_stft2_b1_pct: stft2B1Pct,
          stft2_b1: stft2B1Obj ?? null,
        })
        raised.push('stft2_b1_warn')
      }
    }

    // LTFT secondary O2 B1 (OBD PID 0156)
    const ltft2B1Obj = signals.ltft2_b1 as Record<string, unknown> | undefined
    let ltft2B1Pct: number | null =
      typeof ltft2B1Obj?.trim_pct === 'number'
        ? (ltft2B1Obj.trim_pct as number)
        : typeof signals.fuel_trim_ltft2_b1_pct === 'number'
          ? (signals.fuel_trim_ltft2_b1_pct as number)
          : null
    const ltft2B1Speed =
      typeof ltft2B1Obj?.speed_kmh === 'number'
        ? (ltft2B1Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof ltft2B1Pct === 'number') {
      const warnPct = typeof signals.ltft2_b1_warn_pct === 'number' ? (signals.ltft2_b1_warn_pct as number) : 12
      const alertPct = typeof signals.ltft2_b1_alert_pct === 'number' ? (signals.ltft2_b1_alert_pct as number) : 20
      const minSpd = typeof signals.ltft2_b1_speed_min_kmh === 'number' ? (signals.ltft2_b1_speed_min_kmh as number) : 20
      const spdOk = typeof ltft2B1Speed === 'number' && ltft2B1Speed >= minSpd
      const absT = Math.abs(ltft2B1Pct)
      if (spdOk && absT >= alertPct && !recentlyAlerted(deviceId, 'ltft2_b1_alert', 120)) {
        insertAlert(deviceId, 'ltft2_b1_alert', 'critical', `LTFT O2 sec B1 crítico · ${Math.round(ltft2B1Pct)}%`, {
          fuel_trim_ltft2_b1_pct: ltft2B1Pct,
          ltft2_b1: ltft2B1Obj ?? null,
        })
        raised.push('ltft2_b1_alert')
      } else if (
        spdOk &&
        absT >= warnPct &&
        absT < alertPct &&
        !recentlyAlerted(deviceId, 'ltft2_b1_warn', 120)
      ) {
        insertAlert(deviceId, 'ltft2_b1_warn', 'warn', `LTFT O2 sec B1 alto · ${Math.round(ltft2B1Pct)}%`, {
          fuel_trim_ltft2_b1_pct: ltft2B1Pct,
          ltft2_b1: ltft2B1Obj ?? null,
        })
        raised.push('ltft2_b1_warn')
      }
    }

    // STFT secondary O2 B2 (OBD PID 0157)
    const stft2B2Obj = signals.stft2_b2 as Record<string, unknown> | undefined
    let stft2B2Pct: number | null =
      typeof stft2B2Obj?.trim_pct === 'number'
        ? (stft2B2Obj.trim_pct as number)
        : typeof signals.fuel_trim_stft2_b2_pct === 'number'
          ? (signals.fuel_trim_stft2_b2_pct as number)
          : null
    const stft2B2Speed =
      typeof stft2B2Obj?.speed_kmh === 'number'
        ? (stft2B2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof stft2B2Pct === 'number') {
      const warnPct = typeof signals.stft2_b2_warn_pct === 'number' ? (signals.stft2_b2_warn_pct as number) : 12
      const alertPct = typeof signals.stft2_b2_alert_pct === 'number' ? (signals.stft2_b2_alert_pct as number) : 20
      const minSpd = typeof signals.stft2_b2_speed_min_kmh === 'number' ? (signals.stft2_b2_speed_min_kmh as number) : 20
      const spdOk = typeof stft2B2Speed === 'number' && stft2B2Speed >= minSpd
      const absT = Math.abs(stft2B2Pct)
      if (spdOk && absT >= alertPct && !recentlyAlerted(deviceId, 'stft2_b2_alert', 120)) {
        insertAlert(deviceId, 'stft2_b2_alert', 'critical', `STFT O2 sec B2 crítico · ${Math.round(stft2B2Pct)}%`, {
          fuel_trim_stft2_b2_pct: stft2B2Pct,
          stft2_b2: stft2B2Obj ?? null,
        })
        raised.push('stft2_b2_alert')
      } else if (
        spdOk &&
        absT >= warnPct &&
        absT < alertPct &&
        !recentlyAlerted(deviceId, 'stft2_b2_warn', 120)
      ) {
        insertAlert(deviceId, 'stft2_b2_warn', 'warn', `STFT O2 sec B2 alto · ${Math.round(stft2B2Pct)}%`, {
          fuel_trim_stft2_b2_pct: stft2B2Pct,
          stft2_b2: stft2B2Obj ?? null,
        })
        raised.push('stft2_b2_warn')
      }
    }

    // LTFT secondary O2 B2 (OBD PID 0158)
    const ltft2B2Obj = signals.ltft2_b2 as Record<string, unknown> | undefined
    let ltft2B2Pct: number | null =
      typeof ltft2B2Obj?.trim_pct === 'number'
        ? (ltft2B2Obj.trim_pct as number)
        : typeof signals.fuel_trim_ltft2_b2_pct === 'number'
          ? (signals.fuel_trim_ltft2_b2_pct as number)
          : null
    const ltft2B2Speed =
      typeof ltft2B2Obj?.speed_kmh === 'number'
        ? (ltft2B2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof ltft2B2Pct === 'number') {
      const warnPct = typeof signals.ltft2_b2_warn_pct === 'number' ? (signals.ltft2_b2_warn_pct as number) : 12
      const alertPct = typeof signals.ltft2_b2_alert_pct === 'number' ? (signals.ltft2_b2_alert_pct as number) : 20
      const minSpd = typeof signals.ltft2_b2_speed_min_kmh === 'number' ? (signals.ltft2_b2_speed_min_kmh as number) : 20
      const spdOk = typeof ltft2B2Speed === 'number' && ltft2B2Speed >= minSpd
      const absT = Math.abs(ltft2B2Pct)
      if (spdOk && absT >= alertPct && !recentlyAlerted(deviceId, 'ltft2_b2_alert', 120)) {
        insertAlert(deviceId, 'ltft2_b2_alert', 'critical', `LTFT O2 sec B2 crítico · ${Math.round(ltft2B2Pct)}%`, {
          fuel_trim_ltft2_b2_pct: ltft2B2Pct,
          ltft2_b2: ltft2B2Obj ?? null,
        })
        raised.push('ltft2_b2_alert')
      } else if (
        spdOk &&
        absT >= warnPct &&
        absT < alertPct &&
        !recentlyAlerted(deviceId, 'ltft2_b2_warn', 120)
      ) {
        insertAlert(deviceId, 'ltft2_b2_warn', 'warn', `LTFT O2 sec B2 alto · ${Math.round(ltft2B2Pct)}%`, {
          fuel_trim_ltft2_b2_pct: ltft2B2Pct,
          ltft2_b2: ltft2B2Obj ?? null,
        })
        raised.push('ltft2_b2_warn')
      }
    }

    // Catalyst temp B1S5 (OBD PID 0177)
    const catB1s5Obj = signals.catalyst_b1s5 as Record<string, unknown> | undefined
    const catalystB1s5TempC =
      typeof catB1s5Obj?.catalyst_temp_c === 'number'
        ? (catB1s5Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s5_temp_c === 'number'
          ? (signals.catalyst_b1s5_temp_c as number)
          : null
    if (typeof catalystB1s5TempC === 'number') {
      const warnC = typeof signals.cat_b1s5_warn_c === 'number' ? (signals.cat_b1s5_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s5_alert_c === 'number' ? (signals.cat_b1s5_alert_c as number) : 850
      if (catalystB1s5TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s5_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s5_alert', 'critical', `Catalizador B1S5 crítico · ${Math.round(catalystB1s5TempC)} °C`, {
          catalyst_b1s5_temp_c: catalystB1s5TempC,
          catalyst_b1s5: catB1s5Obj ?? null,
        })
        raised.push('cat_b1s5_alert')
      } else if (
        catalystB1s5TempC >= warnC &&
        catalystB1s5TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s5_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s5_warn', 'warn', `Catalizador B1S5 caliente · ${Math.round(catalystB1s5TempC)} °C`, {
          catalyst_b1s5_temp_c: catalystB1s5TempC,
          catalyst_b1s5: catB1s5Obj ?? null,
        })
        raised.push('cat_b1s5_warn')
      }
    }

    // Catalyst temp B2S5 (OBD PID 0178)
    const catB2s5Obj = signals.catalyst_b2s5 as Record<string, unknown> | undefined
    const catalystB2s5TempC =
      typeof catB2s5Obj?.catalyst_temp_c === 'number'
        ? (catB2s5Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s5_temp_c === 'number'
          ? (signals.catalyst_b2s5_temp_c as number)
          : null
    if (typeof catalystB2s5TempC === 'number') {
      const warnC = typeof signals.cat_b2s5_warn_c === 'number' ? (signals.cat_b2s5_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s5_alert_c === 'number' ? (signals.cat_b2s5_alert_c as number) : 850
      if (catalystB2s5TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s5_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s5_alert', 'critical', `Catalizador B2S5 crítico · ${Math.round(catalystB2s5TempC)} °C`, {
          catalyst_b2s5_temp_c: catalystB2s5TempC,
          catalyst_b2s5: catB2s5Obj ?? null,
        })
        raised.push('cat_b2s5_alert')
      } else if (
        catalystB2s5TempC >= warnC &&
        catalystB2s5TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s5_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s5_warn', 'warn', `Catalizador B2S5 caliente · ${Math.round(catalystB2s5TempC)} °C`, {
          catalyst_b2s5_temp_c: catalystB2s5TempC,
          catalyst_b2s5: catB2s5Obj ?? null,
        })
        raised.push('cat_b2s5_warn')
      }
    }

    // Fuel injection timing (OBD PID 015D)
    const injectObj = signals.fuel_inject as Record<string, unknown> | undefined
    let injectDeg: number | null =
      typeof injectObj?.timing_deg === 'number'
        ? (injectObj.timing_deg as number)
        : typeof signals.fuel_inject_timing_deg === 'number'
          ? (signals.fuel_inject_timing_deg as number)
          : null
    const injectSpeed =
      typeof injectObj?.speed_kmh === 'number'
        ? (injectObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof injectDeg === 'number') {
      const warnDeg = typeof signals.inject_warn_deg === 'number' ? (signals.inject_warn_deg as number) : 28
      const alertDeg = typeof signals.inject_alert_deg === 'number' ? (signals.inject_alert_deg as number) : 40
      const minSpd = typeof signals.inject_speed_min_kmh === 'number' ? (signals.inject_speed_min_kmh as number) : 20
      const spdOk = typeof injectSpeed === 'number' && injectSpeed >= minSpd
      const absD = Math.abs(injectDeg)
      if (spdOk && absD >= alertDeg && !recentlyAlerted(deviceId, 'inject_alert', 120)) {
        insertAlert(deviceId, 'inject_alert', 'critical', `Inyección crítica · ${Math.round(injectDeg)}°`, {
          fuel_inject_timing_deg: injectDeg,
          fuel_inject: injectObj ?? null,
        })
        raised.push('inject_alert')
      } else if (
        spdOk &&
        absD >= warnDeg &&
        absD < alertDeg &&
        !recentlyAlerted(deviceId, 'inject_warn', 120)
      ) {
        insertAlert(deviceId, 'inject_warn', 'warn', `Inyección fuera de rango · ${Math.round(injectDeg)}°`, {
          fuel_inject_timing_deg: injectDeg,
          fuel_inject: injectObj ?? null,
        })
        raised.push('inject_warn')
      }
    }

    // Hybrid pack life (OBD PID 015B)
    const hybridObj = signals.hybrid_batt as Record<string, unknown> | undefined
    let hybridPct: number | null =
      typeof hybridObj?.life_pct === 'number'
        ? (hybridObj.life_pct as number)
        : typeof signals.hybrid_batt_life_pct === 'number'
          ? (signals.hybrid_batt_life_pct as number)
          : null
    const hybridSpeed =
      typeof hybridObj?.speed_kmh === 'number'
        ? (hybridObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof hybridPct === 'number') {
      const warnPct = typeof signals.hybrid_warn_pct === 'number' ? (signals.hybrid_warn_pct as number) : 30
      const alertPct = typeof signals.hybrid_alert_pct === 'number' ? (signals.hybrid_alert_pct as number) : 15
      const minSpd = typeof signals.hybrid_speed_min_kmh === 'number' ? (signals.hybrid_speed_min_kmh as number) : 0
      const spdOk = typeof hybridSpeed === 'number' && hybridSpeed >= minSpd
      if (spdOk && hybridPct <= alertPct && !recentlyAlerted(deviceId, 'hybrid_batt_alert', 120)) {
        insertAlert(deviceId, 'hybrid_batt_alert', 'critical', `Batería híbrida crítica · ${Math.round(hybridPct)}%`, {
          hybrid_batt_life_pct: hybridPct,
          hybrid_batt: hybridObj ?? null,
        })
        raised.push('hybrid_batt_alert')
      } else if (
        spdOk &&
        hybridPct <= warnPct &&
        hybridPct > alertPct &&
        !recentlyAlerted(deviceId, 'hybrid_batt_warn', 120)
      ) {
        insertAlert(deviceId, 'hybrid_batt_warn', 'warn', `Batería híbrida baja · ${Math.round(hybridPct)}%`, {
          hybrid_batt_life_pct: hybridPct,
          hybrid_batt: hybridObj ?? null,
        })
        raised.push('hybrid_batt_warn')
      }
    }

    // Engine reference torque (OBD PID 0163)
    const refTorqueObj = signals.ref_torque as Record<string, unknown> | undefined
    let refTorqueNm: number | null =
      typeof refTorqueObj?.torque_nm === 'number'
        ? (refTorqueObj.torque_nm as number)
        : typeof signals.engine_ref_torque_nm === 'number'
          ? (signals.engine_ref_torque_nm as number)
          : null
    if (typeof refTorqueNm === 'number') {
      const warnLo = typeof signals.ref_torque_warn_low_nm === 'number' ? (signals.ref_torque_warn_low_nm as number) : 100
      const alertLo = typeof signals.ref_torque_alert_low_nm === 'number' ? (signals.ref_torque_alert_low_nm as number) : 80
      const warnHi = typeof signals.ref_torque_warn_high_nm === 'number' ? (signals.ref_torque_warn_high_nm as number) : 450
      const alertHi = typeof signals.ref_torque_alert_high_nm === 'number' ? (signals.ref_torque_alert_high_nm as number) : 520
      if ((refTorqueNm <= alertLo || refTorqueNm >= alertHi) && !recentlyAlerted(deviceId, 'ref_torque_alert', 120)) {
        insertAlert(deviceId, 'ref_torque_alert', 'critical', `Torque ref crítico · ${Math.round(refTorqueNm)} Nm`, {
          engine_ref_torque_nm: refTorqueNm,
          ref_torque: refTorqueObj ?? null,
        })
        raised.push('ref_torque_alert')
      } else if (
        (refTorqueNm <= warnLo || refTorqueNm >= warnHi) &&
        refTorqueNm > alertLo &&
        refTorqueNm < alertHi &&
        !recentlyAlerted(deviceId, 'ref_torque_warn', 120)
      ) {
        insertAlert(deviceId, 'ref_torque_warn', 'warn', `Torque ref anómalo · ${Math.round(refTorqueNm)} Nm`, {
          engine_ref_torque_nm: refTorqueNm,
          ref_torque: refTorqueObj ?? null,
        })
        raised.push('ref_torque_warn')
      }
    }

    // Catalyst B1S6 (OBD PID 0179)
    const catalystB1s6Obj = signals.catalyst_b1s6 as Record<string, unknown> | undefined
    const catalystB1s6TempC =
      typeof catalystB1s6Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s6Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s6_temp_c === 'number'
          ? (signals.catalyst_b1s6_temp_c as number)
          : null
    if (typeof catalystB1s6TempC === 'number') {
      const warnC = typeof signals.cat_b1s6_warn_c === 'number' ? (signals.cat_b1s6_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s6_alert_c === 'number' ? (signals.cat_b1s6_alert_c as number) : 850
      if (catalystB1s6TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s6_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s6_alert', 'critical', `Catalizador B1S6 crítico · ${Math.round(catalystB1s6TempC)} °C`, {
          catalyst_b1s6_temp_c: catalystB1s6TempC,
          catalyst_b1s6: catalystB1s6Obj ?? null,
        })
        raised.push('cat_b1s6_alert')
      } else if (
        catalystB1s6TempC >= warnC &&
        catalystB1s6TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s6_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s6_warn', 'warn', `Catalizador B1S6 caliente · ${Math.round(catalystB1s6TempC)} °C`, {
          catalyst_b1s6_temp_c: catalystB1s6TempC,
          catalyst_b1s6: catalystB1s6Obj ?? null,
        })
        raised.push('cat_b1s6_warn')
      }
    }

    // Catalyst B2S6 (OBD PID 017A)
    const catalystB2s6Obj = signals.catalyst_b2s6 as Record<string, unknown> | undefined
    const catalystB2s6TempC =
      typeof catalystB2s6Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s6Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s6_temp_c === 'number'
          ? (signals.catalyst_b2s6_temp_c as number)
          : null
    if (typeof catalystB2s6TempC === 'number') {
      const warnC = typeof signals.cat_b2s6_warn_c === 'number' ? (signals.cat_b2s6_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s6_alert_c === 'number' ? (signals.cat_b2s6_alert_c as number) : 850
      if (catalystB2s6TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s6_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s6_alert', 'critical', `Catalizador B2S6 crítico · ${Math.round(catalystB2s6TempC)} °C`, {
          catalyst_b2s6_temp_c: catalystB2s6TempC,
          catalyst_b2s6: catalystB2s6Obj ?? null,
        })
        raised.push('cat_b2s6_alert')
      } else if (
        catalystB2s6TempC >= warnC &&
        catalystB2s6TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s6_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s6_warn', 'warn', `Catalizador B2S6 caliente · ${Math.round(catalystB2s6TempC)} °C`, {
          catalyst_b2s6_temp_c: catalystB2s6TempC,
          catalyst_b2s6: catalystB2s6Obj ?? null,
        })
        raised.push('cat_b2s6_warn')
      }
    }

    // Throttle B (OBD PID 0147)
    const thrBObj = signals.throttle_b as Record<string, unknown> | undefined
    const thrBPct =
      typeof thrBObj?.throttle_pct === 'number'
        ? (thrBObj.throttle_pct as number)
        : typeof signals.throttle_b_pct === 'number'
          ? (signals.throttle_b_pct as number)
          : null
    const thrBSpeed =
      typeof thrBObj?.speed_kmh === 'number'
        ? (thrBObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof thrBPct === 'number') {
      const warnPct = typeof signals.thr_b_warn_pct === 'number' ? (signals.thr_b_warn_pct as number) : 75
      const alertPct = typeof signals.thr_b_alert_pct === 'number' ? (signals.thr_b_alert_pct as number) : 90
      const minSpd = typeof signals.thr_b_speed_min_kmh === 'number' ? (signals.thr_b_speed_min_kmh as number) : 20
      const spdOk = typeof thrBSpeed === 'number' && thrBSpeed >= minSpd
      if (spdOk && thrBPct >= alertPct && !recentlyAlerted(deviceId, 'thr_b_alert', 120)) {
        insertAlert(deviceId, 'thr_b_alert', 'critical', `Mariposa B crítica · ${Math.round(thrBPct)}%`, {
          throttle_b_pct: thrBPct,
          throttle_b: thrBObj ?? null,
        })
        raised.push('thr_b_alert')
      } else if (
        spdOk &&
        thrBPct >= warnPct &&
        thrBPct < alertPct &&
        !recentlyAlerted(deviceId, 'thr_b_warn', 120)
      ) {
        insertAlert(deviceId, 'thr_b_warn', 'warn', `Mariposa B alta · ${Math.round(thrBPct)}%`, {
          throttle_b_pct: thrBPct,
          throttle_b: thrBObj ?? null,
        })
        raised.push('thr_b_warn')
      }
    }

    // Throttle C (OBD PID 0148)
    const thrCObj = signals.throttle_c as Record<string, unknown> | undefined
    const thrCPct =
      typeof thrCObj?.throttle_pct === 'number'
        ? (thrCObj.throttle_pct as number)
        : typeof signals.throttle_c_pct === 'number'
          ? (signals.throttle_c_pct as number)
          : null
    const thrCSpeed =
      typeof thrCObj?.speed_kmh === 'number'
        ? (thrCObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof thrCPct === 'number') {
      const warnPct = typeof signals.thr_c_warn_pct === 'number' ? (signals.thr_c_warn_pct as number) : 75
      const alertPct = typeof signals.thr_c_alert_pct === 'number' ? (signals.thr_c_alert_pct as number) : 90
      const minSpd = typeof signals.thr_c_speed_min_kmh === 'number' ? (signals.thr_c_speed_min_kmh as number) : 20
      const spdOk = typeof thrCSpeed === 'number' && thrCSpeed >= minSpd
      if (spdOk && thrCPct >= alertPct && !recentlyAlerted(deviceId, 'thr_c_alert', 120)) {
        insertAlert(deviceId, 'thr_c_alert', 'critical', `Mariposa C crítica · ${Math.round(thrCPct)}%`, {
          throttle_c_pct: thrCPct,
          throttle_c: thrCObj ?? null,
        })
        raised.push('thr_c_alert')
      } else if (
        spdOk &&
        thrCPct >= warnPct &&
        thrCPct < alertPct &&
        !recentlyAlerted(deviceId, 'thr_c_warn', 120)
      ) {
        insertAlert(deviceId, 'thr_c_warn', 'warn', `Mariposa C alta · ${Math.round(thrCPct)}%`, {
          throttle_c_pct: thrCPct,
          throttle_c: thrCObj ?? null,
        })
        raised.push('thr_c_warn')
      }
    }

    // MIL time on (OBD PID 0154)
    const milTimeObj = signals.mil_time as Record<string, unknown> | undefined
    const milTimeMin =
      typeof milTimeObj?.minutes === 'number'
        ? (milTimeObj.minutes as number)
        : typeof signals.mil_time_min === 'number'
          ? (signals.mil_time_min as number)
          : null
    const milTimeActive =
      milTimeObj?.mil_on === true ||
      signals.mil === true ||
      (typeof milTimeMin === 'number' && milTimeMin > 0)
    if (typeof milTimeMin === 'number' && milTimeActive) {
      const warnMin = typeof signals.mil_time_warn_min === 'number' ? (signals.mil_time_warn_min as number) : 30
      const alertMin = typeof signals.mil_time_alert_min === 'number' ? (signals.mil_time_alert_min as number) : 60
      if (milTimeMin >= alertMin && !recentlyAlerted(deviceId, 'mil_time_alert', 300)) {
        insertAlert(deviceId, 'mil_time_alert', 'critical', `MIL activa · ${Math.round(milTimeMin)} min`, {
          mil_time_min: milTimeMin,
          mil_time: milTimeObj ?? null,
        })
        raised.push('mil_time_alert')
      } else if (
        milTimeMin >= warnMin &&
        milTimeMin < alertMin &&
        !recentlyAlerted(deviceId, 'mil_time_warn', 300)
      ) {
        insertAlert(deviceId, 'mil_time_warn', 'warn', `MIL activa · ${Math.round(milTimeMin)} min`, {
          mil_time_min: milTimeMin,
          mil_time: milTimeObj ?? null,
        })
        raised.push('mil_time_warn')
      }
    }

    // Catalyst B1S7 (OBD PID 017B)
    const catalystB1s7Obj = signals.catalyst_b1s7 as Record<string, unknown> | undefined
    const catalystB1s7TempC =
      typeof catalystB1s7Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s7Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s7_temp_c === 'number'
          ? (signals.catalyst_b1s7_temp_c as number)
          : null
    if (typeof catalystB1s7TempC === 'number') {
      const warnC = typeof signals.cat_b1s7_warn_c === 'number' ? (signals.cat_b1s7_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s7_alert_c === 'number' ? (signals.cat_b1s7_alert_c as number) : 850
      if (catalystB1s7TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s7_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s7_alert', 'critical', `Catalizador B1S7 crítico · ${Math.round(catalystB1s7TempC)} °C`, {
          catalyst_b1s7_temp_c: catalystB1s7TempC,
          catalyst_b1s7: catalystB1s7Obj ?? null,
        })
        raised.push('cat_b1s7_alert')
      } else if (
        catalystB1s7TempC >= warnC &&
        catalystB1s7TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s7_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s7_warn', 'warn', `Catalizador B1S7 caliente · ${Math.round(catalystB1s7TempC)} °C`, {
          catalyst_b1s7_temp_c: catalystB1s7TempC,
          catalyst_b1s7: catalystB1s7Obj ?? null,
        })
        raised.push('cat_b1s7_warn')
      }
    }

    // Catalyst B2S7 (OBD PID 017C)
    const catalystB2s7Obj = signals.catalyst_b2s7 as Record<string, unknown> | undefined
    const catalystB2s7TempC =
      typeof catalystB2s7Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s7Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s7_temp_c === 'number'
          ? (signals.catalyst_b2s7_temp_c as number)
          : null
    if (typeof catalystB2s7TempC === 'number') {
      const warnC = typeof signals.cat_b2s7_warn_c === 'number' ? (signals.cat_b2s7_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s7_alert_c === 'number' ? (signals.cat_b2s7_alert_c as number) : 850
      if (catalystB2s7TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s7_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s7_alert', 'critical', `Catalizador B2S7 crítico · ${Math.round(catalystB2s7TempC)} °C`, {
          catalyst_b2s7_temp_c: catalystB2s7TempC,
          catalyst_b2s7: catalystB2s7Obj ?? null,
        })
        raised.push('cat_b2s7_alert')
      } else if (
        catalystB2s7TempC >= warnC &&
        catalystB2s7TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s7_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s7_warn', 'warn', `Catalizador B2S7 caliente · ${Math.round(catalystB2s7TempC)} °C`, {
          catalyst_b2s7_temp_c: catalystB2s7TempC,
          catalyst_b2s7: catalystB2s7Obj ?? null,
        })
        raised.push('cat_b2s7_warn')
      }
    }

    // Fuel type (OBD PID 0151)
    const fuelTypeObj = signals.fuel_type as Record<string, unknown> | undefined
    const fuelTypeCode =
      typeof fuelTypeObj?.type_code === 'number'
        ? (fuelTypeObj.type_code as number)
        : typeof signals.fuel_type_code === 'number'
          ? (signals.fuel_type_code as number)
          : null
    const fuelTypeSpeed =
      typeof fuelTypeObj?.speed_kmh === 'number'
        ? (fuelTypeObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const fuelExpected =
      typeof signals.fuel_type_expected === 'number' ? (signals.fuel_type_expected as number) : 1
    const fuelMinSpd =
      typeof signals.fuel_type_speed_min_kmh === 'number' ? (signals.fuel_type_speed_min_kmh as number) : 5
    if (
      typeof fuelTypeCode === 'number' &&
      fuelTypeCode > 0 &&
      typeof fuelTypeSpeed === 'number' &&
      fuelTypeSpeed >= fuelMinSpd &&
      fuelTypeCode !== fuelExpected &&
      !recentlyAlerted(deviceId, 'fuel_type_alert', 300)
    ) {
      insertAlert(deviceId, 'fuel_type_alert', 'critical', `Combustible incorrecto · tipo ${fuelTypeCode}`, {
        fuel_type_code: fuelTypeCode,
        fuel_type_expected: fuelExpected,
        fuel_type: fuelTypeObj ?? null,
      })
      raised.push('fuel_type_alert')
    }

    // Max equiv ratio (OBD PID 014F)
    const maxEquivObj = signals.max_equiv as Record<string, unknown> | undefined
    const maxEquivRatio =
      typeof maxEquivObj?.ratio === 'number'
        ? (maxEquivObj.ratio as number)
        : typeof signals.max_equiv_ratio === 'number'
          ? (signals.max_equiv_ratio as number)
          : null
    if (typeof maxEquivRatio === 'number') {
      const warnLo = typeof signals.max_equiv_warn_low === 'number' ? (signals.max_equiv_warn_low as number) : 0.88
      const alertLo = typeof signals.max_equiv_alert_low === 'number' ? (signals.max_equiv_alert_low as number) : 0.82
      const warnHi = typeof signals.max_equiv_warn_high === 'number' ? (signals.max_equiv_warn_high as number) : 1.18
      const alertHi = typeof signals.max_equiv_alert_high === 'number' ? (signals.max_equiv_alert_high as number) : 1.24
      if ((maxEquivRatio <= alertLo || maxEquivRatio >= alertHi) && !recentlyAlerted(deviceId, 'max_equiv_alert', 120)) {
        insertAlert(deviceId, 'max_equiv_alert', 'critical', `Lambda máx crítica · ${maxEquivRatio.toFixed(2)}`, {
          max_equiv_ratio: maxEquivRatio,
          max_equiv: maxEquivObj ?? null,
        })
        raised.push('max_equiv_alert')
      } else if (
        (maxEquivRatio <= warnLo || maxEquivRatio >= warnHi) &&
        maxEquivRatio > alertLo &&
        maxEquivRatio < alertHi &&
        !recentlyAlerted(deviceId, 'max_equiv_warn', 120)
      ) {
        insertAlert(deviceId, 'max_equiv_warn', 'warn', `Lambda máx anómala · ${maxEquivRatio.toFixed(2)}`, {
          max_equiv_ratio: maxEquivRatio,
          max_equiv: maxEquivObj ?? null,
        })
        raised.push('max_equiv_warn')
      }
    }

    // Max MAF (OBD PID 0150)
    const maxMafObj = signals.max_maf as Record<string, unknown> | undefined
    const maxMafGps =
      typeof maxMafObj?.maf_gps === 'number'
        ? (maxMafObj.maf_gps as number)
        : typeof signals.max_maf_gps === 'number'
          ? (signals.max_maf_gps as number)
          : null
    if (typeof maxMafGps === 'number') {
      const warnLo = typeof signals.max_maf_warn_low_gps === 'number' ? (signals.max_maf_warn_low_gps as number) : 25
      const alertLo = typeof signals.max_maf_alert_low_gps === 'number' ? (signals.max_maf_alert_low_gps as number) : 15
      if (maxMafGps <= alertLo && !recentlyAlerted(deviceId, 'max_maf_alert', 120)) {
        insertAlert(deviceId, 'max_maf_alert', 'critical', `MAF máx crítico · ${Math.round(maxMafGps)} g/s`, {
          max_maf_gps: maxMafGps,
          max_maf: maxMafObj ?? null,
        })
        raised.push('max_maf_alert')
      } else if (
        maxMafGps <= warnLo &&
        maxMafGps > alertLo &&
        !recentlyAlerted(deviceId, 'max_maf_warn', 120)
      ) {
        insertAlert(deviceId, 'max_maf_warn', 'warn', `MAF máx bajo · ${Math.round(maxMafGps)} g/s`, {
          max_maf_gps: maxMafGps,
          max_maf: maxMafObj ?? null,
        })
        raised.push('max_maf_warn')
      }
    }

    // Catalyst B1S8 (OBD PID 017D)
    const catalystB1s8Obj = signals.catalyst_b1s8 as Record<string, unknown> | undefined
    const catalystB1s8TempC =
      typeof catalystB1s8Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s8Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s8_temp_c === 'number'
          ? (signals.catalyst_b1s8_temp_c as number)
          : null
    if (typeof catalystB1s8TempC === 'number') {
      const warnC = typeof signals.cat_b1s8_warn_c === 'number' ? (signals.cat_b1s8_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s8_alert_c === 'number' ? (signals.cat_b1s8_alert_c as number) : 850
      if (catalystB1s8TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s8_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s8_alert', 'critical', `Catalizador B1S8 crítico · ${Math.round(catalystB1s8TempC)} °C`, {
          catalyst_b1s8_temp_c: catalystB1s8TempC,
          catalyst_b1s8: catalystB1s8Obj ?? null,
        })
        raised.push('cat_b1s8_alert')
      } else if (
        catalystB1s8TempC >= warnC &&
        catalystB1s8TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s8_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s8_warn', 'warn', `Catalizador B1S8 caliente · ${Math.round(catalystB1s8TempC)} °C`, {
          catalyst_b1s8_temp_c: catalystB1s8TempC,
          catalyst_b1s8: catalystB1s8Obj ?? null,
        })
        raised.push('cat_b1s8_warn')
      }
    }

    // Catalyst B2S8 (OBD PID 017E)
    const catalystB2s8Obj = signals.catalyst_b2s8 as Record<string, unknown> | undefined
    const catalystB2s8TempC =
      typeof catalystB2s8Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s8Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s8_temp_c === 'number'
          ? (signals.catalyst_b2s8_temp_c as number)
          : null
    if (typeof catalystB2s8TempC === 'number') {
      const warnC = typeof signals.cat_b2s8_warn_c === 'number' ? (signals.cat_b2s8_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s8_alert_c === 'number' ? (signals.cat_b2s8_alert_c as number) : 850
      if (catalystB2s8TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s8_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s8_alert', 'critical', `Catalizador B2S8 crítico · ${Math.round(catalystB2s8TempC)} °C`, {
          catalyst_b2s8_temp_c: catalystB2s8TempC,
          catalyst_b2s8: catalystB2s8Obj ?? null,
        })
        raised.push('cat_b2s8_alert')
      } else if (
        catalystB2s8TempC >= warnC &&
        catalystB2s8TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s8_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s8_warn', 'warn', `Catalizador B2S8 caliente · ${Math.round(catalystB2s8TempC)} °C`, {
          catalyst_b2s8_temp_c: catalystB2s8TempC,
          catalyst_b2s8: catalystB2s8Obj ?? null,
        })
        raised.push('cat_b2s8_warn')
      }
    }

    // Max available torque (OBD PID 0164)
    const maxAvailTorqueObj = signals.max_avail_torque as Record<string, unknown> | undefined
    const maxAvailTorquePct =
      typeof maxAvailTorqueObj?.torque_pct === 'number'
        ? (maxAvailTorqueObj.torque_pct as number)
        : typeof signals.max_avail_torque_pct === 'number'
          ? (signals.max_avail_torque_pct as number)
          : null
    const maxAvailTorqueSpeed =
      typeof maxAvailTorqueObj?.speed_kmh === 'number'
        ? (maxAvailTorqueObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const maxAvailTorqueMinSpd =
      typeof signals.max_avail_torque_speed_min_kmh === 'number'
        ? (signals.max_avail_torque_speed_min_kmh as number)
        : 10
    if (
      typeof maxAvailTorquePct === 'number' &&
      typeof maxAvailTorqueSpeed === 'number' &&
      maxAvailTorqueSpeed >= maxAvailTorqueMinSpd
    ) {
      const warnLo =
        typeof signals.max_avail_torque_warn_low === 'number' ? (signals.max_avail_torque_warn_low as number) : 30
      const alertLo =
        typeof signals.max_avail_torque_alert_low === 'number' ? (signals.max_avail_torque_alert_low as number) : 20
      if (maxAvailTorquePct <= alertLo && !recentlyAlerted(deviceId, 'max_avail_torque_alert', 120)) {
        insertAlert(deviceId, 'max_avail_torque_alert', 'critical', `Torque máx crítico · ${Math.round(maxAvailTorquePct)}%`, {
          max_avail_torque_pct: maxAvailTorquePct,
          max_avail_torque: maxAvailTorqueObj ?? null,
        })
        raised.push('max_avail_torque_alert')
      } else if (
        maxAvailTorquePct <= warnLo &&
        maxAvailTorquePct > alertLo &&
        !recentlyAlerted(deviceId, 'max_avail_torque_warn', 120)
      ) {
        insertAlert(deviceId, 'max_avail_torque_warn', 'warn', `Torque máx bajo · ${Math.round(maxAvailTorquePct)}%`, {
          max_avail_torque_pct: maxAvailTorquePct,
          max_avail_torque: maxAvailTorqueObj ?? null,
        })
        raised.push('max_avail_torque_warn')
      }
    }

    // MAF sensor IAT (OBD PID 0166)
    const mafIatObj = signals.maf_iat as Record<string, unknown> | undefined
    const mafSensorIatC =
      typeof mafIatObj?.temp_c === 'number'
        ? (mafIatObj.temp_c as number)
        : typeof signals.maf_sensor_iat_c === 'number'
          ? (signals.maf_sensor_iat_c as number)
          : null
    const mafIatSpeed =
      typeof mafIatObj?.speed_kmh === 'number'
        ? (mafIatObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const mafIatMinSpd =
      typeof signals.maf_iat_speed_min_kmh === 'number' ? (signals.maf_iat_speed_min_kmh as number) : 15
    if (
      typeof mafSensorIatC === 'number' &&
      typeof mafIatSpeed === 'number' &&
      mafIatSpeed >= mafIatMinSpd
    ) {
      const warnC = typeof signals.maf_iat_warn_c === 'number' ? (signals.maf_iat_warn_c as number) : 70
      const alertC = typeof signals.maf_iat_alert_c === 'number' ? (signals.maf_iat_alert_c as number) : 85
      if (mafSensorIatC >= alertC && !recentlyAlerted(deviceId, 'maf_iat_alert', 120)) {
        insertAlert(deviceId, 'maf_iat_alert', 'critical', `MAF IAT crítica · ${Math.round(mafSensorIatC)} °C`, {
          maf_sensor_iat_c: mafSensorIatC,
          maf_iat: mafIatObj ?? null,
        })
        raised.push('maf_iat_alert')
      } else if (
        mafSensorIatC >= warnC &&
        mafSensorIatC < alertC &&
        !recentlyAlerted(deviceId, 'maf_iat_warn', 120)
      ) {
        insertAlert(deviceId, 'maf_iat_warn', 'warn', `MAF IAT caliente · ${Math.round(mafSensorIatC)} °C`, {
          maf_sensor_iat_c: mafSensorIatC,
          maf_iat: mafIatObj ?? null,
        })
        raised.push('maf_iat_warn')
      }
    }

    // Aux input status (OBD PID 0165)
    const auxInputObj = signals.aux_input as Record<string, unknown> | undefined
    const auxInputStatus =
      typeof auxInputObj?.status_code === 'number'
        ? (auxInputObj.status_code as number)
        : typeof signals.aux_input_status === 'number'
          ? (signals.aux_input_status as number)
          : null
    const auxInputSpeed =
      typeof auxInputObj?.speed_kmh === 'number'
        ? (auxInputObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const auxInputMinSpd =
      typeof signals.aux_input_speed_min_kmh === 'number' ? (signals.aux_input_speed_min_kmh as number) : 10
    const auxInputMask =
      typeof signals.aux_input_alert_mask === 'number' ? (signals.aux_input_alert_mask as number) : 0x0f
    if (
      typeof auxInputStatus === 'number' &&
      auxInputStatus > 0 &&
      typeof auxInputSpeed === 'number' &&
      auxInputSpeed >= auxInputMinSpd &&
      (auxInputStatus & auxInputMask) !== 0 &&
      !recentlyAlerted(deviceId, 'aux_input_alert', 300)
    ) {
      insertAlert(deviceId, 'aux_input_alert', 'critical', `Entrada auxiliar · 0x${auxInputStatus.toString(16).toUpperCase().padStart(2, '0')}`, {
        aux_input_status: auxInputStatus,
        aux_input: auxInputObj ?? null,
      })
      raised.push('aux_input_alert')
    }

    // Catalyst B1S9 (OBD PID 017F)
    const catalystB1s9Obj = signals.catalyst_b1s9 as Record<string, unknown> | undefined
    const catalystB1s9TempC =
      typeof catalystB1s9Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s9Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s9_temp_c === 'number'
          ? (signals.catalyst_b1s9_temp_c as number)
          : null
    if (typeof catalystB1s9TempC === 'number') {
      const warnC = typeof signals.cat_b1s9_warn_c === 'number' ? (signals.cat_b1s9_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s9_alert_c === 'number' ? (signals.cat_b1s9_alert_c as number) : 850
      if (catalystB1s9TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s9_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s9_alert', 'critical', `Catalizador B1S9 crítico · ${Math.round(catalystB1s9TempC)} °C`, {
          catalyst_b1s9_temp_c: catalystB1s9TempC,
          catalyst_b1s9: catalystB1s9Obj ?? null,
        })
        raised.push('cat_b1s9_alert')
      } else if (
        catalystB1s9TempC >= warnC &&
        catalystB1s9TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s9_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s9_warn', 'warn', `Catalizador B1S9 caliente · ${Math.round(catalystB1s9TempC)} °C`, {
          catalyst_b1s9_temp_c: catalystB1s9TempC,
          catalyst_b1s9: catalystB1s9Obj ?? null,
        })
        raised.push('cat_b1s9_warn')
      }
    }

    // Catalyst B2S9 (OBD PID 0180)
    const catalystB2s9Obj = signals.catalyst_b2s9 as Record<string, unknown> | undefined
    const catalystB2s9TempC =
      typeof catalystB2s9Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s9Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s9_temp_c === 'number'
          ? (signals.catalyst_b2s9_temp_c as number)
          : null
    if (typeof catalystB2s9TempC === 'number') {
      const warnC = typeof signals.cat_b2s9_warn_c === 'number' ? (signals.cat_b2s9_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s9_alert_c === 'number' ? (signals.cat_b2s9_alert_c as number) : 850
      if (catalystB2s9TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s9_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s9_alert', 'critical', `Catalizador B2S9 crítico · ${Math.round(catalystB2s9TempC)} °C`, {
          catalyst_b2s9_temp_c: catalystB2s9TempC,
          catalyst_b2s9: catalystB2s9Obj ?? null,
        })
        raised.push('cat_b2s9_alert')
      } else if (
        catalystB2s9TempC >= warnC &&
        catalystB2s9TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s9_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s9_warn', 'warn', `Catalizador B2S9 caliente · ${Math.round(catalystB2s9TempC)} °C`, {
          catalyst_b2s9_temp_c: catalystB2s9TempC,
          catalyst_b2s9: catalystB2s9Obj ?? null,
        })
        raised.push('cat_b2s9_warn')
      }
    }

    // Coolant ECT2 (OBD PID 0167)
    const ect2Obj = signals.ect2 as Record<string, unknown> | undefined
    const coolantEct2C =
      typeof ect2Obj?.coolant_c === 'number'
        ? (ect2Obj.coolant_c as number)
        : typeof signals.coolant_ect2_c === 'number'
          ? (signals.coolant_ect2_c as number)
          : null
    if (typeof coolantEct2C === 'number') {
      const warnC = typeof signals.ect2_warn_c === 'number' ? (signals.ect2_warn_c as number) : 95
      const alertC = typeof signals.ect2_alert_c === 'number' ? (signals.ect2_alert_c as number) : 105
      if (coolantEct2C >= alertC && !recentlyAlerted(deviceId, 'ect2_alert', 300)) {
        insertAlert(deviceId, 'ect2_alert', 'critical', `Refrigerante ECT2 crítico · ${Math.round(coolantEct2C)} °C`, {
          coolant_ect2_c: coolantEct2C,
          ect2: ect2Obj ?? null,
        })
        raised.push('ect2_alert')
      } else if (
        coolantEct2C >= warnC &&
        coolantEct2C < alertC &&
        !recentlyAlerted(deviceId, 'ect2_warn', 300)
      ) {
        insertAlert(deviceId, 'ect2_warn', 'warn', `Refrigerante ECT2 caliente · ${Math.round(coolantEct2C)} °C`, {
          coolant_ect2_c: coolantEct2C,
          ect2: ect2Obj ?? null,
        })
        raised.push('ect2_warn')
      }
    }

    // IAT sensor 2 (OBD PID 0168)
    const iat2Obj = signals.iat2 as Record<string, unknown> | undefined
    const iatSensor2C =
      typeof iat2Obj?.temp_c === 'number'
        ? (iat2Obj.temp_c as number)
        : typeof signals.iat_sensor2_c === 'number'
          ? (signals.iat_sensor2_c as number)
          : null
    const iat2Speed =
      typeof iat2Obj?.speed_kmh === 'number'
        ? (iat2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const iat2MinSpd =
      typeof signals.iat2_speed_min_kmh === 'number' ? (signals.iat2_speed_min_kmh as number) : 10
    if (
      typeof iatSensor2C === 'number' &&
      typeof iat2Speed === 'number' &&
      iat2Speed >= iat2MinSpd
    ) {
      const warnC = typeof signals.iat2_warn_c === 'number' ? (signals.iat2_warn_c as number) : 55
      const alertC = typeof signals.iat2_alert_c === 'number' ? (signals.iat2_alert_c as number) : 65
      if (iatSensor2C >= alertC && !recentlyAlerted(deviceId, 'iat2_alert', 120)) {
        insertAlert(deviceId, 'iat2_alert', 'critical', `IAT2 crítica · ${Math.round(iatSensor2C)} °C`, {
          iat_sensor2_c: iatSensor2C,
          iat2: iat2Obj ?? null,
        })
        raised.push('iat2_alert')
      } else if (
        iatSensor2C >= warnC &&
        iatSensor2C < alertC &&
        !recentlyAlerted(deviceId, 'iat2_warn', 120)
      ) {
        insertAlert(deviceId, 'iat2_warn', 'warn', `IAT2 caliente · ${Math.round(iatSensor2C)} °C`, {
          iat_sensor2_c: iatSensor2C,
          iat2: iat2Obj ?? null,
        })
        raised.push('iat2_warn')
      }
    }

    // Turbo inlet pressure (OBD PID 016F)
    const turboInletObj = signals.turbo_inlet as Record<string, unknown> | undefined
    const turboInletKpa =
      typeof turboInletObj?.pressure_kpa === 'number'
        ? (turboInletObj.pressure_kpa as number)
        : typeof signals.turbo_inlet_kpa === 'number'
          ? (signals.turbo_inlet_kpa as number)
          : null
    const turboInletSpeed =
      typeof turboInletObj?.speed_kmh === 'number'
        ? (turboInletObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const turboInletMinSpd =
      typeof signals.turbo_inlet_speed_min_kmh === 'number' ? (signals.turbo_inlet_speed_min_kmh as number) : 15
    if (
      typeof turboInletKpa === 'number' &&
      typeof turboInletSpeed === 'number' &&
      turboInletSpeed >= turboInletMinSpd
    ) {
      const warnKpa =
        typeof signals.turbo_inlet_warn_kpa === 'number' ? (signals.turbo_inlet_warn_kpa as number) : 200
      const alertKpa =
        typeof signals.turbo_inlet_alert_kpa === 'number' ? (signals.turbo_inlet_alert_kpa as number) : 230
      if (turboInletKpa >= alertKpa && !recentlyAlerted(deviceId, 'turbo_inlet_alert', 120)) {
        insertAlert(deviceId, 'turbo_inlet_alert', 'critical', `Turbo inlet crítico · ${Math.round(turboInletKpa)} kPa`, {
          turbo_inlet_kpa: turboInletKpa,
          turbo_inlet: turboInletObj ?? null,
        })
        raised.push('turbo_inlet_alert')
      } else if (
        turboInletKpa >= warnKpa &&
        turboInletKpa < alertKpa &&
        !recentlyAlerted(deviceId, 'turbo_inlet_warn', 120)
      ) {
        insertAlert(deviceId, 'turbo_inlet_warn', 'warn', `Turbo inlet alto · ${Math.round(turboInletKpa)} kPa`, {
          turbo_inlet_kpa: turboInletKpa,
          turbo_inlet: turboInletObj ?? null,
        })
        raised.push('turbo_inlet_warn')
      }
    }

    // Catalyst B1S10 (OBD PID 0181)
    const catalystB1s10Obj = signals.catalyst_b1s10 as Record<string, unknown> | undefined
    const catalystB1s10TempC =
      typeof catalystB1s10Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s10Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s10_temp_c === 'number'
          ? (signals.catalyst_b1s10_temp_c as number)
          : null
    if (typeof catalystB1s10TempC === 'number') {
      const warnC = typeof signals.cat_b1s10_warn_c === 'number' ? (signals.cat_b1s10_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s10_alert_c === 'number' ? (signals.cat_b1s10_alert_c as number) : 850
      if (catalystB1s10TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s10_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s10_alert', 'critical', `Catalizador B1S10 crítico · ${Math.round(catalystB1s10TempC)} °C`, {
          catalyst_b1s10_temp_c: catalystB1s10TempC,
          catalyst_b1s10: catalystB1s10Obj ?? null,
        })
        raised.push('cat_b1s10_alert')
      } else if (
        catalystB1s10TempC >= warnC &&
        catalystB1s10TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s10_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s10_warn', 'warn', `Catalizador B1S10 caliente · ${Math.round(catalystB1s10TempC)} °C`, {
          catalyst_b1s10_temp_c: catalystB1s10TempC,
          catalyst_b1s10: catalystB1s10Obj ?? null,
        })
        raised.push('cat_b1s10_warn')
      }
    }

    // Catalyst B2S10 (OBD PID 0182)
    const catalystB2s10Obj = signals.catalyst_b2s10 as Record<string, unknown> | undefined
    const catalystB2s10TempC =
      typeof catalystB2s10Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s10Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s10_temp_c === 'number'
          ? (signals.catalyst_b2s10_temp_c as number)
          : null
    if (typeof catalystB2s10TempC === 'number') {
      const warnC = typeof signals.cat_b2s10_warn_c === 'number' ? (signals.cat_b2s10_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s10_alert_c === 'number' ? (signals.cat_b2s10_alert_c as number) : 850
      if (catalystB2s10TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s10_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s10_alert', 'critical', `Catalizador B2S10 crítico · ${Math.round(catalystB2s10TempC)} °C`, {
          catalyst_b2s10_temp_c: catalystB2s10TempC,
          catalyst_b2s10: catalystB2s10Obj ?? null,
        })
        raised.push('cat_b2s10_alert')
      } else if (
        catalystB2s10TempC >= warnC &&
        catalystB2s10TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s10_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s10_warn', 'warn', `Catalizador B2S10 caliente · ${Math.round(catalystB2s10TempC)} °C`, {
          catalyst_b2s10_temp_c: catalystB2s10TempC,
          catalyst_b2s10: catalystB2s10Obj ?? null,
        })
        raised.push('cat_b2s10_warn')
      }
    }

    // EGR temperature (OBD PID 016B)
    const egrTempObj = signals.egr_temp as Record<string, unknown> | undefined
    const egrTempC =
      typeof egrTempObj?.temp_c === 'number'
        ? (egrTempObj.temp_c as number)
        : typeof signals.egr_temp_c === 'number'
          ? (signals.egr_temp_c as number)
          : null
    const egrTempSpeed =
      typeof egrTempObj?.speed_kmh === 'number'
        ? (egrTempObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const egrTempMinSpd =
      typeof signals.egr_temp_speed_min_kmh === 'number' ? (signals.egr_temp_speed_min_kmh as number) : 10
    if (
      typeof egrTempC === 'number' &&
      typeof egrTempSpeed === 'number' &&
      egrTempSpeed >= egrTempMinSpd
    ) {
      const warnC = typeof signals.egr_temp_warn_c === 'number' ? (signals.egr_temp_warn_c as number) : 350
      const alertC = typeof signals.egr_temp_alert_c === 'number' ? (signals.egr_temp_alert_c as number) : 450
      if (egrTempC >= alertC && !recentlyAlerted(deviceId, 'egr_temp_alert', 120)) {
        insertAlert(deviceId, 'egr_temp_alert', 'critical', `EGR caliente crítica · ${Math.round(egrTempC)} °C`, {
          egr_temp_c: egrTempC,
          egr_temp: egrTempObj ?? null,
        })
        raised.push('egr_temp_alert')
      } else if (
        egrTempC >= warnC &&
        egrTempC < alertC &&
        !recentlyAlerted(deviceId, 'egr_temp_warn', 120)
      ) {
        insertAlert(deviceId, 'egr_temp_warn', 'warn', `EGR caliente · ${Math.round(egrTempC)} °C`, {
          egr_temp_c: egrTempC,
          egr_temp: egrTempObj ?? null,
        })
        raised.push('egr_temp_warn')
      }
    }

    // Diesel intake air flow (OBD PID 016A)
    const dieselIafObj = signals.diesel_iaf as Record<string, unknown> | undefined
    const dieselIafPct =
      typeof dieselIafObj?.flow_pct === 'number'
        ? (dieselIafObj.flow_pct as number)
        : typeof signals.diesel_iaf_cmd_pct === 'number'
          ? (signals.diesel_iaf_cmd_pct as number)
          : null
    const dieselIafSpeed =
      typeof dieselIafObj?.speed_kmh === 'number'
        ? (dieselIafObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const dieselIafMinSpd =
      typeof signals.diesel_iaf_speed_min_kmh === 'number' ? (signals.diesel_iaf_speed_min_kmh as number) : 15
    if (
      typeof dieselIafPct === 'number' &&
      typeof dieselIafSpeed === 'number' &&
      dieselIafSpeed >= dieselIafMinSpd
    ) {
      const warnPct = typeof signals.diesel_iaf_warn_pct === 'number' ? (signals.diesel_iaf_warn_pct as number) : 75
      const alertPct = typeof signals.diesel_iaf_alert_pct === 'number' ? (signals.diesel_iaf_alert_pct as number) : 88
      if (dieselIafPct >= alertPct && !recentlyAlerted(deviceId, 'diesel_iaf_alert', 120)) {
        insertAlert(deviceId, 'diesel_iaf_alert', 'critical', `Diesel IAF crítico · ${Math.round(dieselIafPct)}%`, {
          diesel_iaf_cmd_pct: dieselIafPct,
          diesel_iaf: dieselIafObj ?? null,
        })
        raised.push('diesel_iaf_alert')
      } else if (
        dieselIafPct >= warnPct &&
        dieselIafPct < alertPct &&
        !recentlyAlerted(deviceId, 'diesel_iaf_warn', 120)
      ) {
        insertAlert(deviceId, 'diesel_iaf_warn', 'warn', `Diesel IAF alto · ${Math.round(dieselIafPct)}%`, {
          diesel_iaf_cmd_pct: dieselIafPct,
          diesel_iaf: dieselIafObj ?? null,
        })
        raised.push('diesel_iaf_warn')
      }
    }

    // Throttle actuator (OBD PID 016C)
    const thrActObj = signals.thr_act as Record<string, unknown> | undefined
    const thrActuatorPct =
      typeof thrActObj?.actuator_pct === 'number'
        ? (thrActObj.actuator_pct as number)
        : typeof signals.thr_actuator_pct === 'number'
          ? (signals.thr_actuator_pct as number)
          : null
    const thrActSpeed =
      typeof thrActObj?.speed_kmh === 'number'
        ? (thrActObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const thrActMinSpd =
      typeof signals.thr_act_speed_min_kmh === 'number' ? (signals.thr_act_speed_min_kmh as number) : 10
    if (
      typeof thrActuatorPct === 'number' &&
      typeof thrActSpeed === 'number' &&
      thrActSpeed >= thrActMinSpd
    ) {
      const warnPct = typeof signals.thr_act_warn_pct === 'number' ? (signals.thr_act_warn_pct as number) : 85
      const alertPct = typeof signals.thr_act_alert_pct === 'number' ? (signals.thr_act_alert_pct as number) : 92
      if (thrActuatorPct >= alertPct && !recentlyAlerted(deviceId, 'thr_act_alert', 120)) {
        insertAlert(deviceId, 'thr_act_alert', 'critical', `Actuador mariposa crítico · ${Math.round(thrActuatorPct)}%`, {
          thr_actuator_pct: thrActuatorPct,
          thr_act: thrActObj ?? null,
        })
        raised.push('thr_act_alert')
      } else if (
        thrActuatorPct >= warnPct &&
        thrActuatorPct < alertPct &&
        !recentlyAlerted(deviceId, 'thr_act_warn', 120)
      ) {
        insertAlert(deviceId, 'thr_act_warn', 'warn', `Actuador mariposa alto · ${Math.round(thrActuatorPct)}%`, {
          thr_actuator_pct: thrActuatorPct,
          thr_act: thrActObj ?? null,
        })
        raised.push('thr_act_warn')
      }
    }

    // Catalyst B1S11 (OBD PID 0183)
    const catalystB1s11Obj = signals.catalyst_b1s11 as Record<string, unknown> | undefined
    const catalystB1s11TempC =
      typeof catalystB1s11Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s11Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s11_temp_c === 'number'
          ? (signals.catalyst_b1s11_temp_c as number)
          : null
    if (typeof catalystB1s11TempC === 'number') {
      const warnC = typeof signals.cat_b1s11_warn_c === 'number' ? (signals.cat_b1s11_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s11_alert_c === 'number' ? (signals.cat_b1s11_alert_c as number) : 850
      if (catalystB1s11TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s11_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s11_alert', 'critical', `Catalizador B1S11 crítico · ${Math.round(catalystB1s11TempC)} °C`, {
          catalyst_b1s11_temp_c: catalystB1s11TempC,
          catalyst_b1s11: catalystB1s11Obj ?? null,
        })
        raised.push('cat_b1s11_alert')
      } else if (
        catalystB1s11TempC >= warnC &&
        catalystB1s11TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s11_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s11_warn', 'warn', `Catalizador B1S11 caliente · ${Math.round(catalystB1s11TempC)} °C`, {
          catalyst_b1s11_temp_c: catalystB1s11TempC,
          catalyst_b1s11: catalystB1s11Obj ?? null,
        })
        raised.push('cat_b1s11_warn')
      }
    }

    // Catalyst B2S11 (OBD PID 0184)
    const catalystB2s11Obj = signals.catalyst_b2s11 as Record<string, unknown> | undefined
    const catalystB2s11TempC =
      typeof catalystB2s11Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s11Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s11_temp_c === 'number'
          ? (signals.catalyst_b2s11_temp_c as number)
          : null
    if (typeof catalystB2s11TempC === 'number') {
      const warnC = typeof signals.cat_b2s11_warn_c === 'number' ? (signals.cat_b2s11_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s11_alert_c === 'number' ? (signals.cat_b2s11_alert_c as number) : 850
      if (catalystB2s11TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s11_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s11_alert', 'critical', `Catalizador B2S11 crítico · ${Math.round(catalystB2s11TempC)} °C`, {
          catalyst_b2s11_temp_c: catalystB2s11TempC,
          catalyst_b2s11: catalystB2s11Obj ?? null,
        })
        raised.push('cat_b2s11_alert')
      } else if (
        catalystB2s11TempC >= warnC &&
        catalystB2s11TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s11_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s11_warn', 'warn', `Catalizador B2S11 caliente · ${Math.round(catalystB2s11TempC)} °C`, {
          catalyst_b2s11_temp_c: catalystB2s11TempC,
          catalyst_b2s11: catalystB2s11Obj ?? null,
        })
        raised.push('cat_b2s11_warn')
      }
    }

    // Actual EGR (OBD PID 0169)
    const egrActualObj = signals.egr_actual as Record<string, unknown> | undefined
    const egrActualPct =
      typeof egrActualObj?.egr_pct === 'number'
        ? (egrActualObj.egr_pct as number)
        : typeof signals.actual_egr_pct === 'number'
          ? (signals.actual_egr_pct as number)
          : null
    const egrActualSpeed =
      typeof egrActualObj?.speed_kmh === 'number'
        ? (egrActualObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const egrActualMinSpd =
      typeof signals.egr_actual_speed_min_kmh === 'number' ? (signals.egr_actual_speed_min_kmh as number) : 15
    if (
      typeof egrActualPct === 'number' &&
      typeof egrActualSpeed === 'number' &&
      egrActualSpeed >= egrActualMinSpd
    ) {
      const warnPct = typeof signals.egr_actual_warn_pct === 'number' ? (signals.egr_actual_warn_pct as number) : 55
      const alertPct = typeof signals.egr_actual_alert_pct === 'number' ? (signals.egr_actual_alert_pct as number) : 70
      if (egrActualPct >= alertPct && !recentlyAlerted(deviceId, 'egr_actual_alert', 120)) {
        insertAlert(deviceId, 'egr_actual_alert', 'critical', `EGR real crítico · ${Math.round(egrActualPct)}%`, {
          actual_egr_pct: egrActualPct,
          egr_actual: egrActualObj ?? null,
        })
        raised.push('egr_actual_alert')
      } else if (
        egrActualPct >= warnPct &&
        egrActualPct < alertPct &&
        !recentlyAlerted(deviceId, 'egr_actual_warn', 120)
      ) {
        insertAlert(deviceId, 'egr_actual_warn', 'warn', `EGR real alto · ${Math.round(egrActualPct)}%`, {
          actual_egr_pct: egrActualPct,
          egr_actual: egrActualObj ?? null,
        })
        raised.push('egr_actual_warn')
      }
    }

    // Injection pressure control (OBD PID 016E)
    const injectCtrlObj = signals.inject_ctrl as Record<string, unknown> | undefined
    const injectCtrlKpa =
      typeof injectCtrlObj?.pressure_kpa === 'number'
        ? (injectCtrlObj.pressure_kpa as number)
        : typeof signals.inject_ctrl_kpa === 'number'
          ? (signals.inject_ctrl_kpa as number)
          : null
    const injectCtrlSpeed =
      typeof injectCtrlObj?.speed_kmh === 'number'
        ? (injectCtrlObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const injectCtrlMinSpd =
      typeof signals.inject_ctrl_speed_min_kmh === 'number' ? (signals.inject_ctrl_speed_min_kmh as number) : 10
    if (
      typeof injectCtrlKpa === 'number' &&
      typeof injectCtrlSpeed === 'number' &&
      injectCtrlSpeed >= injectCtrlMinSpd
    ) {
      const warnKpa = typeof signals.inject_ctrl_warn_kpa === 'number' ? (signals.inject_ctrl_warn_kpa as number) : 8000
      const alertKpa = typeof signals.inject_ctrl_alert_kpa === 'number' ? (signals.inject_ctrl_alert_kpa as number) : 12000
      if (injectCtrlKpa >= alertKpa && !recentlyAlerted(deviceId, 'inject_ctrl_alert', 120)) {
        insertAlert(deviceId, 'inject_ctrl_alert', 'critical', `Presión inyección crítica · ${Math.round(injectCtrlKpa)} kPa`, {
          inject_ctrl_kpa: injectCtrlKpa,
          inject_ctrl: injectCtrlObj ?? null,
        })
        raised.push('inject_ctrl_alert')
      } else if (
        injectCtrlKpa >= warnKpa &&
        injectCtrlKpa < alertKpa &&
        !recentlyAlerted(deviceId, 'inject_ctrl_warn', 120)
      ) {
        insertAlert(deviceId, 'inject_ctrl_warn', 'warn', `Presión inyección alta · ${Math.round(injectCtrlKpa)} kPa`, {
          inject_ctrl_kpa: injectCtrlKpa,
          inject_ctrl: injectCtrlObj ?? null,
        })
        raised.push('inject_ctrl_warn')
      }
    }

    // Fuel pressure control (OBD PID 016D)
    const fuelCtrlObj = signals.fuel_ctrl as Record<string, unknown> | undefined
    const fuelCtrlKpa =
      typeof fuelCtrlObj?.pressure_kpa === 'number'
        ? (fuelCtrlObj.pressure_kpa as number)
        : typeof signals.fuel_ctrl_kpa === 'number'
          ? (signals.fuel_ctrl_kpa as number)
          : null
    const fuelCtrlSpeed =
      typeof fuelCtrlObj?.speed_kmh === 'number'
        ? (fuelCtrlObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const fuelCtrlMinSpd =
      typeof signals.fuel_ctrl_speed_min_kmh === 'number' ? (signals.fuel_ctrl_speed_min_kmh as number) : 10
    if (
      typeof fuelCtrlKpa === 'number' &&
      typeof fuelCtrlSpeed === 'number' &&
      fuelCtrlSpeed >= fuelCtrlMinSpd
    ) {
      const warnKpa = typeof signals.fuel_ctrl_warn_kpa === 'number' ? (signals.fuel_ctrl_warn_kpa as number) : 6000
      const alertKpa = typeof signals.fuel_ctrl_alert_kpa === 'number' ? (signals.fuel_ctrl_alert_kpa as number) : 9000
      if (fuelCtrlKpa >= alertKpa && !recentlyAlerted(deviceId, 'fuel_ctrl_alert', 120)) {
        insertAlert(deviceId, 'fuel_ctrl_alert', 'critical', `Control combustible crítico · ${Math.round(fuelCtrlKpa)} kPa`, {
          fuel_ctrl_kpa: fuelCtrlKpa,
          fuel_ctrl: fuelCtrlObj ?? null,
        })
        raised.push('fuel_ctrl_alert')
      } else if (
        fuelCtrlKpa >= warnKpa &&
        fuelCtrlKpa < alertKpa &&
        !recentlyAlerted(deviceId, 'fuel_ctrl_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_ctrl_warn', 'warn', `Control combustible alto · ${Math.round(fuelCtrlKpa)} kPa`, {
          fuel_ctrl_kpa: fuelCtrlKpa,
          fuel_ctrl: fuelCtrlObj ?? null,
        })
        raised.push('fuel_ctrl_warn')
      }
    }

    // Catalyst B1S12 (OBD PID 0185)
    const catalystB1s12Obj = signals.catalyst_b1s12 as Record<string, unknown> | undefined
    const catalystB1s12TempC =
      typeof catalystB1s12Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s12Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s12_temp_c === 'number'
          ? (signals.catalyst_b1s12_temp_c as number)
          : null
    if (typeof catalystB1s12TempC === 'number') {
      const warnC = typeof signals.cat_b1s12_warn_c === 'number' ? (signals.cat_b1s12_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s12_alert_c === 'number' ? (signals.cat_b1s12_alert_c as number) : 850
      if (catalystB1s12TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s12_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s12_alert', 'critical', `Catalizador B1S12 crítico · ${Math.round(catalystB1s12TempC)} °C`, {
          catalyst_b1s12_temp_c: catalystB1s12TempC,
          catalyst_b1s12: catalystB1s12Obj ?? null,
        })
        raised.push('cat_b1s12_alert')
      } else if (
        catalystB1s12TempC >= warnC &&
        catalystB1s12TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s12_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s12_warn', 'warn', `Catalizador B1S12 caliente · ${Math.round(catalystB1s12TempC)} °C`, {
          catalyst_b1s12_temp_c: catalystB1s12TempC,
          catalyst_b1s12: catalystB1s12Obj ?? null,
        })
        raised.push('cat_b1s12_warn')
      }
    }

    // Catalyst B2S12 (OBD PID 0186)
    const catalystB2s12Obj = signals.catalyst_b2s12 as Record<string, unknown> | undefined
    const catalystB2s12TempC =
      typeof catalystB2s12Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s12Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s12_temp_c === 'number'
          ? (signals.catalyst_b2s12_temp_c as number)
          : null
    if (typeof catalystB2s12TempC === 'number') {
      const warnC = typeof signals.cat_b2s12_warn_c === 'number' ? (signals.cat_b2s12_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s12_alert_c === 'number' ? (signals.cat_b2s12_alert_c as number) : 850
      if (catalystB2s12TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s12_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s12_alert', 'critical', `Catalizador B2S12 crítico · ${Math.round(catalystB2s12TempC)} °C`, {
          catalyst_b2s12_temp_c: catalystB2s12TempC,
          catalyst_b2s12: catalystB2s12Obj ?? null,
        })
        raised.push('cat_b2s12_alert')
      } else if (
        catalystB2s12TempC >= warnC &&
        catalystB2s12TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s12_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s12_warn', 'warn', `Catalizador B2S12 caliente · ${Math.round(catalystB2s12TempC)} °C`, {
          catalyst_b2s12_temp_c: catalystB2s12TempC,
          catalyst_b2s12: catalystB2s12Obj ?? null,
        })
        raised.push('cat_b2s12_warn')
      }
    }

    // STFT bank 2 (OBD PID 0108)
    const stftB2Obj = signals.stft_b2 as Record<string, unknown> | undefined
    let stftB2Pct: number | null =
      typeof stftB2Obj?.trim_pct === 'number'
        ? (stftB2Obj.trim_pct as number)
        : typeof signals.fuel_trim_stft_b2_pct === 'number'
          ? (signals.fuel_trim_stft_b2_pct as number)
          : null
    const stftB2SpeedKmh =
      typeof stftB2Obj?.speed_kmh === 'number'
        ? (stftB2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof stftB2Pct === 'number') {
      const warnStftB2Pct =
        typeof signals.stft_b2_warn_pct === 'number' ? (signals.stft_b2_warn_pct as number) : 12
      const alertStftB2Pct =
        typeof signals.stft_b2_alert_pct === 'number' ? (signals.stft_b2_alert_pct as number) : 20
      const stftB2MinSpd =
        typeof signals.stft_b2_speed_min_kmh === 'number' ? (signals.stft_b2_speed_min_kmh as number) : 20
      const stftB2SpdOk = typeof stftB2SpeedKmh === 'number' && stftB2SpeedKmh >= stftB2MinSpd
      const absStftB2 = Math.abs(stftB2Pct)
      if (
        stftB2SpdOk &&
        absStftB2 >= alertStftB2Pct &&
        !recentlyAlerted(deviceId, 'stft_b2_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'stft_b2_alert',
          'critical',
          `STFT B2 crítico · ${stftB2Pct > 0 ? '+' : ''}${Math.round(stftB2Pct)}%`,
          {
            fuel_trim_stft_b2_pct: stftB2Pct,
            alert_pct: alertStftB2Pct,
            stft_b2: stftB2Obj ?? null,
          },
        )
        raised.push('stft_b2_alert')
      } else if (
        stftB2SpdOk &&
        absStftB2 >= warnStftB2Pct &&
        absStftB2 < alertStftB2Pct &&
        !recentlyAlerted(deviceId, 'stft_b2_warn', 120)
      ) {
        insertAlert(deviceId, 'stft_b2_warn', 'warn', `STFT B2 alto · ${stftB2Pct > 0 ? '+' : ''}${Math.round(stftB2Pct)}%`, {
          fuel_trim_stft_b2_pct: stftB2Pct,
          stft_b2: stftB2Obj ?? null,
        })
        raised.push('stft_b2_warn')
      }
    }

    // LTFT bank 2 (OBD PID 0109)
    const ltftB2Obj = signals.ltft_b2 as Record<string, unknown> | undefined
    let ltftB2Pct: number | null =
      typeof ltftB2Obj?.trim_pct === 'number'
        ? (ltftB2Obj.trim_pct as number)
        : typeof signals.fuel_trim_ltft_b2_pct === 'number'
          ? (signals.fuel_trim_ltft_b2_pct as number)
          : null
    const ltftB2SpeedKmh =
      typeof ltftB2Obj?.speed_kmh === 'number'
        ? (ltftB2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof ltftB2Pct === 'number') {
      const warnLtftB2Pct =
        typeof signals.ltft_b2_warn_pct === 'number' ? (signals.ltft_b2_warn_pct as number) : 12
      const alertLtftB2Pct =
        typeof signals.ltft_b2_alert_pct === 'number' ? (signals.ltft_b2_alert_pct as number) : 20
      const ltftB2MinSpd =
        typeof signals.ltft_b2_speed_min_kmh === 'number' ? (signals.ltft_b2_speed_min_kmh as number) : 20
      const ltftB2SpdOk = typeof ltftB2SpeedKmh === 'number' && ltftB2SpeedKmh >= ltftB2MinSpd
      const absLtftB2 = Math.abs(ltftB2Pct)
      if (
        ltftB2SpdOk &&
        absLtftB2 >= alertLtftB2Pct &&
        !recentlyAlerted(deviceId, 'ltft_b2_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'ltft_b2_alert',
          'critical',
          `LTFT B2 crítico · ${ltftB2Pct > 0 ? '+' : ''}${Math.round(ltftB2Pct)}%`,
          {
            fuel_trim_ltft_b2_pct: ltftB2Pct,
            alert_pct: alertLtftB2Pct,
            ltft_b2: ltftB2Obj ?? null,
          },
        )
        raised.push('ltft_b2_alert')
      } else if (
        ltftB2SpdOk &&
        absLtftB2 >= warnLtftB2Pct &&
        absLtftB2 < alertLtftB2Pct &&
        !recentlyAlerted(deviceId, 'ltft_b2_warn', 120)
      ) {
        insertAlert(deviceId, 'ltft_b2_warn', 'warn', `LTFT B2 alto · ${ltftB2Pct > 0 ? '+' : ''}${Math.round(ltftB2Pct)}%`, {
          fuel_trim_ltft_b2_pct: ltftB2Pct,
          ltft_b2: ltftB2Obj ?? null,
        })
        raised.push('ltft_b2_warn')
      }
    }

    // Catalyst B1S13 (OBD PID 0187)
    const catalystB1s13Obj = signals.catalyst_b1s13 as Record<string, unknown> | undefined
    const catalystB1s13TempC =
      typeof catalystB1s13Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s13Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s13_temp_c === 'number'
          ? (signals.catalyst_b1s13_temp_c as number)
          : null
    if (typeof catalystB1s13TempC === 'number') {
      const warnC = typeof signals.cat_b1s13_warn_c === 'number' ? (signals.cat_b1s13_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s13_alert_c === 'number' ? (signals.cat_b1s13_alert_c as number) : 850
      if (catalystB1s13TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s13_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s13_alert', 'critical', `Catalizador B1S13 crítico · ${Math.round(catalystB1s13TempC)} °C`, {
          catalyst_b1s13_temp_c: catalystB1s13TempC,
          catalyst_b1s13: catalystB1s13Obj ?? null,
        })
        raised.push('cat_b1s13_alert')
      } else if (
        catalystB1s13TempC >= warnC &&
        catalystB1s13TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s13_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s13_warn', 'warn', `Catalizador B1S13 caliente · ${Math.round(catalystB1s13TempC)} °C`, {
          catalyst_b1s13_temp_c: catalystB1s13TempC,
          catalyst_b1s13: catalystB1s13Obj ?? null,
        })
        raised.push('cat_b1s13_warn')
      }
    }

    // Catalyst B2S13 (OBD PID 0188)
    const catalystB2s13Obj = signals.catalyst_b2s13 as Record<string, unknown> | undefined
    const catalystB2s13TempC =
      typeof catalystB2s13Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s13Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s13_temp_c === 'number'
          ? (signals.catalyst_b2s13_temp_c as number)
          : null
    if (typeof catalystB2s13TempC === 'number') {
      const warnC = typeof signals.cat_b2s13_warn_c === 'number' ? (signals.cat_b2s13_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s13_alert_c === 'number' ? (signals.cat_b2s13_alert_c as number) : 850
      if (catalystB2s13TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s13_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s13_alert', 'critical', `Catalizador B2S13 crítico · ${Math.round(catalystB2s13TempC)} °C`, {
          catalyst_b2s13_temp_c: catalystB2s13TempC,
          catalyst_b2s13: catalystB2s13Obj ?? null,
        })
        raised.push('cat_b2s13_alert')
      } else if (
        catalystB2s13TempC >= warnC &&
        catalystB2s13TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s13_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s13_warn', 'warn', `Catalizador B2S13 caliente · ${Math.round(catalystB2s13TempC)} °C`, {
          catalyst_b2s13_temp_c: catalystB2s13TempC,
          catalyst_b2s13: catalystB2s13Obj ?? null,
        })
        raised.push('cat_b2s13_warn')
      }
    }

    // DPF aftertreatment trigger (OBD PID 018B)
    const dpfAftertreatmentObj = signals.dpf_aftertreatment as Record<string, unknown> | undefined
    const dpfTriggerPct =
      typeof dpfAftertreatmentObj?.trigger_pct === 'number'
        ? (dpfAftertreatmentObj.trigger_pct as number)
        : typeof signals.dpf_trigger_pct === 'number'
          ? (signals.dpf_trigger_pct as number)
          : null
    const dpfTriggerSpeed =
      typeof dpfAftertreatmentObj?.speed_kmh === 'number'
        ? (dpfAftertreatmentObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof dpfTriggerPct === 'number') {
      const warnPct = typeof signals.dpf_trigger_warn_pct === 'number' ? (signals.dpf_trigger_warn_pct as number) : 70
      const alertPct = typeof signals.dpf_trigger_alert_pct === 'number' ? (signals.dpf_trigger_alert_pct as number) : 85
      const minSpd = typeof signals.dpf_trigger_speed_min_kmh === 'number' ? (signals.dpf_trigger_speed_min_kmh as number) : 15
      const spdOk = typeof dpfTriggerSpeed === 'number' && dpfTriggerSpeed >= minSpd
      if (spdOk && dpfTriggerPct >= alertPct && !recentlyAlerted(deviceId, 'dpf_trigger_alert', 120)) {
        insertAlert(deviceId, 'dpf_trigger_alert', 'critical', `DPF trigger crítico · ${Math.round(dpfTriggerPct)}%`, {
          dpf_trigger_pct: dpfTriggerPct,
          dpf_aftertreatment: dpfAftertreatmentObj ?? null,
        })
        raised.push('dpf_trigger_alert')
      } else if (
        spdOk &&
        dpfTriggerPct >= warnPct &&
        dpfTriggerPct < alertPct &&
        !recentlyAlerted(deviceId, 'dpf_trigger_warn', 120)
      ) {
        insertAlert(deviceId, 'dpf_trigger_warn', 'warn', `DPF trigger alto · ${Math.round(dpfTriggerPct)}%`, {
          dpf_trigger_pct: dpfTriggerPct,
          dpf_aftertreatment: dpfAftertreatmentObj ?? null,
        })
        raised.push('dpf_trigger_warn')
      }
    }

    // Throttle G (OBD PID 018D)
    const throttleGObj = signals.throttle_g as Record<string, unknown> | undefined
    const throttleGPct =
      typeof throttleGObj?.throttle_pct === 'number'
        ? (throttleGObj.throttle_pct as number)
        : typeof signals.throttle_g_pct === 'number'
          ? (signals.throttle_g_pct as number)
          : null
    const thrGSpeed =
      typeof throttleGObj?.speed_kmh === 'number'
        ? (throttleGObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    const thrGMinSpd = typeof signals.thr_g_speed_min_kmh === 'number' ? (signals.thr_g_speed_min_kmh as number) : 20
    if (
      typeof throttleGPct === 'number' &&
      typeof thrGSpeed === 'number' &&
      thrGSpeed >= thrGMinSpd
    ) {
      const warnPct = typeof signals.thr_g_warn_pct === 'number' ? (signals.thr_g_warn_pct as number) : 75
      const alertPct = typeof signals.thr_g_alert_pct === 'number' ? (signals.thr_g_alert_pct as number) : 90
      if (throttleGPct >= alertPct && !recentlyAlerted(deviceId, 'thr_g_alert', 120)) {
        insertAlert(deviceId, 'thr_g_alert', 'critical', `Mariposa G crítica · ${Math.round(throttleGPct)}%`, {
          throttle_g_pct: throttleGPct,
          throttle_g: throttleGObj ?? null,
        })
        raised.push('thr_g_alert')
      } else if (
        throttleGPct >= warnPct &&
        throttleGPct < alertPct &&
        !recentlyAlerted(deviceId, 'thr_g_warn', 120)
      ) {
        insertAlert(deviceId, 'thr_g_warn', 'warn', `Mariposa G alta · ${Math.round(throttleGPct)}%`, {
          throttle_g_pct: throttleGPct,
          throttle_g: throttleGObj ?? null,
        })
        raised.push('thr_g_warn')
      }
    }

    // Engine friction torque (OBD PID 018E)
    const engFrictionObj = signals.eng_friction as Record<string, unknown> | undefined
    const engFrictionPct =
      typeof engFrictionObj?.friction_pct === 'number'
        ? (engFrictionObj.friction_pct as number)
        : typeof signals.engine_friction_pct === 'number'
          ? (signals.engine_friction_pct as number)
          : null
    const engFrictionSpeed =
      typeof engFrictionObj?.speed_kmh === 'number'
        ? (engFrictionObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof engFrictionPct === 'number') {
      const warnPct = typeof signals.eng_friction_warn_pct === 'number' ? (signals.eng_friction_warn_pct as number) : 35
      const alertPct = typeof signals.eng_friction_alert_pct === 'number' ? (signals.eng_friction_alert_pct as number) : 50
      const minSpd = typeof signals.eng_friction_speed_min_kmh === 'number' ? (signals.eng_friction_speed_min_kmh as number) : 20
      const spdOk = typeof engFrictionSpeed === 'number' && engFrictionSpeed >= minSpd
      const absF = Math.abs(engFrictionPct)
      if (spdOk && absF >= alertPct && !recentlyAlerted(deviceId, 'eng_friction_alert', 120)) {
        insertAlert(deviceId, 'eng_friction_alert', 'critical', `Fricción motor crítica · ${Math.round(engFrictionPct)}%`, {
          engine_friction_pct: engFrictionPct,
          eng_friction: engFrictionObj ?? null,
        })
        raised.push('eng_friction_alert')
      } else if (
        spdOk &&
        absF >= warnPct &&
        absF < alertPct &&
        !recentlyAlerted(deviceId, 'eng_friction_warn', 120)
      ) {
        insertAlert(deviceId, 'eng_friction_warn', 'warn', `Fricción motor alta · ${Math.round(engFrictionPct)}%`, {
          engine_friction_pct: engFrictionPct,
          eng_friction: engFrictionObj ?? null,
        })
        raised.push('eng_friction_warn')
      }
    }

    // Catalyst B1S14 (OBD PID 0189)
    const catalystB1s14Obj = signals.catalyst_b1s14 as Record<string, unknown> | undefined
    const catalystB1s14TempC =
      typeof catalystB1s14Obj?.catalyst_temp_c === 'number'
        ? (catalystB1s14Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b1s14_temp_c === 'number'
          ? (signals.catalyst_b1s14_temp_c as number)
          : null
    if (typeof catalystB1s14TempC === 'number') {
      const warnC = typeof signals.cat_b1s14_warn_c === 'number' ? (signals.cat_b1s14_warn_c as number) : 750
      const alertC = typeof signals.cat_b1s14_alert_c === 'number' ? (signals.cat_b1s14_alert_c as number) : 850
      if (catalystB1s14TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b1s14_alert', 300)) {
        insertAlert(deviceId, 'cat_b1s14_alert', 'critical', `Catalizador B1S14 crítico · ${Math.round(catalystB1s14TempC)} °C`, {
          catalyst_b1s14_temp_c: catalystB1s14TempC,
          catalyst_b1s14: catalystB1s14Obj ?? null,
        })
        raised.push('cat_b1s14_alert')
      } else if (
        catalystB1s14TempC >= warnC &&
        catalystB1s14TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b1s14_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b1s14_warn', 'warn', `Catalizador B1S14 caliente · ${Math.round(catalystB1s14TempC)} °C`, {
          catalyst_b1s14_temp_c: catalystB1s14TempC,
          catalyst_b1s14: catalystB1s14Obj ?? null,
        })
        raised.push('cat_b1s14_warn')
      }
    }

    // Catalyst B2S14 (OBD PID 018A)
    const catalystB2s14Obj = signals.catalyst_b2s14 as Record<string, unknown> | undefined
    const catalystB2s14TempC =
      typeof catalystB2s14Obj?.catalyst_temp_c === 'number'
        ? (catalystB2s14Obj.catalyst_temp_c as number)
        : typeof signals.catalyst_b2s14_temp_c === 'number'
          ? (signals.catalyst_b2s14_temp_c as number)
          : null
    if (typeof catalystB2s14TempC === 'number') {
      const warnC = typeof signals.cat_b2s14_warn_c === 'number' ? (signals.cat_b2s14_warn_c as number) : 750
      const alertC = typeof signals.cat_b2s14_alert_c === 'number' ? (signals.cat_b2s14_alert_c as number) : 850
      if (catalystB2s14TempC >= alertC && !recentlyAlerted(deviceId, 'cat_b2s14_alert', 300)) {
        insertAlert(deviceId, 'cat_b2s14_alert', 'critical', `Catalizador B2S14 crítico · ${Math.round(catalystB2s14TempC)} °C`, {
          catalyst_b2s14_temp_c: catalystB2s14TempC,
          catalyst_b2s14: catalystB2s14Obj ?? null,
        })
        raised.push('cat_b2s14_alert')
      } else if (
        catalystB2s14TempC >= warnC &&
        catalystB2s14TempC < alertC &&
        !recentlyAlerted(deviceId, 'cat_b2s14_warn', 300)
      ) {
        insertAlert(deviceId, 'cat_b2s14_warn', 'warn', `Catalizador B2S14 caliente · ${Math.round(catalystB2s14TempC)} °C`, {
          catalyst_b2s14_temp_c: catalystB2s14TempC,
          catalyst_b2s14: catalystB2s14Obj ?? null,
        })
        raised.push('cat_b2s14_warn')
      }
    }

    // O2 lambda B1S1 (OBD PID 018C)
    const o2LambdaObj = signals.o2_lambda as Record<string, unknown> | undefined
    const o2LambdaB1 =
      typeof o2LambdaObj?.lambda === 'number'
        ? (o2LambdaObj.lambda as number)
        : typeof signals.o2_lambda_b1 === 'number'
          ? (signals.o2_lambda_b1 as number)
          : null
    const o2LambdaSpeedKmh =
      typeof o2LambdaObj?.speed_kmh === 'number'
        ? (o2LambdaObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2LambdaB1 === 'number') {
      const warnL = typeof signals.o2_lambda_warn === 'number' ? (signals.o2_lambda_warn as number) : 1.1
      const alertL = typeof signals.o2_lambda_alert === 'number' ? (signals.o2_lambda_alert as number) : 1.15
      const minSpd = typeof signals.o2_lambda_speed_min_kmh === 'number' ? (signals.o2_lambda_speed_min_kmh as number) : 20
      const spdOk = typeof o2LambdaSpeedKmh === 'number' && o2LambdaSpeedKmh >= minSpd
      if (spdOk && o2LambdaB1 >= alertL && !recentlyAlerted(deviceId, 'o2_lambda_alert', 120)) {
        insertAlert(deviceId, 'o2_lambda_alert', 'critical', `Lambda O2 crítica · ${o2LambdaB1.toFixed(2)}`, {
          o2_lambda_b1: o2LambdaB1,
          o2_lambda: o2LambdaObj ?? null,
        })
        raised.push('o2_lambda_alert')
      } else if (
        spdOk &&
        o2LambdaB1 >= warnL &&
        o2LambdaB1 < alertL &&
        !recentlyAlerted(deviceId, 'o2_lambda_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_lambda_warn', 'warn', `Lambda O2 alta · ${o2LambdaB1.toFixed(2)}`, {
          o2_lambda_b1: o2LambdaB1,
          o2_lambda: o2LambdaObj ?? null,
        })
        raised.push('o2_lambda_warn')
      }
    }

    // PM sensor B1 (OBD PID 018F C/D)
    const pmB1Obj = signals.pm_b1 as Record<string, unknown> | undefined
    const pmB1Pct =
      typeof pmB1Obj?.pm_pct === 'number'
        ? (pmB1Obj.pm_pct as number)
        : typeof signals.pm_sensor_b1_pct === 'number'
          ? (signals.pm_sensor_b1_pct as number)
          : null
    const pmB1SpeedKmh =
      typeof pmB1Obj?.speed_kmh === 'number'
        ? (pmB1Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof pmB1Pct === 'number') {
      const warnPct = typeof signals.pm_b1_warn_pct === 'number' ? (signals.pm_b1_warn_pct as number) : 70
      const alertPct = typeof signals.pm_b1_alert_pct === 'number' ? (signals.pm_b1_alert_pct as number) : 85
      const minSpd = typeof signals.pm_b1_speed_min_kmh === 'number' ? (signals.pm_b1_speed_min_kmh as number) : 15
      const spdOk = typeof pmB1SpeedKmh === 'number' && pmB1SpeedKmh >= minSpd
      if (spdOk && pmB1Pct >= alertPct && !recentlyAlerted(deviceId, 'pm_b1_alert', 120)) {
        insertAlert(deviceId, 'pm_b1_alert', 'critical', `PM B1 crítico · ${Math.round(pmB1Pct)}%`, {
          pm_sensor_b1_pct: pmB1Pct,
          pm_b1: pmB1Obj ?? null,
        })
        raised.push('pm_b1_alert')
      } else if (
        spdOk &&
        pmB1Pct >= warnPct &&
        pmB1Pct < alertPct &&
        !recentlyAlerted(deviceId, 'pm_b1_warn', 120)
      ) {
        insertAlert(deviceId, 'pm_b1_warn', 'warn', `PM B1 alto · ${Math.round(pmB1Pct)}%`, {
          pm_sensor_b1_pct: pmB1Pct,
          pm_b1: pmB1Obj ?? null,
        })
        raised.push('pm_b1_warn')
      }
    }

    // PM sensor B2 (OBD PID 018F F/G)
    const pmB2Obj = signals.pm_b2 as Record<string, unknown> | undefined
    const pmB2Pct =
      typeof pmB2Obj?.pm_pct === 'number'
        ? (pmB2Obj.pm_pct as number)
        : typeof signals.pm_sensor_b2_pct === 'number'
          ? (signals.pm_sensor_b2_pct as number)
          : null
    const pmB2SpeedKmh =
      typeof pmB2Obj?.speed_kmh === 'number'
        ? (pmB2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof pmB2Pct === 'number') {
      const warnPct = typeof signals.pm_b2_warn_pct === 'number' ? (signals.pm_b2_warn_pct as number) : 70
      const alertPct = typeof signals.pm_b2_alert_pct === 'number' ? (signals.pm_b2_alert_pct as number) : 85
      const minSpd = typeof signals.pm_b2_speed_min_kmh === 'number' ? (signals.pm_b2_speed_min_kmh as number) : 15
      const spdOk = typeof pmB2SpeedKmh === 'number' && pmB2SpeedKmh >= minSpd
      if (spdOk && pmB2Pct >= alertPct && !recentlyAlerted(deviceId, 'pm_b2_alert', 120)) {
        insertAlert(deviceId, 'pm_b2_alert', 'critical', `PM B2 crítico · ${Math.round(pmB2Pct)}%`, {
          pm_sensor_b2_pct: pmB2Pct,
          pm_b2: pmB2Obj ?? null,
        })
        raised.push('pm_b2_alert')
      } else if (
        spdOk &&
        pmB2Pct >= warnPct &&
        pmB2Pct < alertPct &&
        !recentlyAlerted(deviceId, 'pm_b2_warn', 120)
      ) {
        insertAlert(deviceId, 'pm_b2_warn', 'warn', `PM B2 alto · ${Math.round(pmB2Pct)}%`, {
          pm_sensor_b2_pct: pmB2Pct,
          pm_b2: pmB2Obj ?? null,
        })
        raised.push('pm_b2_warn')
      }
    }

    // EGT B1S5 (OBD PID 0198)
    const egtB1s5Obj = signals.egt_b1s5 as Record<string, unknown> | undefined
    const egtB1s5TempC =
      typeof egtB1s5Obj?.egt_temp_c === 'number'
        ? (egtB1s5Obj.egt_temp_c as number)
        : typeof signals.egt_b1s5_temp_c === 'number'
          ? (signals.egt_b1s5_temp_c as number)
          : null
    if (typeof egtB1s5TempC === 'number') {
      const warnC = typeof signals.egt_b1s5_warn_c === 'number' ? (signals.egt_b1s5_warn_c as number) : 750
      const alertC = typeof signals.egt_b1s5_alert_c === 'number' ? (signals.egt_b1s5_alert_c as number) : 850
      if (egtB1s5TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b1s5_alert', 300)) {
        insertAlert(deviceId, 'egt_b1s5_alert', 'critical', `EGT B1S5 crítico · ${Math.round(egtB1s5TempC)} °C`, {
          egt_b1s5_temp_c: egtB1s5TempC,
          egt_b1s5: egtB1s5Obj ?? null,
        })
        raised.push('egt_b1s5_alert')
      } else if (
        egtB1s5TempC >= warnC &&
        egtB1s5TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b1s5_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b1s5_warn', 'warn', `EGT B1S5 caliente · ${Math.round(egtB1s5TempC)} °C`, {
          egt_b1s5_temp_c: egtB1s5TempC,
          egt_b1s5: egtB1s5Obj ?? null,
        })
        raised.push('egt_b1s5_warn')
      }
    }

    // EGT B2S5 (OBD PID 0199)
    const egtB2s5Obj = signals.egt_b2s5 as Record<string, unknown> | undefined
    const egtB2s5TempC =
      typeof egtB2s5Obj?.egt_temp_c === 'number'
        ? (egtB2s5Obj.egt_temp_c as number)
        : typeof signals.egt_b2s5_temp_c === 'number'
          ? (signals.egt_b2s5_temp_c as number)
          : null
    if (typeof egtB2s5TempC === 'number') {
      const warnC = typeof signals.egt_b2s5_warn_c === 'number' ? (signals.egt_b2s5_warn_c as number) : 750
      const alertC = typeof signals.egt_b2s5_alert_c === 'number' ? (signals.egt_b2s5_alert_c as number) : 850
      if (egtB2s5TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b2s5_alert', 300)) {
        insertAlert(deviceId, 'egt_b2s5_alert', 'critical', `EGT B2S5 crítico · ${Math.round(egtB2s5TempC)} °C`, {
          egt_b2s5_temp_c: egtB2s5TempC,
          egt_b2s5: egtB2s5Obj ?? null,
        })
        raised.push('egt_b2s5_alert')
      } else if (
        egtB2s5TempC >= warnC &&
        egtB2s5TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b2s5_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b2s5_warn', 'warn', `EGT B2S5 caliente · ${Math.round(egtB2s5TempC)} °C`, {
          egt_b2s5_temp_c: egtB2s5TempC,
          egt_b2s5: egtB2s5Obj ?? null,
        })
        raised.push('egt_b2s5_warn')
      }
    }

    // O2 lambda B1S3 (OBD PID 019C)
    const o2LmbB1s3Obj = signals.o2_lmb_b1s3 as Record<string, unknown> | undefined
    const o2LambdaB1s3 =
      typeof o2LmbB1s3Obj?.lambda === 'number'
        ? (o2LmbB1s3Obj.lambda as number)
        : typeof signals.o2_lambda_b1s3 === 'number'
          ? (signals.o2_lambda_b1s3 as number)
          : null
    const o2LmbB1s3Speed =
      typeof o2LmbB1s3Obj?.speed_kmh === 'number'
        ? (o2LmbB1s3Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2LambdaB1s3 === 'number') {
      const warnL = typeof signals.o2_lmb_b1s3_warn === 'number' ? (signals.o2_lmb_b1s3_warn as number) : 1.1
      const alertL = typeof signals.o2_lmb_b1s3_alert === 'number' ? (signals.o2_lmb_b1s3_alert as number) : 1.15
      const minSpd = typeof signals.o2_lmb_b1s3_speed_min_kmh === 'number' ? (signals.o2_lmb_b1s3_speed_min_kmh as number) : 20
      const spdOk = typeof o2LmbB1s3Speed === 'number' && o2LmbB1s3Speed >= minSpd
      if (spdOk && o2LambdaB1s3 >= alertL && !recentlyAlerted(deviceId, 'o2_lmb_b1s3_alert', 120)) {
        insertAlert(deviceId, 'o2_lmb_b1s3_alert', 'critical', `Lambda O2 B1S3 crítica · ${o2LambdaB1s3.toFixed(2)}`, {
          o2_lambda_b1s3: o2LambdaB1s3,
          o2_lmb_b1s3: o2LmbB1s3Obj ?? null,
        })
        raised.push('o2_lmb_b1s3_alert')
      } else if (
        spdOk &&
        o2LambdaB1s3 >= warnL &&
        o2LambdaB1s3 < alertL &&
        !recentlyAlerted(deviceId, 'o2_lmb_b1s3_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_lmb_b1s3_warn', 'warn', `Lambda O2 B1S3 alta · ${o2LambdaB1s3.toFixed(2)}`, {
          o2_lambda_b1s3: o2LambdaB1s3,
          o2_lmb_b1s3: o2LmbB1s3Obj ?? null,
        })
        raised.push('o2_lmb_b1s3_warn')
      }
    }

    // O2 lambda B2S3 (OBD PID 019C)
    const o2LmbB2s3Obj = signals.o2_lmb_b2s3 as Record<string, unknown> | undefined
    const o2LambdaB2s3 =
      typeof o2LmbB2s3Obj?.lambda === 'number'
        ? (o2LmbB2s3Obj.lambda as number)
        : typeof signals.o2_lambda_b2s3 === 'number'
          ? (signals.o2_lambda_b2s3 as number)
          : null
    const o2LmbB2s3Speed =
      typeof o2LmbB2s3Obj?.speed_kmh === 'number'
        ? (o2LmbB2s3Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2LambdaB2s3 === 'number') {
      const warnL = typeof signals.o2_lmb_b2s3_warn === 'number' ? (signals.o2_lmb_b2s3_warn as number) : 1.1
      const alertL = typeof signals.o2_lmb_b2s3_alert === 'number' ? (signals.o2_lmb_b2s3_alert as number) : 1.15
      const minSpd = typeof signals.o2_lmb_b2s3_speed_min_kmh === 'number' ? (signals.o2_lmb_b2s3_speed_min_kmh as number) : 20
      const spdOk = typeof o2LmbB2s3Speed === 'number' && o2LmbB2s3Speed >= minSpd
      if (spdOk && o2LambdaB2s3 >= alertL && !recentlyAlerted(deviceId, 'o2_lmb_b2s3_alert', 120)) {
        insertAlert(deviceId, 'o2_lmb_b2s3_alert', 'critical', `Lambda O2 B2S3 crítica · ${o2LambdaB2s3.toFixed(2)}`, {
          o2_lambda_b2s3: o2LambdaB2s3,
          o2_lmb_b2s3: o2LmbB2s3Obj ?? null,
        })
        raised.push('o2_lmb_b2s3_alert')
      } else if (
        spdOk &&
        o2LambdaB2s3 >= warnL &&
        o2LambdaB2s3 < alertL &&
        !recentlyAlerted(deviceId, 'o2_lmb_b2s3_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_lmb_b2s3_warn', 'warn', `Lambda O2 B2S3 alta · ${o2LambdaB2s3.toFixed(2)}`, {
          o2_lambda_b2s3: o2LambdaB2s3,
          o2_lmb_b2s3: o2LmbB2s3Obj ?? null,
        })
        raised.push('o2_lmb_b2s3_warn')
      }
    }

    // NOx reagent quality (OBD PID 0194)
    const noxReagentObj = signals.nox_reagent as Record<string, unknown> | undefined
    const noxReagentQualHours =
      typeof noxReagentObj?.qual_hours === 'number'
        ? (noxReagentObj.qual_hours as number)
        : typeof signals.nox_reagent_qual_hours === 'number'
          ? (signals.nox_reagent_qual_hours as number)
          : null
    if (typeof noxReagentQualHours === 'number') {
      const warnH = typeof signals.nox_req_warn_h === 'number' ? (signals.nox_req_warn_h as number) : 10
      const alertH = typeof signals.nox_req_alert_h === 'number' ? (signals.nox_req_alert_h as number) : 20
      if (noxReagentQualHours >= alertH && !recentlyAlerted(deviceId, 'nox_req_alert', 300)) {
        insertAlert(deviceId, 'nox_req_alert', 'critical', `Reactivo NOx crítico · ${Math.round(noxReagentQualHours)} h`, {
          nox_reagent_qual_hours: noxReagentQualHours,
          nox_reagent: noxReagentObj ?? null,
        })
        raised.push('nox_req_alert')
      } else if (
        noxReagentQualHours >= warnH &&
        noxReagentQualHours < alertH &&
        !recentlyAlerted(deviceId, 'nox_req_warn', 300)
      ) {
        insertAlert(deviceId, 'nox_req_warn', 'warn', `Reactivo NOx bajo · ${Math.round(noxReagentQualHours)} h`, {
          nox_reagent_qual_hours: noxReagentQualHours,
          nox_reagent: noxReagentObj ?? null,
        })
        raised.push('nox_req_warn')
      }
    }

    // NOx warning active (OBD PID 0194 byte B bit0)
    const noxWarnObj = signals.nox_warn as Record<string, unknown> | undefined
    const noxWarnActive =
      noxWarnObj?.active === true ||
      signals.nox_warning_active === 1 ||
      signals.nox_warning_active === true
    const noxWarnSpeed =
      typeof noxWarnObj?.speed_kmh === 'number'
        ? (noxWarnObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (noxWarnActive) {
      const minSpd =
        typeof signals.nox_warn_speed_min_kmh === 'number' ? (signals.nox_warn_speed_min_kmh as number) : 20
      const spdOk = typeof noxWarnSpeed === 'number' && noxWarnSpeed >= minSpd
      if (spdOk && !recentlyAlerted(deviceId, 'nox_warn_alert', 120)) {
        insertAlert(deviceId, 'nox_warn_alert', 'critical', 'Sistema aviso NOx activo', {
          nox_warning_active: 1,
          nox_warn: noxWarnObj ?? null,
        })
        raised.push('nox_warn_alert')
      }
    }

    // NOx inducement level 1 (OBD PID 0194)
    const noxIndL1Obj = signals.nox_ind_l1 as Record<string, unknown> | undefined
    const noxIndL1 =
      typeof noxIndL1Obj?.status === 'number'
        ? (noxIndL1Obj.status as number)
        : typeof signals.nox_induce_level1 === 'number'
          ? (signals.nox_induce_level1 as number)
          : null
    const noxIndL1Speed =
      typeof noxIndL1Obj?.speed_kmh === 'number'
        ? (noxIndL1Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxIndL1 === 'number') {
      const minSpd =
        typeof signals.nox_ind_l1_speed_min_kmh === 'number' ? (signals.nox_ind_l1_speed_min_kmh as number) : 20
      const spdOk = typeof noxIndL1Speed === 'number' && noxIndL1Speed >= minSpd
      if (spdOk && noxIndL1 >= 2 && !recentlyAlerted(deviceId, 'nox_ind_l1_alert', 120)) {
        insertAlert(deviceId, 'nox_ind_l1_alert', 'critical', `Inducement NOx L1 activo · ${noxIndL1}`, {
          nox_induce_level1: noxIndL1,
          nox_ind_l1: noxIndL1Obj ?? null,
        })
        raised.push('nox_ind_l1_alert')
      } else if (
        spdOk &&
        noxIndL1 === 1 &&
        !recentlyAlerted(deviceId, 'nox_ind_l1_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_ind_l1_warn', 'warn', `Inducement NOx L1 habilitado · ${noxIndL1}`, {
          nox_induce_level1: noxIndL1,
          nox_ind_l1: noxIndL1Obj ?? null,
        })
        raised.push('nox_ind_l1_warn')
      }
    }

    // NOx inducement level 2 (OBD PID 0194)
    const noxIndL2Obj = signals.nox_ind_l2 as Record<string, unknown> | undefined
    const noxIndL2 =
      typeof noxIndL2Obj?.status === 'number'
        ? (noxIndL2Obj.status as number)
        : typeof signals.nox_induce_level2 === 'number'
          ? (signals.nox_induce_level2 as number)
          : null
    const noxIndL2Speed =
      typeof noxIndL2Obj?.speed_kmh === 'number'
        ? (noxIndL2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxIndL2 === 'number') {
      const minSpd =
        typeof signals.nox_ind_l2_speed_min_kmh === 'number' ? (signals.nox_ind_l2_speed_min_kmh as number) : 20
      const spdOk = typeof noxIndL2Speed === 'number' && noxIndL2Speed >= minSpd
      if (spdOk && noxIndL2 >= 2 && !recentlyAlerted(deviceId, 'nox_ind_l2_alert', 120)) {
        insertAlert(deviceId, 'nox_ind_l2_alert', 'critical', `Inducement NOx L2 activo · ${noxIndL2}`, {
          nox_induce_level2: noxIndL2,
          nox_ind_l2: noxIndL2Obj ?? null,
        })
        raised.push('nox_ind_l2_alert')
      } else if (
        spdOk &&
        noxIndL2 === 1 &&
        !recentlyAlerted(deviceId, 'nox_ind_l2_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_ind_l2_warn', 'warn', `Inducement NOx L2 habilitado · ${noxIndL2}`, {
          nox_induce_level2: noxIndL2,
          nox_ind_l2: noxIndL2Obj ?? null,
        })
        raised.push('nox_ind_l2_warn')
      }
    }

    // NOx EGR valve counter hours (OBD PID 0194)
    const noxEgrObj = signals.nox_egr_counter as Record<string, unknown> | undefined
    const noxEgrH =
      typeof noxEgrObj?.egr_hours === 'number'
        ? (noxEgrObj.egr_hours as number)
        : typeof signals.nox_egr_valve_counter_hours === 'number'
          ? (signals.nox_egr_valve_counter_hours as number)
          : null
    if (typeof noxEgrH === 'number') {
      const warnH = typeof signals.nox_egr_warn_h === 'number' ? (signals.nox_egr_warn_h as number) : 50
      const alertH = typeof signals.nox_egr_alert_h === 'number' ? (signals.nox_egr_alert_h as number) : 100
      if (noxEgrH >= alertH && !recentlyAlerted(deviceId, 'nox_egr_counter_alert', 120)) {
        insertAlert(deviceId, 'nox_egr_counter_alert', 'critical', `Contador EGR NOx crítico · ${Math.round(noxEgrH)}h`, {
          nox_egr_valve_counter_hours: noxEgrH,
          nox_egr_counter: noxEgrObj ?? null,
        })
        raised.push('nox_egr_counter_alert')
      } else if (
        noxEgrH >= warnH &&
        noxEgrH < alertH &&
        !recentlyAlerted(deviceId, 'nox_egr_counter_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_egr_counter_warn', 'warn', `Contador EGR NOx alto · ${Math.round(noxEgrH)}h`, {
          nox_egr_valve_counter_hours: noxEgrH,
          nox_egr_counter: noxEgrObj ?? null,
        })
        raised.push('nox_egr_counter_warn')
      }
    }

    // NOx monitor malfunction hours (OBD PID 0194)
    const noxMalObj = signals.nox_monitor_malf as Record<string, unknown> | undefined
    const noxMalH =
      typeof noxMalObj?.malf_hours === 'number'
        ? (noxMalObj.malf_hours as number)
        : typeof signals.nox_monitor_malfunction_hours === 'number'
          ? (signals.nox_monitor_malfunction_hours as number)
          : null
    if (typeof noxMalH === 'number') {
      const warnH = typeof signals.nox_mal_warn_h === 'number' ? (signals.nox_mal_warn_h as number) : 50
      const alertH = typeof signals.nox_mal_alert_h === 'number' ? (signals.nox_mal_alert_h as number) : 100
      if (noxMalH >= alertH && !recentlyAlerted(deviceId, 'nox_monitor_malf_alert', 120)) {
        insertAlert(deviceId, 'nox_monitor_malf_alert', 'critical', `Malfunction NOx crítico · ${Math.round(noxMalH)}h`, {
          nox_monitor_malfunction_hours: noxMalH,
          nox_monitor_malf: noxMalObj ?? null,
        })
        raised.push('nox_monitor_malf_alert')
      } else if (
        noxMalH >= warnH &&
        noxMalH < alertH &&
        !recentlyAlerted(deviceId, 'nox_monitor_malf_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_monitor_malf_warn', 'warn', `Malfunction NOx alto · ${Math.round(noxMalH)}h`, {
          nox_monitor_malfunction_hours: noxMalH,
          nox_monitor_malf: noxMalObj ?? null,
        })
        raised.push('nox_monitor_malf_warn')
      }
    }

    // EGT B1S6 (OBD PID 0198)
    const egtB1s6Obj = signals.egt_b1s6 as Record<string, unknown> | undefined
    const egtB1s6TempC =
      typeof egtB1s6Obj?.egt_temp_c === 'number'
        ? (egtB1s6Obj.egt_temp_c as number)
        : typeof signals.egt_b1s6_temp_c === 'number'
          ? (signals.egt_b1s6_temp_c as number)
          : null
    if (typeof egtB1s6TempC === 'number') {
      const warnC = typeof signals.egt_b1s6_warn_c === 'number' ? (signals.egt_b1s6_warn_c as number) : 750
      const alertC = typeof signals.egt_b1s6_alert_c === 'number' ? (signals.egt_b1s6_alert_c as number) : 850
      if (egtB1s6TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b1s6_alert', 300)) {
        insertAlert(deviceId, 'egt_b1s6_alert', 'critical', `EGT B1S6 crítico · ${Math.round(egtB1s6TempC)} °C`, {
          egt_b1s6_temp_c: egtB1s6TempC,
          egt_b1s6: egtB1s6Obj ?? null,
        })
        raised.push('egt_b1s6_alert')
      } else if (
        egtB1s6TempC >= warnC &&
        egtB1s6TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b1s6_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b1s6_warn', 'warn', `EGT B1S6 caliente · ${Math.round(egtB1s6TempC)} °C`, {
          egt_b1s6_temp_c: egtB1s6TempC,
          egt_b1s6: egtB1s6Obj ?? null,
        })
        raised.push('egt_b1s6_warn')
      }
    }

    // EGT B2S6 (OBD PID 0199)
    const egtB2s6Obj = signals.egt_b2s6 as Record<string, unknown> | undefined
    const egtB2s6TempC =
      typeof egtB2s6Obj?.egt_temp_c === 'number'
        ? (egtB2s6Obj.egt_temp_c as number)
        : typeof signals.egt_b2s6_temp_c === 'number'
          ? (signals.egt_b2s6_temp_c as number)
          : null
    if (typeof egtB2s6TempC === 'number') {
      const warnC = typeof signals.egt_b2s6_warn_c === 'number' ? (signals.egt_b2s6_warn_c as number) : 750
      const alertC = typeof signals.egt_b2s6_alert_c === 'number' ? (signals.egt_b2s6_alert_c as number) : 850
      if (egtB2s6TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b2s6_alert', 300)) {
        insertAlert(deviceId, 'egt_b2s6_alert', 'critical', `EGT B2S6 crítico · ${Math.round(egtB2s6TempC)} °C`, {
          egt_b2s6_temp_c: egtB2s6TempC,
          egt_b2s6: egtB2s6Obj ?? null,
        })
        raised.push('egt_b2s6_alert')
      } else if (
        egtB2s6TempC >= warnC &&
        egtB2s6TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b2s6_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b2s6_warn', 'warn', `EGT B2S6 caliente · ${Math.round(egtB2s6TempC)} °C`, {
          egt_b2s6_temp_c: egtB2s6TempC,
          egt_b2s6: egtB2s6Obj ?? null,
        })
        raised.push('egt_b2s6_warn')
      }
    }

    // O2 lambda B1S4 (OBD PID 019C)
    const o2LmbB1s4Obj = signals.o2_lmb_b1s4 as Record<string, unknown> | undefined
    const o2LambdaB1s4 =
      typeof o2LmbB1s4Obj?.lambda === 'number'
        ? (o2LmbB1s4Obj.lambda as number)
        : typeof signals.o2_lambda_b1s4 === 'number'
          ? (signals.o2_lambda_b1s4 as number)
          : null
    const o2LmbB1s4Speed =
      typeof o2LmbB1s4Obj?.speed_kmh === 'number'
        ? (o2LmbB1s4Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2LambdaB1s4 === 'number') {
      const warnL = typeof signals.o2_lmb_b1s4_warn === 'number' ? (signals.o2_lmb_b1s4_warn as number) : 1.1
      const alertL = typeof signals.o2_lmb_b1s4_alert === 'number' ? (signals.o2_lmb_b1s4_alert as number) : 1.15
      const minSpd = typeof signals.o2_lmb_b1s4_speed_min_kmh === 'number' ? (signals.o2_lmb_b1s4_speed_min_kmh as number) : 20
      const spdOk = typeof o2LmbB1s4Speed === 'number' && o2LmbB1s4Speed >= minSpd
      if (spdOk && o2LambdaB1s4 >= alertL && !recentlyAlerted(deviceId, 'o2_lmb_b1s4_alert', 120)) {
        insertAlert(deviceId, 'o2_lmb_b1s4_alert', 'critical', `Lambda O2 B1S4 crítica · ${o2LambdaB1s4.toFixed(2)}`, {
          o2_lambda_b1s4: o2LambdaB1s4,
          o2_lmb_b1s4: o2LmbB1s4Obj ?? null,
        })
        raised.push('o2_lmb_b1s4_alert')
      } else if (
        spdOk &&
        o2LambdaB1s4 >= warnL &&
        o2LambdaB1s4 < alertL &&
        !recentlyAlerted(deviceId, 'o2_lmb_b1s4_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_lmb_b1s4_warn', 'warn', `Lambda O2 B1S4 alta · ${o2LambdaB1s4.toFixed(2)}`, {
          o2_lambda_b1s4: o2LambdaB1s4,
          o2_lmb_b1s4: o2LmbB1s4Obj ?? null,
        })
        raised.push('o2_lmb_b1s4_warn')
      }
    }

    // O2 lambda B2S4 (OBD PID 019C)
    const o2LmbB2s4Obj = signals.o2_lmb_b2s4 as Record<string, unknown> | undefined
    const o2LambdaB2s4 =
      typeof o2LmbB2s4Obj?.lambda === 'number'
        ? (o2LmbB2s4Obj.lambda as number)
        : typeof signals.o2_lambda_b2s4 === 'number'
          ? (signals.o2_lambda_b2s4 as number)
          : null
    const o2LmbB2s4Speed =
      typeof o2LmbB2s4Obj?.speed_kmh === 'number'
        ? (o2LmbB2s4Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2LambdaB2s4 === 'number') {
      const warnL = typeof signals.o2_lmb_b2s4_warn === 'number' ? (signals.o2_lmb_b2s4_warn as number) : 1.1
      const alertL = typeof signals.o2_lmb_b2s4_alert === 'number' ? (signals.o2_lmb_b2s4_alert as number) : 1.15
      const minSpd = typeof signals.o2_lmb_b2s4_speed_min_kmh === 'number' ? (signals.o2_lmb_b2s4_speed_min_kmh as number) : 20
      const spdOk = typeof o2LmbB2s4Speed === 'number' && o2LmbB2s4Speed >= minSpd
      if (spdOk && o2LambdaB2s4 >= alertL && !recentlyAlerted(deviceId, 'o2_lmb_b2s4_alert', 120)) {
        insertAlert(deviceId, 'o2_lmb_b2s4_alert', 'critical', `Lambda O2 B2S4 crítica · ${o2LambdaB2s4.toFixed(2)}`, {
          o2_lambda_b2s4: o2LambdaB2s4,
          o2_lmb_b2s4: o2LmbB2s4Obj ?? null,
        })
        raised.push('o2_lmb_b2s4_alert')
      } else if (
        spdOk &&
        o2LambdaB2s4 >= warnL &&
        o2LambdaB2s4 < alertL &&
        !recentlyAlerted(deviceId, 'o2_lmb_b2s4_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_lmb_b2s4_warn', 'warn', `Lambda O2 B2S4 alta · ${o2LambdaB2s4.toFixed(2)}`, {
          o2_lambda_b2s4: o2LambdaB2s4,
          o2_lmb_b2s4: o2LmbB2s4Obj ?? null,
        })
        raised.push('o2_lmb_b2s4_warn')
      }
    }

    // Diesel exhaust fluid (OBD PID 019B)
    const defFluidObj = signals.def_fluid as Record<string, unknown> | undefined
    const defFluidPct =
      typeof defFluidObj?.def_pct === 'number'
        ? (defFluidObj.def_pct as number)
        : typeof signals.def_fluid_pct === 'number'
          ? (signals.def_fluid_pct as number)
          : null
    if (typeof defFluidPct === 'number') {
      const warnPct = typeof signals.def_warn_pct === 'number' ? (signals.def_warn_pct as number) : 25
      const alertPct = typeof signals.def_alert_pct === 'number' ? (signals.def_alert_pct as number) : 15
      if (defFluidPct <= alertPct && !recentlyAlerted(deviceId, 'def_alert', 300)) {
        insertAlert(deviceId, 'def_alert', 'critical', `DEF crítico · ${Math.round(defFluidPct)}%`, {
          def_fluid_pct: defFluidPct,
          def_fluid: defFluidObj ?? null,
        })
        raised.push('def_alert')
      } else if (
        defFluidPct <= warnPct &&
        defFluidPct > alertPct &&
        !recentlyAlerted(deviceId, 'def_warn', 300)
      ) {
        insertAlert(deviceId, 'def_warn', 'warn', `DEF bajo · ${Math.round(defFluidPct)}%`, {
          def_fluid_pct: defFluidPct,
          def_fluid: defFluidObj ?? null,
        })
        raised.push('def_warn')
      }
    }

    // EGT B1S7 (OBD PID 0198)
    const egtB1s7Obj = signals.egt_b1s7 as Record<string, unknown> | undefined
    const egtB1s7TempC =
      typeof egtB1s7Obj?.egt_temp_c === 'number'
        ? (egtB1s7Obj.egt_temp_c as number)
        : typeof signals.egt_b1s7_temp_c === 'number'
          ? (signals.egt_b1s7_temp_c as number)
          : null
    if (typeof egtB1s7TempC === 'number') {
      const warnC = typeof signals.egt_b1s7_warn_c === 'number' ? (signals.egt_b1s7_warn_c as number) : 750
      const alertC = typeof signals.egt_b1s7_alert_c === 'number' ? (signals.egt_b1s7_alert_c as number) : 850
      if (egtB1s7TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b1s7_alert', 300)) {
        insertAlert(deviceId, 'egt_b1s7_alert', 'critical', `EGT B1S7 crítico · ${Math.round(egtB1s7TempC)} °C`, {
          egt_b1s7_temp_c: egtB1s7TempC,
          egt_b1s7: egtB1s7Obj ?? null,
        })
        raised.push('egt_b1s7_alert')
      } else if (
        egtB1s7TempC >= warnC &&
        egtB1s7TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b1s7_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b1s7_warn', 'warn', `EGT B1S7 caliente · ${Math.round(egtB1s7TempC)} °C`, {
          egt_b1s7_temp_c: egtB1s7TempC,
          egt_b1s7: egtB1s7Obj ?? null,
        })
        raised.push('egt_b1s7_warn')
      }
    }

    // EGT B2S7 (OBD PID 0199)
    const egtB2s7Obj = signals.egt_b2s7 as Record<string, unknown> | undefined
    const egtB2s7TempC =
      typeof egtB2s7Obj?.egt_temp_c === 'number'
        ? (egtB2s7Obj.egt_temp_c as number)
        : typeof signals.egt_b2s7_temp_c === 'number'
          ? (signals.egt_b2s7_temp_c as number)
          : null
    if (typeof egtB2s7TempC === 'number') {
      const warnC = typeof signals.egt_b2s7_warn_c === 'number' ? (signals.egt_b2s7_warn_c as number) : 750
      const alertC = typeof signals.egt_b2s7_alert_c === 'number' ? (signals.egt_b2s7_alert_c as number) : 850
      if (egtB2s7TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b2s7_alert', 300)) {
        insertAlert(deviceId, 'egt_b2s7_alert', 'critical', `EGT B2S7 crítico · ${Math.round(egtB2s7TempC)} °C`, {
          egt_b2s7_temp_c: egtB2s7TempC,
          egt_b2s7: egtB2s7Obj ?? null,
        })
        raised.push('egt_b2s7_alert')
      } else if (
        egtB2s7TempC >= warnC &&
        egtB2s7TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b2s7_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b2s7_warn', 'warn', `EGT B2S7 caliente · ${Math.round(egtB2s7TempC)} °C`, {
          egt_b2s7_temp_c: egtB2s7TempC,
          egt_b2s7: egtB2s7Obj ?? null,
        })
        raised.push('egt_b2s7_warn')
      }
    }

    // EGT B1S8 (OBD PID 0198)
    const egtB1s8Obj = signals.egt_b1s8 as Record<string, unknown> | undefined
    const egtB1s8TempC =
      typeof egtB1s8Obj?.egt_temp_c === 'number'
        ? (egtB1s8Obj.egt_temp_c as number)
        : typeof signals.egt_b1s8_temp_c === 'number'
          ? (signals.egt_b1s8_temp_c as number)
          : null
    if (typeof egtB1s8TempC === 'number') {
      const warnC = typeof signals.egt_b1s8_warn_c === 'number' ? (signals.egt_b1s8_warn_c as number) : 750
      const alertC = typeof signals.egt_b1s8_alert_c === 'number' ? (signals.egt_b1s8_alert_c as number) : 850
      if (egtB1s8TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b1s8_alert', 300)) {
        insertAlert(deviceId, 'egt_b1s8_alert', 'critical', `EGT B1S8 crítico · ${Math.round(egtB1s8TempC)} °C`, {
          egt_b1s8_temp_c: egtB1s8TempC,
          egt_b1s8: egtB1s8Obj ?? null,
        })
        raised.push('egt_b1s8_alert')
      } else if (
        egtB1s8TempC >= warnC &&
        egtB1s8TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b1s8_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b1s8_warn', 'warn', `EGT B1S8 caliente · ${Math.round(egtB1s8TempC)} °C`, {
          egt_b1s8_temp_c: egtB1s8TempC,
          egt_b1s8: egtB1s8Obj ?? null,
        })
        raised.push('egt_b1s8_warn')
      }
    }

    // EGT B2S8 (OBD PID 0199)
    const egtB2s8Obj = signals.egt_b2s8 as Record<string, unknown> | undefined
    const egtB2s8TempC =
      typeof egtB2s8Obj?.egt_temp_c === 'number'
        ? (egtB2s8Obj.egt_temp_c as number)
        : typeof signals.egt_b2s8_temp_c === 'number'
          ? (signals.egt_b2s8_temp_c as number)
          : null
    if (typeof egtB2s8TempC === 'number') {
      const warnC = typeof signals.egt_b2s8_warn_c === 'number' ? (signals.egt_b2s8_warn_c as number) : 750
      const alertC = typeof signals.egt_b2s8_alert_c === 'number' ? (signals.egt_b2s8_alert_c as number) : 850
      if (egtB2s8TempC >= alertC && !recentlyAlerted(deviceId, 'egt_b2s8_alert', 300)) {
        insertAlert(deviceId, 'egt_b2s8_alert', 'critical', `EGT B2S8 crítico · ${Math.round(egtB2s8TempC)} °C`, {
          egt_b2s8_temp_c: egtB2s8TempC,
          egt_b2s8: egtB2s8Obj ?? null,
        })
        raised.push('egt_b2s8_alert')
      } else if (
        egtB2s8TempC >= warnC &&
        egtB2s8TempC < alertC &&
        !recentlyAlerted(deviceId, 'egt_b2s8_warn', 300)
      ) {
        insertAlert(deviceId, 'egt_b2s8_warn', 'warn', `EGT B2S8 caliente · ${Math.round(egtB2s8TempC)} °C`, {
          egt_b2s8_temp_c: egtB2s8TempC,
          egt_b2s8: egtB2s8Obj ?? null,
        })
        raised.push('egt_b2s8_warn')
      }
    }

    // O2 concentration B1S3 (OBD PID 019C)
    const o2ConcB1s3Obj = signals.o2_conc_b1s3 as Record<string, unknown> | undefined
    const o2ConcB1s3Pct =
      typeof o2ConcB1s3Obj?.conc_pct === 'number'
        ? (o2ConcB1s3Obj.conc_pct as number)
        : typeof signals.o2_conc_b1s3_pct === 'number'
          ? (signals.o2_conc_b1s3_pct as number)
          : null
    const o2ConcB1s3Speed =
      typeof o2ConcB1s3Obj?.speed_kmh === 'number'
        ? (o2ConcB1s3Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2ConcB1s3Pct === 'number') {
      const warnP = typeof signals.o2_conc_b1s3_warn === 'number' ? (signals.o2_conc_b1s3_warn as number) : 12
      const alertP = typeof signals.o2_conc_b1s3_alert === 'number' ? (signals.o2_conc_b1s3_alert as number) : 18
      const minSpd = typeof signals.o2_conc_b1s3_speed_min_kmh === 'number' ? (signals.o2_conc_b1s3_speed_min_kmh as number) : 20
      const spdOk = typeof o2ConcB1s3Speed === 'number' && o2ConcB1s3Speed >= minSpd
      if (spdOk && o2ConcB1s3Pct >= alertP && !recentlyAlerted(deviceId, 'o2_conc_b1s3_alert', 120)) {
        insertAlert(deviceId, 'o2_conc_b1s3_alert', 'critical', `O2 conc B1S3 crítica · ${o2ConcB1s3Pct.toFixed(1)}%`, {
          o2_conc_b1s3_pct: o2ConcB1s3Pct,
          o2_conc_b1s3: o2ConcB1s3Obj ?? null,
        })
        raised.push('o2_conc_b1s3_alert')
      } else if (
        spdOk &&
        o2ConcB1s3Pct >= warnP &&
        o2ConcB1s3Pct < alertP &&
        !recentlyAlerted(deviceId, 'o2_conc_b1s3_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_conc_b1s3_warn', 'warn', `O2 conc B1S3 alta · ${o2ConcB1s3Pct.toFixed(1)}%`, {
          o2_conc_b1s3_pct: o2ConcB1s3Pct,
          o2_conc_b1s3: o2ConcB1s3Obj ?? null,
        })
        raised.push('o2_conc_b1s3_warn')
      }
    }

    // O2 concentration B1S4 (OBD PID 019C)
    const o2ConcB1s4Obj = signals.o2_conc_b1s4 as Record<string, unknown> | undefined
    const o2ConcB1s4Pct =
      typeof o2ConcB1s4Obj?.conc_pct === 'number'
        ? (o2ConcB1s4Obj.conc_pct as number)
        : typeof signals.o2_conc_b1s4_pct === 'number'
          ? (signals.o2_conc_b1s4_pct as number)
          : null
    const o2ConcB1s4Speed =
      typeof o2ConcB1s4Obj?.speed_kmh === 'number'
        ? (o2ConcB1s4Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2ConcB1s4Pct === 'number') {
      const warnP = typeof signals.o2_conc_b1s4_warn === 'number' ? (signals.o2_conc_b1s4_warn as number) : 12
      const alertP = typeof signals.o2_conc_b1s4_alert === 'number' ? (signals.o2_conc_b1s4_alert as number) : 18
      const minSpd = typeof signals.o2_conc_b1s4_speed_min_kmh === 'number' ? (signals.o2_conc_b1s4_speed_min_kmh as number) : 20
      const spdOk = typeof o2ConcB1s4Speed === 'number' && o2ConcB1s4Speed >= minSpd
      if (spdOk && o2ConcB1s4Pct >= alertP && !recentlyAlerted(deviceId, 'o2_conc_b1s4_alert', 120)) {
        insertAlert(deviceId, 'o2_conc_b1s4_alert', 'critical', `O2 conc B1S4 crítica · ${o2ConcB1s4Pct.toFixed(1)}%`, {
          o2_conc_b1s4_pct: o2ConcB1s4Pct,
          o2_conc_b1s4: o2ConcB1s4Obj ?? null,
        })
        raised.push('o2_conc_b1s4_alert')
      } else if (
        spdOk &&
        o2ConcB1s4Pct >= warnP &&
        o2ConcB1s4Pct < alertP &&
        !recentlyAlerted(deviceId, 'o2_conc_b1s4_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_conc_b1s4_warn', 'warn', `O2 conc B1S4 alta · ${o2ConcB1s4Pct.toFixed(1)}%`, {
          o2_conc_b1s4_pct: o2ConcB1s4Pct,
          o2_conc_b1s4: o2ConcB1s4Obj ?? null,
        })
        raised.push('o2_conc_b1s4_warn')
      }
    }

    // O2 concentration B2S3 (OBD PID 019C)
    const o2ConcB2s3Obj = signals.o2_conc_b2s3 as Record<string, unknown> | undefined
    const o2ConcB2s3Pct =
      typeof o2ConcB2s3Obj?.conc_pct === 'number'
        ? (o2ConcB2s3Obj.conc_pct as number)
        : typeof signals.o2_conc_b2s3_pct === 'number'
          ? (signals.o2_conc_b2s3_pct as number)
          : null
    const o2ConcB2s3Speed =
      typeof o2ConcB2s3Obj?.speed_kmh === 'number'
        ? (o2ConcB2s3Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2ConcB2s3Pct === 'number') {
      const warnP = typeof signals.o2_conc_b2s3_warn === 'number' ? (signals.o2_conc_b2s3_warn as number) : 12
      const alertP = typeof signals.o2_conc_b2s3_alert === 'number' ? (signals.o2_conc_b2s3_alert as number) : 18
      const minSpd = typeof signals.o2_conc_b2s3_speed_min_kmh === 'number' ? (signals.o2_conc_b2s3_speed_min_kmh as number) : 20
      const spdOk = typeof o2ConcB2s3Speed === 'number' && o2ConcB2s3Speed >= minSpd
      if (spdOk && o2ConcB2s3Pct >= alertP && !recentlyAlerted(deviceId, 'o2_conc_b2s3_alert', 120)) {
        insertAlert(deviceId, 'o2_conc_b2s3_alert', 'critical', `O2 conc B2S3 crítica · ${o2ConcB2s3Pct.toFixed(1)}%`, {
          o2_conc_b2s3_pct: o2ConcB2s3Pct,
          o2_conc_b2s3: o2ConcB2s3Obj ?? null,
        })
        raised.push('o2_conc_b2s3_alert')
      } else if (
        spdOk &&
        o2ConcB2s3Pct >= warnP &&
        o2ConcB2s3Pct < alertP &&
        !recentlyAlerted(deviceId, 'o2_conc_b2s3_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_conc_b2s3_warn', 'warn', `O2 conc B2S3 alta · ${o2ConcB2s3Pct.toFixed(1)}%`, {
          o2_conc_b2s3_pct: o2ConcB2s3Pct,
          o2_conc_b2s3: o2ConcB2s3Obj ?? null,
        })
        raised.push('o2_conc_b2s3_warn')
      }
    }

    // O2 concentration B2S4 (OBD PID 019C)
    const o2ConcB2s4Obj = signals.o2_conc_b2s4 as Record<string, unknown> | undefined
    const o2ConcB2s4Pct =
      typeof o2ConcB2s4Obj?.conc_pct === 'number'
        ? (o2ConcB2s4Obj.conc_pct as number)
        : typeof signals.o2_conc_b2s4_pct === 'number'
          ? (signals.o2_conc_b2s4_pct as number)
          : null
    const o2ConcB2s4Speed =
      typeof o2ConcB2s4Obj?.speed_kmh === 'number'
        ? (o2ConcB2s4Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof o2ConcB2s4Pct === 'number') {
      const warnP = typeof signals.o2_conc_b2s4_warn === 'number' ? (signals.o2_conc_b2s4_warn as number) : 12
      const alertP = typeof signals.o2_conc_b2s4_alert === 'number' ? (signals.o2_conc_b2s4_alert as number) : 18
      const minSpd = typeof signals.o2_conc_b2s4_speed_min_kmh === 'number' ? (signals.o2_conc_b2s4_speed_min_kmh as number) : 20
      const spdOk = typeof o2ConcB2s4Speed === 'number' && o2ConcB2s4Speed >= minSpd
      if (spdOk && o2ConcB2s4Pct >= alertP && !recentlyAlerted(deviceId, 'o2_conc_b2s4_alert', 120)) {
        insertAlert(deviceId, 'o2_conc_b2s4_alert', 'critical', `O2 conc B2S4 crítica · ${o2ConcB2s4Pct.toFixed(1)}%`, {
          o2_conc_b2s4_pct: o2ConcB2s4Pct,
          o2_conc_b2s4: o2ConcB2s4Obj ?? null,
        })
        raised.push('o2_conc_b2s4_alert')
      } else if (
        spdOk &&
        o2ConcB2s4Pct >= warnP &&
        o2ConcB2s4Pct < alertP &&
        !recentlyAlerted(deviceId, 'o2_conc_b2s4_warn', 120)
      ) {
        insertAlert(deviceId, 'o2_conc_b2s4_warn', 'warn', `O2 conc B2S4 alta · ${o2ConcB2s4Pct.toFixed(1)}%`, {
          o2_conc_b2s4_pct: o2ConcB2s4Pct,
          o2_conc_b2s4: o2ConcB2s4Obj ?? null,
        })
        raised.push('o2_conc_b2s4_warn')
      }
    }

    // Commanded DEF dosing (OBD PID 01A5)
    const defDoseObj = signals.def_dose as Record<string, unknown> | undefined
    const defDosePct =
      typeof defDoseObj?.dose_pct === 'number'
        ? (defDoseObj.dose_pct as number)
        : typeof signals.def_dosing_cmd_pct === 'number'
          ? (signals.def_dosing_cmd_pct as number)
          : null
    const defDoseSpeed =
      typeof defDoseObj?.speed_kmh === 'number'
        ? (defDoseObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof defDosePct === 'number') {
      const warnP = typeof signals.def_dose_warn_pct === 'number' ? (signals.def_dose_warn_pct as number) : 60
      const alertP = typeof signals.def_dose_alert_pct === 'number' ? (signals.def_dose_alert_pct as number) : 90
      const minSpd = typeof signals.def_dose_speed_min_kmh === 'number' ? (signals.def_dose_speed_min_kmh as number) : 20
      const spdOk = typeof defDoseSpeed === 'number' && defDoseSpeed >= minSpd
      if (spdOk && defDosePct >= alertP && !recentlyAlerted(deviceId, 'def_dose_alert', 120)) {
        insertAlert(deviceId, 'def_dose_alert', 'critical', `DEF dosing crítico · ${Math.round(defDosePct)}%`, {
          def_dosing_cmd_pct: defDosePct,
          def_dose: defDoseObj ?? null,
        })
        raised.push('def_dose_alert')
      } else if (
        spdOk &&
        defDosePct >= warnP &&
        defDosePct < alertP &&
        !recentlyAlerted(deviceId, 'def_dose_warn', 120)
      ) {
        insertAlert(deviceId, 'def_dose_warn', 'warn', `DEF dosing alto · ${Math.round(defDosePct)}%`, {
          def_dosing_cmd_pct: defDosePct,
          def_dose: defDoseObj ?? null,
        })
        raised.push('def_dose_warn')
      }
    }

    // NOx corrected B1S1 (OBD PID 01A1)
    const noxCorrB1s1Obj = signals.nox_corr_b1s1 as Record<string, unknown> | undefined
    const noxCorrB1s1Ppm =
      typeof noxCorrB1s1Obj?.nox_ppm === 'number'
        ? (noxCorrB1s1Obj.nox_ppm as number)
        : typeof signals.nox_corrected_b1s1_ppm === 'number'
          ? (signals.nox_corrected_b1s1_ppm as number)
          : null
    const noxCorrB1s1Speed =
      typeof noxCorrB1s1Obj?.speed_kmh === 'number'
        ? (noxCorrB1s1Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxCorrB1s1Ppm === 'number') {
      const warnP = typeof signals.nox_corr_b1s1_warn === 'number' ? (signals.nox_corr_b1s1_warn as number) : 600
      const alertP = typeof signals.nox_corr_b1s1_alert === 'number' ? (signals.nox_corr_b1s1_alert as number) : 800
      const minSpd = typeof signals.nox_corr_b1s1_speed_min_kmh === 'number' ? (signals.nox_corr_b1s1_speed_min_kmh as number) : 20
      const spdOk = typeof noxCorrB1s1Speed === 'number' && noxCorrB1s1Speed >= minSpd
      if (spdOk && noxCorrB1s1Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_corr_b1s1_alert', 120)) {
        insertAlert(deviceId, 'nox_corr_b1s1_alert', 'critical', `NOx corregido B1S1 crítico · ${Math.round(noxCorrB1s1Ppm)} ppm`, {
          nox_corrected_b1s1_ppm: noxCorrB1s1Ppm,
          nox_corr_b1s1: noxCorrB1s1Obj ?? null,
        })
        raised.push('nox_corr_b1s1_alert')
      } else if (
        spdOk &&
        noxCorrB1s1Ppm >= warnP &&
        noxCorrB1s1Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_corr_b1s1_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_corr_b1s1_warn', 'warn', `NOx corregido B1S1 alto · ${Math.round(noxCorrB1s1Ppm)} ppm`, {
          nox_corrected_b1s1_ppm: noxCorrB1s1Ppm,
          nox_corr_b1s1: noxCorrB1s1Obj ?? null,
        })
        raised.push('nox_corr_b1s1_warn')
      }
    }

    // NOx corrected B1S2 (OBD PID 01A1)
    const noxCorrB1s2Obj = signals.nox_corr_b1s2 as Record<string, unknown> | undefined
    const noxCorrB1s2Ppm =
      typeof noxCorrB1s2Obj?.nox_ppm === 'number'
        ? (noxCorrB1s2Obj.nox_ppm as number)
        : typeof signals.nox_corrected_b1s2_ppm === 'number'
          ? (signals.nox_corrected_b1s2_ppm as number)
          : null
    const noxCorrB1s2Speed =
      typeof noxCorrB1s2Obj?.speed_kmh === 'number'
        ? (noxCorrB1s2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxCorrB1s2Ppm === 'number') {
      const warnP = typeof signals.nox_corr_b1s2_warn === 'number' ? (signals.nox_corr_b1s2_warn as number) : 600
      const alertP = typeof signals.nox_corr_b1s2_alert === 'number' ? (signals.nox_corr_b1s2_alert as number) : 800
      const minSpd = typeof signals.nox_corr_b1s2_speed_min_kmh === 'number' ? (signals.nox_corr_b1s2_speed_min_kmh as number) : 20
      const spdOk = typeof noxCorrB1s2Speed === 'number' && noxCorrB1s2Speed >= minSpd
      if (spdOk && noxCorrB1s2Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_corr_b1s2_alert', 120)) {
        insertAlert(deviceId, 'nox_corr_b1s2_alert', 'critical', `NOx corregido B1S2 crítico · ${Math.round(noxCorrB1s2Ppm)} ppm`, {
          nox_corrected_b1s2_ppm: noxCorrB1s2Ppm,
          nox_corr_b1s2: noxCorrB1s2Obj ?? null,
        })
        raised.push('nox_corr_b1s2_alert')
      } else if (
        spdOk &&
        noxCorrB1s2Ppm >= warnP &&
        noxCorrB1s2Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_corr_b1s2_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_corr_b1s2_warn', 'warn', `NOx corregido B1S2 alto · ${Math.round(noxCorrB1s2Ppm)} ppm`, {
          nox_corrected_b1s2_ppm: noxCorrB1s2Ppm,
          nox_corr_b1s2: noxCorrB1s2Obj ?? null,
        })
        raised.push('nox_corr_b1s2_warn')
      }
    }

    // NOx corrected B2S1 (OBD PID 01A1)
    const noxCorrB2s1Obj = signals.nox_corr_b2s1 as Record<string, unknown> | undefined
    const noxCorrB2s1Ppm =
      typeof noxCorrB2s1Obj?.nox_ppm === 'number'
        ? (noxCorrB2s1Obj.nox_ppm as number)
        : typeof signals.nox_corrected_b2s1_ppm === 'number'
          ? (signals.nox_corrected_b2s1_ppm as number)
          : null
    const noxCorrB2s1Speed =
      typeof noxCorrB2s1Obj?.speed_kmh === 'number'
        ? (noxCorrB2s1Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxCorrB2s1Ppm === 'number') {
      const warnP = typeof signals.nox_corr_b2s1_warn === 'number' ? (signals.nox_corr_b2s1_warn as number) : 600
      const alertP = typeof signals.nox_corr_b2s1_alert === 'number' ? (signals.nox_corr_b2s1_alert as number) : 800
      const minSpd = typeof signals.nox_corr_b2s1_speed_min_kmh === 'number' ? (signals.nox_corr_b2s1_speed_min_kmh as number) : 20
      const spdOk = typeof noxCorrB2s1Speed === 'number' && noxCorrB2s1Speed >= minSpd
      if (spdOk && noxCorrB2s1Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_corr_b2s1_alert', 120)) {
        insertAlert(deviceId, 'nox_corr_b2s1_alert', 'critical', `NOx corregido B2S1 crítico · ${Math.round(noxCorrB2s1Ppm)} ppm`, {
          nox_corrected_b2s1_ppm: noxCorrB2s1Ppm,
          nox_corr_b2s1: noxCorrB2s1Obj ?? null,
        })
        raised.push('nox_corr_b2s1_alert')
      } else if (
        spdOk &&
        noxCorrB2s1Ppm >= warnP &&
        noxCorrB2s1Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_corr_b2s1_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_corr_b2s1_warn', 'warn', `NOx corregido B2S1 alto · ${Math.round(noxCorrB2s1Ppm)} ppm`, {
          nox_corrected_b2s1_ppm: noxCorrB2s1Ppm,
          nox_corr_b2s1: noxCorrB2s1Obj ?? null,
        })
        raised.push('nox_corr_b2s1_warn')
      }
    }

    // NOx corrected B2S2 (OBD PID 01A1)
    const noxCorrB2s2Obj = signals.nox_corr_b2s2 as Record<string, unknown> | undefined
    const noxCorrB2s2Ppm =
      typeof noxCorrB2s2Obj?.nox_ppm === 'number'
        ? (noxCorrB2s2Obj.nox_ppm as number)
        : typeof signals.nox_corrected_b2s2_ppm === 'number'
          ? (signals.nox_corrected_b2s2_ppm as number)
          : null
    const noxCorrB2s2Speed =
      typeof noxCorrB2s2Obj?.speed_kmh === 'number'
        ? (noxCorrB2s2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxCorrB2s2Ppm === 'number') {
      const warnP = typeof signals.nox_corr_b2s2_warn === 'number' ? (signals.nox_corr_b2s2_warn as number) : 600
      const alertP = typeof signals.nox_corr_b2s2_alert === 'number' ? (signals.nox_corr_b2s2_alert as number) : 800
      const minSpd = typeof signals.nox_corr_b2s2_speed_min_kmh === 'number' ? (signals.nox_corr_b2s2_speed_min_kmh as number) : 20
      const spdOk = typeof noxCorrB2s2Speed === 'number' && noxCorrB2s2Speed >= minSpd
      if (spdOk && noxCorrB2s2Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_corr_b2s2_alert', 120)) {
        insertAlert(deviceId, 'nox_corr_b2s2_alert', 'critical', `NOx corregido B2S2 crítico · ${Math.round(noxCorrB2s2Ppm)} ppm`, {
          nox_corrected_b2s2_ppm: noxCorrB2s2Ppm,
          nox_corr_b2s2: noxCorrB2s2Obj ?? null,
        })
        raised.push('nox_corr_b2s2_alert')
      } else if (
        spdOk &&
        noxCorrB2s2Ppm >= warnP &&
        noxCorrB2s2Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_corr_b2s2_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_corr_b2s2_warn', 'warn', `NOx corregido B2S2 alto · ${Math.round(noxCorrB2s2Ppm)} ppm`, {
          nox_corrected_b2s2_ppm: noxCorrB2s2Ppm,
          nox_corr_b2s2: noxCorrB2s2Obj ?? null,
        })
        raised.push('nox_corr_b2s2_warn')
      }
    }

    // NOx concentration S3 (OBD PID 01A7)
    const noxConcS3Obj = signals.nox_conc_s3 as Record<string, unknown> | undefined
    const noxConcS3Ppm =
      typeof noxConcS3Obj?.nox_ppm === 'number'
        ? (noxConcS3Obj.nox_ppm as number)
        : typeof signals.nox_conc_s3_ppm === 'number'
          ? (signals.nox_conc_s3_ppm as number)
          : null
    const noxConcS3Speed =
      typeof noxConcS3Obj?.speed_kmh === 'number'
        ? (noxConcS3Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxConcS3Ppm === 'number') {
      const warnP = typeof signals.nox_conc_s3_warn === 'number' ? (signals.nox_conc_s3_warn as number) : 600
      const alertP = typeof signals.nox_conc_s3_alert === 'number' ? (signals.nox_conc_s3_alert as number) : 800
      const minSpd = typeof signals.nox_conc_s3_speed_min_kmh === 'number' ? (signals.nox_conc_s3_speed_min_kmh as number) : 20
      const spdOk = typeof noxConcS3Speed === 'number' && noxConcS3Speed >= minSpd
      if (spdOk && noxConcS3Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_conc_s3_alert', 120)) {
        insertAlert(deviceId, 'nox_conc_s3_alert', 'critical', `NOx concentración S3 crítica · ${Math.round(noxConcS3Ppm)} ppm`, {
          nox_conc_s3_ppm: noxConcS3Ppm,
          nox_conc_s3: noxConcS3Obj ?? null,
        })
        raised.push('nox_conc_s3_alert')
      } else if (
        spdOk &&
        noxConcS3Ppm >= warnP &&
        noxConcS3Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_conc_s3_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_conc_s3_warn', 'warn', `NOx concentración S3 alta · ${Math.round(noxConcS3Ppm)} ppm`, {
          nox_conc_s3_ppm: noxConcS3Ppm,
          nox_conc_s3: noxConcS3Obj ?? null,
        })
        raised.push('nox_conc_s3_warn')
      }
    }

    // NOx concentration S4 (OBD PID 01A7)
    const noxConcS4Obj = signals.nox_conc_s4 as Record<string, unknown> | undefined
    const noxConcS4Ppm =
      typeof noxConcS4Obj?.nox_ppm === 'number'
        ? (noxConcS4Obj.nox_ppm as number)
        : typeof signals.nox_conc_s4_ppm === 'number'
          ? (signals.nox_conc_s4_ppm as number)
          : null
    const noxConcS4Speed =
      typeof noxConcS4Obj?.speed_kmh === 'number'
        ? (noxConcS4Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxConcS4Ppm === 'number') {
      const warnP = typeof signals.nox_conc_s4_warn === 'number' ? (signals.nox_conc_s4_warn as number) : 600
      const alertP = typeof signals.nox_conc_s4_alert === 'number' ? (signals.nox_conc_s4_alert as number) : 800
      const minSpd = typeof signals.nox_conc_s4_speed_min_kmh === 'number' ? (signals.nox_conc_s4_speed_min_kmh as number) : 20
      const spdOk = typeof noxConcS4Speed === 'number' && noxConcS4Speed >= minSpd
      if (spdOk && noxConcS4Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_conc_s4_alert', 120)) {
        insertAlert(deviceId, 'nox_conc_s4_alert', 'critical', `NOx concentración S4 crítica · ${Math.round(noxConcS4Ppm)} ppm`, {
          nox_conc_s4_ppm: noxConcS4Ppm,
          nox_conc_s4: noxConcS4Obj ?? null,
        })
        raised.push('nox_conc_s4_alert')
      } else if (
        spdOk &&
        noxConcS4Ppm >= warnP &&
        noxConcS4Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_conc_s4_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_conc_s4_warn', 'warn', `NOx concentración S4 alta · ${Math.round(noxConcS4Ppm)} ppm`, {
          nox_conc_s4_ppm: noxConcS4Ppm,
          nox_conc_s4: noxConcS4Obj ?? null,
        })
        raised.push('nox_conc_s4_warn')
      }
    }

    // NOx corrected S3 (OBD PID 01A8)
    const noxCorrS3Obj = signals.nox_corr_s3 as Record<string, unknown> | undefined
    const noxCorrS3Ppm =
      typeof noxCorrS3Obj?.nox_ppm === 'number'
        ? (noxCorrS3Obj.nox_ppm as number)
        : typeof signals.nox_corrected_s3_ppm === 'number'
          ? (signals.nox_corrected_s3_ppm as number)
          : null
    const noxCorrS3Speed =
      typeof noxCorrS3Obj?.speed_kmh === 'number'
        ? (noxCorrS3Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxCorrS3Ppm === 'number') {
      const warnP = typeof signals.nox_corr_s3_warn === 'number' ? (signals.nox_corr_s3_warn as number) : 600
      const alertP = typeof signals.nox_corr_s3_alert === 'number' ? (signals.nox_corr_s3_alert as number) : 800
      const minSpd = typeof signals.nox_corr_s3_speed_min_kmh === 'number' ? (signals.nox_corr_s3_speed_min_kmh as number) : 20
      const spdOk = typeof noxCorrS3Speed === 'number' && noxCorrS3Speed >= minSpd
      if (spdOk && noxCorrS3Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_corr_s3_alert', 120)) {
        insertAlert(deviceId, 'nox_corr_s3_alert', 'critical', `NOx corregido S3 crítico · ${Math.round(noxCorrS3Ppm)} ppm`, {
          nox_corrected_s3_ppm: noxCorrS3Ppm,
          nox_corr_s3: noxCorrS3Obj ?? null,
        })
        raised.push('nox_corr_s3_alert')
      } else if (
        spdOk &&
        noxCorrS3Ppm >= warnP &&
        noxCorrS3Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_corr_s3_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_corr_s3_warn', 'warn', `NOx corregido S3 alto · ${Math.round(noxCorrS3Ppm)} ppm`, {
          nox_corrected_s3_ppm: noxCorrS3Ppm,
          nox_corr_s3: noxCorrS3Obj ?? null,
        })
        raised.push('nox_corr_s3_warn')
      }
    }

    // NOx corrected S4 (OBD PID 01A8)
    const noxCorrS4Obj = signals.nox_corr_s4 as Record<string, unknown> | undefined
    const noxCorrS4Ppm =
      typeof noxCorrS4Obj?.nox_ppm === 'number'
        ? (noxCorrS4Obj.nox_ppm as number)
        : typeof signals.nox_corrected_s4_ppm === 'number'
          ? (signals.nox_corrected_s4_ppm as number)
          : null
    const noxCorrS4Speed =
      typeof noxCorrS4Obj?.speed_kmh === 'number'
        ? (noxCorrS4Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof noxCorrS4Ppm === 'number') {
      const warnP = typeof signals.nox_corr_s4_warn === 'number' ? (signals.nox_corr_s4_warn as number) : 600
      const alertP = typeof signals.nox_corr_s4_alert === 'number' ? (signals.nox_corr_s4_alert as number) : 800
      const minSpd = typeof signals.nox_corr_s4_speed_min_kmh === 'number' ? (signals.nox_corr_s4_speed_min_kmh as number) : 20
      const spdOk = typeof noxCorrS4Speed === 'number' && noxCorrS4Speed >= minSpd
      if (spdOk && noxCorrS4Ppm >= alertP && !recentlyAlerted(deviceId, 'nox_corr_s4_alert', 120)) {
        insertAlert(deviceId, 'nox_corr_s4_alert', 'critical', `NOx corregido S4 crítico · ${Math.round(noxCorrS4Ppm)} ppm`, {
          nox_corrected_s4_ppm: noxCorrS4Ppm,
          nox_corr_s4: noxCorrS4Obj ?? null,
        })
        raised.push('nox_corr_s4_alert')
      } else if (
        spdOk &&
        noxCorrS4Ppm >= warnP &&
        noxCorrS4Ppm < alertP &&
        !recentlyAlerted(deviceId, 'nox_corr_s4_warn', 120)
      ) {
        insertAlert(deviceId, 'nox_corr_s4_warn', 'warn', `NOx corregido S4 alto · ${Math.round(noxCorrS4Ppm)} ppm`, {
          nox_corrected_s4_ppm: noxCorrS4Ppm,
          nox_corr_s4: noxCorrS4Obj ?? null,
        })
        raised.push('nox_corr_s4_warn')
      }
    }

    // Cylinder fuel rate (OBD PID 01A2)
    const cylFuelObj = signals.cyl_fuel as Record<string, unknown> | undefined
    const cylFuelMg =
      typeof cylFuelObj?.mg_per_stroke === 'number'
        ? (cylFuelObj.mg_per_stroke as number)
        : typeof signals.cylinder_fuel_rate_mg === 'number'
          ? (signals.cylinder_fuel_rate_mg as number)
          : null
    const cylFuelSpeed =
      typeof cylFuelObj?.speed_kmh === 'number'
        ? (cylFuelObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof cylFuelMg === 'number') {
      const warnMg = typeof signals.cyl_fuel_warn_mg === 'number' ? (signals.cyl_fuel_warn_mg as number) : 40
      const alertMg = typeof signals.cyl_fuel_alert_mg === 'number' ? (signals.cyl_fuel_alert_mg as number) : 55
      const minSpd = typeof signals.cyl_fuel_speed_min_kmh === 'number' ? (signals.cyl_fuel_speed_min_kmh as number) : 20
      const spdOk = typeof cylFuelSpeed === 'number' && cylFuelSpeed >= minSpd
      if (spdOk && cylFuelMg >= alertMg && !recentlyAlerted(deviceId, 'cyl_fuel_alert', 120)) {
        insertAlert(deviceId, 'cyl_fuel_alert', 'critical', `Tasa cilindro crítica · ${Math.round(cylFuelMg)} mg/ciclo`, {
          cylinder_fuel_rate_mg: cylFuelMg,
          cyl_fuel: cylFuelObj ?? null,
        })
        raised.push('cyl_fuel_alert')
      } else if (
        spdOk &&
        cylFuelMg >= warnMg &&
        cylFuelMg < alertMg &&
        !recentlyAlerted(deviceId, 'cyl_fuel_warn', 120)
      ) {
        insertAlert(deviceId, 'cyl_fuel_warn', 'warn', `Tasa cilindro alta · ${Math.round(cylFuelMg)} mg/ciclo`, {
          cylinder_fuel_rate_mg: cylFuelMg,
          cyl_fuel: cylFuelObj ?? null,
        })
        raised.push('cyl_fuel_warn')
      }
    }

    // Evap system vapor pressure (OBD PID 01A3)
    const evapSysObj = signals.evap_sys_vapor as Record<string, unknown> | undefined
    const evapSysPa =
      typeof evapSysObj?.pressure_pa === 'number'
        ? (evapSysObj.pressure_pa as number)
        : typeof signals.evap_sys_vapor_pa === 'number'
          ? (signals.evap_sys_vapor_pa as number)
          : null
    const evapSysSpeed =
      typeof evapSysObj?.speed_kmh === 'number'
        ? (evapSysObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof evapSysPa === 'number') {
      const warnPa = typeof signals.evap_sys_vapor_warn_pa === 'number' ? (signals.evap_sys_vapor_warn_pa as number) : 5000
      const alertPa = typeof signals.evap_sys_vapor_alert_pa === 'number' ? (signals.evap_sys_vapor_alert_pa as number) : 8000
      const minSpd = typeof signals.evap_sys_vapor_speed_min_kmh === 'number' ? (signals.evap_sys_vapor_speed_min_kmh as number) : 20
      const spdOk = typeof evapSysSpeed === 'number' && evapSysSpeed >= minSpd
      const absPa = Math.abs(evapSysPa)
      if (spdOk && absPa >= alertPa && !recentlyAlerted(deviceId, 'evap_sys_vapor_alert', 120)) {
        insertAlert(deviceId, 'evap_sys_vapor_alert', 'critical', `Vapor evaporativo sistema crítico · ${Math.round(evapSysPa)} Pa`, {
          evap_sys_vapor_pa: evapSysPa,
          evap_sys_vapor: evapSysObj ?? null,
        })
        raised.push('evap_sys_vapor_alert')
      } else if (
        spdOk &&
        absPa >= warnPa &&
        absPa < alertPa &&
        !recentlyAlerted(deviceId, 'evap_sys_vapor_warn', 120)
      ) {
        insertAlert(deviceId, 'evap_sys_vapor_warn', 'warn', `Vapor evaporativo sistema alto · ${Math.round(evapSysPa)} Pa`, {
          evap_sys_vapor_pa: evapSysPa,
          evap_sys_vapor: evapSysObj ?? null,
        })
        raised.push('evap_sys_vapor_warn')
      }
    }

    // Transmission gear ratio (OBD PID 01A4)
    const transGearObj = signals.trans_gear as Record<string, unknown> | undefined
    const transGearRatio =
      typeof transGearObj?.gear_ratio === 'number'
        ? (transGearObj.gear_ratio as number)
        : typeof signals.trans_gear_ratio === 'number'
          ? (signals.trans_gear_ratio as number)
          : null
    const transGearSpeed =
      typeof transGearObj?.speed_kmh === 'number'
        ? (transGearObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof transGearRatio === 'number') {
      const warnRatio = typeof signals.trans_gear_warn_ratio === 'number' ? (signals.trans_gear_warn_ratio as number) : 2.5
      const alertRatio = typeof signals.trans_gear_alert_ratio === 'number' ? (signals.trans_gear_alert_ratio as number) : 3.5
      const minSpd = typeof signals.trans_gear_speed_min_kmh === 'number' ? (signals.trans_gear_speed_min_kmh as number) : 20
      const spdOk = typeof transGearSpeed === 'number' && transGearSpeed >= minSpd
      if (spdOk && transGearRatio >= alertRatio && !recentlyAlerted(deviceId, 'trans_gear_alert', 120)) {
        insertAlert(deviceId, 'trans_gear_alert', 'critical', `Relación transmisión crítica · ${transGearRatio.toFixed(2)}`, {
          trans_gear_ratio: transGearRatio,
          trans_gear: transGearObj ?? null,
        })
        raised.push('trans_gear_alert')
      } else if (
        spdOk &&
        transGearRatio >= warnRatio &&
        transGearRatio < alertRatio &&
        !recentlyAlerted(deviceId, 'trans_gear_warn', 120)
      ) {
        insertAlert(deviceId, 'trans_gear_warn', 'warn', `Relación transmisión alta · ${transGearRatio.toFixed(2)}`, {
          trans_gear_ratio: transGearRatio,
          trans_gear: transGearObj ?? null,
        })
        raised.push('trans_gear_warn')
      }
    }

    // OBD odometer (OBD PID 01A6)
    const obdOdoObj = signals.obd_odometer as Record<string, unknown> | undefined
    const obdOdoKm =
      typeof obdOdoObj?.odometer_km === 'number'
        ? (obdOdoObj.odometer_km as number)
        : typeof signals.obd_odometer_km === 'number'
          ? (signals.obd_odometer_km as number)
          : null
    const obdOdoSpeed =
      typeof obdOdoObj?.speed_kmh === 'number'
        ? (obdOdoObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof obdOdoKm === 'number') {
      const warnKm = typeof signals.obd_odometer_warn_km === 'number' ? (signals.obd_odometer_warn_km as number) : 120000
      const alertKm = typeof signals.obd_odometer_alert_km === 'number' ? (signals.obd_odometer_alert_km as number) : 160000
      const minSpd = typeof signals.obd_odometer_speed_min_kmh === 'number' ? (signals.obd_odometer_speed_min_kmh as number) : 20
      const spdOk = typeof obdOdoSpeed === 'number' && obdOdoSpeed >= minSpd
      if (spdOk && obdOdoKm >= alertKm && !recentlyAlerted(deviceId, 'obd_odometer_alert', 120)) {
        insertAlert(deviceId, 'obd_odometer_alert', 'critical', `Odómetro OBD crítico · ${Math.round(obdOdoKm)} km`, {
          obd_odometer_km: obdOdoKm,
          obd_odometer: obdOdoObj ?? null,
        })
        raised.push('obd_odometer_alert')
      } else if (
        spdOk &&
        obdOdoKm >= warnKm &&
        obdOdoKm < alertKm &&
        !recentlyAlerted(deviceId, 'obd_odometer_warn', 120)
      ) {
        insertAlert(deviceId, 'obd_odometer_warn', 'warn', `Odómetro OBD alto · ${Math.round(obdOdoKm)} km`, {
          obd_odometer_km: obdOdoKm,
          obd_odometer: obdOdoObj ?? null,
        })
        raised.push('obd_odometer_warn')
      }
    }

    // ABS disable switch (OBD PID 01A9)
    const absDisableObj = signals.abs_disable as Record<string, unknown> | undefined
    const absDisabled =
      typeof absDisableObj?.disabled === 'boolean'
        ? absDisableObj.disabled
        : typeof signals.abs_disabled === 'number'
          ? (signals.abs_disabled as number) === 1
          : null
    const absDisableSpeed =
      typeof absDisableObj?.speed_kmh === 'number'
        ? (absDisableObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (absDisabled === true) {
      const minSpd = typeof signals.abs_disable_speed_min_kmh === 'number' ? (signals.abs_disable_speed_min_kmh as number) : 20
      const spdOk = typeof absDisableSpeed === 'number' && absDisableSpeed >= minSpd
      if (spdOk && !recentlyAlerted(deviceId, 'abs_disable_alert', 120)) {
        insertAlert(deviceId, 'abs_disable_alert', 'critical', 'ABS desactivado mientras conduces', {
          abs_disabled: true,
          abs_disable: absDisableObj ?? null,
        })
        raised.push('abs_disable_alert')
      }
    }

    // Fuel pressure A (OBD PID 01C5)
    const fuelPressAObj = signals.fuel_press_a as Record<string, unknown> | undefined
    const fuelPressAKpa =
      typeof fuelPressAObj?.pressure_kpa === 'number'
        ? (fuelPressAObj.pressure_kpa as number)
        : typeof signals.fuel_press_a_kpa === 'number'
          ? (signals.fuel_press_a_kpa as number)
          : null
    const fuelPressASpeed =
      typeof fuelPressAObj?.speed_kmh === 'number'
        ? (fuelPressAObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelPressAKpa === 'number') {
      const warnKpa = typeof signals.fuel_press_a_warn_kpa === 'number' ? (signals.fuel_press_a_warn_kpa as number) : 4000
      const alertKpa = typeof signals.fuel_press_a_alert_kpa === 'number' ? (signals.fuel_press_a_alert_kpa as number) : 4800
      const minSpd = typeof signals.fuel_press_a_speed_min_kmh === 'number' ? (signals.fuel_press_a_speed_min_kmh as number) : 20
      const spdOk = typeof fuelPressASpeed === 'number' && fuelPressASpeed >= minSpd
      if (spdOk && fuelPressAKpa >= alertKpa && !recentlyAlerted(deviceId, 'fuel_press_a_alert', 120)) {
        insertAlert(deviceId, 'fuel_press_a_alert', 'critical', `Presión combustible A crítica · ${Math.round(fuelPressAKpa)} kPa`, {
          fuel_press_a_kpa: fuelPressAKpa,
          fuel_press_a: fuelPressAObj ?? null,
        })
        raised.push('fuel_press_a_alert')
      } else if (
        spdOk &&
        fuelPressAKpa >= warnKpa &&
        fuelPressAKpa < alertKpa &&
        !recentlyAlerted(deviceId, 'fuel_press_a_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_press_a_warn', 'warn', `Presión combustible A alta · ${Math.round(fuelPressAKpa)} kPa`, {
          fuel_press_a_kpa: fuelPressAKpa,
          fuel_press_a: fuelPressAObj ?? null,
        })
        raised.push('fuel_press_a_warn')
      }
    }

    // Fuel pressure B (OBD PID 01C5)
    const fuelPressBObj = signals.fuel_press_b as Record<string, unknown> | undefined
    const fuelPressBKpa =
      typeof fuelPressBObj?.pressure_kpa === 'number'
        ? (fuelPressBObj.pressure_kpa as number)
        : typeof signals.fuel_press_b_kpa === 'number'
          ? (signals.fuel_press_b_kpa as number)
          : null
    const fuelPressBSpeed =
      typeof fuelPressBObj?.speed_kmh === 'number'
        ? (fuelPressBObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelPressBKpa === 'number') {
      const warnKpa = typeof signals.fuel_press_b_warn_kpa === 'number' ? (signals.fuel_press_b_warn_kpa as number) : 4000
      const alertKpa = typeof signals.fuel_press_b_alert_kpa === 'number' ? (signals.fuel_press_b_alert_kpa as number) : 4800
      const minSpd = typeof signals.fuel_press_b_speed_min_kmh === 'number' ? (signals.fuel_press_b_speed_min_kmh as number) : 20
      const spdOk = typeof fuelPressBSpeed === 'number' && fuelPressBSpeed >= minSpd
      if (spdOk && fuelPressBKpa >= alertKpa && !recentlyAlerted(deviceId, 'fuel_press_b_alert', 120)) {
        insertAlert(deviceId, 'fuel_press_b_alert', 'critical', `Presión combustible B crítica · ${Math.round(fuelPressBKpa)} kPa`, {
          fuel_press_b_kpa: fuelPressBKpa,
          fuel_press_b: fuelPressBObj ?? null,
        })
        raised.push('fuel_press_b_alert')
      } else if (
        spdOk &&
        fuelPressBKpa >= warnKpa &&
        fuelPressBKpa < alertKpa &&
        !recentlyAlerted(deviceId, 'fuel_press_b_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_press_b_warn', 'warn', `Presión combustible B alta · ${Math.round(fuelPressBKpa)} kPa`, {
          fuel_press_b_kpa: fuelPressBKpa,
          fuel_press_b: fuelPressBObj ?? null,
        })
        raised.push('fuel_press_b_warn')
      }
    }

    // Distance since reflash (OBD PID 01C7)
    const reflashObj = signals.reflash_dist as Record<string, unknown> | undefined
    const reflashKm =
      typeof reflashObj?.distance_km === 'number'
        ? (reflashObj.distance_km as number)
        : typeof signals.reflash_dist_km === 'number'
          ? (signals.reflash_dist_km as number)
          : null
    const reflashSpeed =
      typeof reflashObj?.speed_kmh === 'number'
        ? (reflashObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof reflashKm === 'number') {
      const warnKm = typeof signals.reflash_dist_warn_km === 'number' ? (signals.reflash_dist_warn_km as number) : 5000
      const alertKm = typeof signals.reflash_dist_alert_km === 'number' ? (signals.reflash_dist_alert_km as number) : 10000
      const minSpd = typeof signals.reflash_dist_speed_min_kmh === 'number' ? (signals.reflash_dist_speed_min_kmh as number) : 20
      const spdOk = typeof reflashSpeed === 'number' && reflashSpeed >= minSpd
      if (spdOk && reflashKm >= alertKm && !recentlyAlerted(deviceId, 'reflash_dist_alert', 120)) {
        insertAlert(deviceId, 'reflash_dist_alert', 'critical', `Distancia reflash crítica · ${Math.round(reflashKm)} km`, {
          reflash_dist_km: reflashKm,
          reflash_dist: reflashObj ?? null,
        })
        raised.push('reflash_dist_alert')
      } else if (
        spdOk &&
        reflashKm >= warnKm &&
        reflashKm < alertKm &&
        !recentlyAlerted(deviceId, 'reflash_dist_warn', 120)
      ) {
        insertAlert(deviceId, 'reflash_dist_warn', 'warn', `Distancia reflash alta · ${Math.round(reflashKm)} km`, {
          reflash_dist_km: reflashKm,
          reflash_dist: reflashObj ?? null,
        })
        raised.push('reflash_dist_warn')
      }
    }

    // Fuel level input A (OBD PID 01C3 byte A) — low level
    const fuelLvlAObj = signals.fuel_level_a as Record<string, unknown> | undefined
    const fuelLvlAPct =
      typeof fuelLvlAObj?.level_pct === 'number'
        ? (fuelLvlAObj.level_pct as number)
        : typeof signals.fuel_level_input_a_pct === 'number'
          ? (signals.fuel_level_input_a_pct as number)
          : null
    const fuelLvlASpeed =
      typeof fuelLvlAObj?.speed_kmh === 'number'
        ? (fuelLvlAObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelLvlAPct === 'number') {
      const warnPct =
        typeof signals.fuel_level_a_warn_pct === 'number' ? (signals.fuel_level_a_warn_pct as number) : 15
      const alertPct =
        typeof signals.fuel_level_a_alert_pct === 'number' ? (signals.fuel_level_a_alert_pct as number) : 8
      const minSpd =
        typeof signals.fuel_level_a_speed_min_kmh === 'number'
          ? (signals.fuel_level_a_speed_min_kmh as number)
          : 20
      const spdOk = typeof fuelLvlASpeed === 'number' && fuelLvlASpeed >= minSpd
      if (spdOk && fuelLvlAPct <= alertPct && !recentlyAlerted(deviceId, 'fuel_level_a_alert', 120)) {
        insertAlert(
          deviceId,
          'fuel_level_a_alert',
          'critical',
          `Nivel combustible A crítico · ${Math.round(fuelLvlAPct)}%`,
          {
            fuel_level_input_a_pct: fuelLvlAPct,
            fuel_level_a: fuelLvlAObj ?? null,
          },
        )
        raised.push('fuel_level_a_alert')
      } else if (
        spdOk &&
        fuelLvlAPct <= warnPct &&
        fuelLvlAPct > alertPct &&
        !recentlyAlerted(deviceId, 'fuel_level_a_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_level_a_warn', 'warn', `Nivel combustible A bajo · ${Math.round(fuelLvlAPct)}%`, {
          fuel_level_input_a_pct: fuelLvlAPct,
          fuel_level_a: fuelLvlAObj ?? null,
        })
        raised.push('fuel_level_a_warn')
      }
    }

    // Fuel level input B (OBD PID 01C3 byte B) — low level
    const fuelLvlBObj = signals.fuel_level_b as Record<string, unknown> | undefined
    const fuelLvlBPct =
      typeof fuelLvlBObj?.level_pct === 'number'
        ? (fuelLvlBObj.level_pct as number)
        : typeof signals.fuel_level_input_b_pct === 'number'
          ? (signals.fuel_level_input_b_pct as number)
          : null
    const fuelLvlBSpeed =
      typeof fuelLvlBObj?.speed_kmh === 'number'
        ? (fuelLvlBObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelLvlBPct === 'number') {
      const warnPct =
        typeof signals.fuel_level_b_warn_pct === 'number' ? (signals.fuel_level_b_warn_pct as number) : 15
      const alertPct =
        typeof signals.fuel_level_b_alert_pct === 'number' ? (signals.fuel_level_b_alert_pct as number) : 8
      const minSpd =
        typeof signals.fuel_level_b_speed_min_kmh === 'number'
          ? (signals.fuel_level_b_speed_min_kmh as number)
          : 20
      const spdOk = typeof fuelLvlBSpeed === 'number' && fuelLvlBSpeed >= minSpd
      if (spdOk && fuelLvlBPct <= alertPct && !recentlyAlerted(deviceId, 'fuel_level_b_alert', 120)) {
        insertAlert(
          deviceId,
          'fuel_level_b_alert',
          'critical',
          `Nivel combustible B crítico · ${Math.round(fuelLvlBPct)}%`,
          {
            fuel_level_input_b_pct: fuelLvlBPct,
            fuel_level_b: fuelLvlBObj ?? null,
          },
        )
        raised.push('fuel_level_b_alert')
      } else if (
        spdOk &&
        fuelLvlBPct <= warnPct &&
        fuelLvlBPct > alertPct &&
        !recentlyAlerted(deviceId, 'fuel_level_b_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_level_b_warn', 'warn', `Nivel combustible B bajo · ${Math.round(fuelLvlBPct)}%`, {
          fuel_level_input_b_pct: fuelLvlBPct,
          fuel_level_b: fuelLvlBObj ?? null,
        })
        raised.push('fuel_level_b_warn')
      }
    }

    // EPCS diagnostic time (OBD PID 01C4 byte A)
    const epcsTimeObj = signals.epcs_time as Record<string, unknown> | undefined
    const epcsTimeSec =
      typeof epcsTimeObj?.time_sec === 'number'
        ? (epcsTimeObj.time_sec as number)
        : typeof signals.epcs_diag_time_sec === 'number'
          ? (signals.epcs_diag_time_sec as number)
          : null
    const epcsTimeSpeed =
      typeof epcsTimeObj?.speed_kmh === 'number'
        ? (epcsTimeObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof epcsTimeSec === 'number') {
      const warnSec = typeof signals.epcs_time_warn_sec === 'number' ? (signals.epcs_time_warn_sec as number) : 120
      const alertSec = typeof signals.epcs_time_alert_sec === 'number' ? (signals.epcs_time_alert_sec as number) : 180
      const minSpd =
        typeof signals.epcs_time_speed_min_kmh === 'number' ? (signals.epcs_time_speed_min_kmh as number) : 20
      const spdOk = typeof epcsTimeSpeed === 'number' && epcsTimeSpeed >= minSpd
      if (spdOk && epcsTimeSec >= alertSec && !recentlyAlerted(deviceId, 'epcs_time_alert', 120)) {
        insertAlert(deviceId, 'epcs_time_alert', 'critical', `Tiempo EPCS crítico · ${Math.round(epcsTimeSec)} s`, {
          epcs_diag_time_sec: epcsTimeSec,
          epcs_time: epcsTimeObj ?? null,
        })
        raised.push('epcs_time_alert')
      } else if (
        spdOk &&
        epcsTimeSec >= warnSec &&
        epcsTimeSec < alertSec &&
        !recentlyAlerted(deviceId, 'epcs_time_warn', 120)
      ) {
        insertAlert(deviceId, 'epcs_time_warn', 'warn', `Tiempo EPCS alto · ${Math.round(epcsTimeSec)} s`, {
          epcs_diag_time_sec: epcsTimeSec,
          epcs_time: epcsTimeObj ?? null,
        })
        raised.push('epcs_time_warn')
      }
    }

    // EPCS diagnostic count (OBD PID 01C4 byte B)
    const epcsCountObj = signals.epcs_count as Record<string, unknown> | undefined
    const epcsCountVal =
      typeof epcsCountObj?.count === 'number'
        ? (epcsCountObj.count as number)
        : typeof signals.epcs_diag_count === 'number'
          ? (signals.epcs_diag_count as number)
          : null
    const epcsCountSpeed =
      typeof epcsCountObj?.speed_kmh === 'number'
        ? (epcsCountObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof epcsCountVal === 'number') {
      const warnCount = typeof signals.epcs_count_warn === 'number' ? (signals.epcs_count_warn as number) : 50
      const alertCount = typeof signals.epcs_count_alert === 'number' ? (signals.epcs_count_alert as number) : 80
      const minSpd =
        typeof signals.epcs_count_speed_min_kmh === 'number' ? (signals.epcs_count_speed_min_kmh as number) : 20
      const spdOk = typeof epcsCountSpeed === 'number' && epcsCountSpeed >= minSpd
      if (spdOk && epcsCountVal >= alertCount && !recentlyAlerted(deviceId, 'epcs_count_alert', 120)) {
        insertAlert(deviceId, 'epcs_count_alert', 'critical', `Conteo EPCS crítico · ${Math.round(epcsCountVal)}`, {
          epcs_diag_count: epcsCountVal,
          epcs_count: epcsCountObj ?? null,
        })
        raised.push('epcs_count_alert')
      } else if (
        spdOk &&
        epcsCountVal >= warnCount &&
        epcsCountVal < alertCount &&
        !recentlyAlerted(deviceId, 'epcs_count_warn', 120)
      ) {
        insertAlert(deviceId, 'epcs_count_warn', 'warn', `Conteo EPCS alto · ${Math.round(epcsCountVal)}`, {
          epcs_diag_count: epcsCountVal,
          epcs_count: epcsCountObj ?? null,
        })
        raised.push('epcs_count_warn')
      }
    }

    // NOx/PCD diagnostic lamp (OBD PID 01C8)
    const noxPcdObj = signals.nox_pcd_lamp as Record<string, unknown> | undefined
    const noxPcdLampOn =
      typeof noxPcdObj?.lamp_on === 'boolean'
        ? (noxPcdObj.lamp_on as boolean)
        : typeof signals.nox_pcd_lamp_on === 'number'
          ? (signals.nox_pcd_lamp_on as number) !== 0
          : false
    const noxPcdSpeed =
      typeof noxPcdObj?.speed_kmh === 'number'
        ? (noxPcdObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (noxPcdLampOn) {
      const minSpd =
        typeof signals.nox_pcd_lamp_speed_min_kmh === 'number'
          ? (signals.nox_pcd_lamp_speed_min_kmh as number)
          : 20
      const spdOk = typeof noxPcdSpeed === 'number' && noxPcdSpeed >= minSpd
      if (spdOk && !recentlyAlerted(deviceId, 'nox_pcd_lamp_alert', 120)) {
        insertAlert(
          deviceId,
          'nox_pcd_lamp_alert',
          'critical',
          'Lámpara diagnóstico NOx o partículas encendida',
          {
            nox_pcd_lamp_on: true,
            nox_pcd_lamp: noxPcdObj ?? null,
          },
        )
        raised.push('nox_pcd_lamp_alert')
      }
    }

    // Particulate inducement warn (OBD PID 01C6 byte A == 1)
    const induceWarnObj = signals.particulate_induce_warn as Record<string, unknown> | undefined
    const induceStatusWarn =
      typeof induceWarnObj?.status === 'number'
        ? (induceWarnObj.status as number)
        : typeof signals.particulate_induce_status === 'number'
          ? (signals.particulate_induce_status as number)
          : null
    const induceWarnSpeed =
      typeof induceWarnObj?.speed_kmh === 'number'
        ? (induceWarnObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof induceStatusWarn === 'number') {
      const warnStatus =
        typeof signals.particulate_induce_warn_status === 'number'
          ? (signals.particulate_induce_warn_status as number)
          : 1
      const minSpd =
        typeof signals.particulate_induce_warn_speed_min_kmh === 'number'
          ? (signals.particulate_induce_warn_speed_min_kmh as number)
          : 20
      const spdOk = typeof induceWarnSpeed === 'number' && induceWarnSpeed >= minSpd
      if (spdOk && induceStatusWarn === warnStatus && !recentlyAlerted(deviceId, 'particulate_induce_warn', 120)) {
        insertAlert(deviceId, 'particulate_induce_warn', 'warn', `Inducement partículas aviso · estado ${induceStatusWarn}`, {
          particulate_induce_status: induceStatusWarn,
          particulate_induce_warn: induceWarnObj ?? null,
        })
        raised.push('particulate_induce_warn')
      }
    }

    // Particulate inducement alert (OBD PID 01C6 byte A >= 2)
    const induceAlertObj = signals.particulate_induce_alert as Record<string, unknown> | undefined
    const induceStatusAlert =
      typeof induceAlertObj?.status === 'number'
        ? (induceAlertObj.status as number)
        : typeof signals.particulate_induce_status === 'number'
          ? (signals.particulate_induce_status as number)
          : null
    const induceAlertSpeed =
      typeof induceAlertObj?.speed_kmh === 'number'
        ? (induceAlertObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof induceStatusAlert === 'number') {
      const alertStatus =
        typeof signals.particulate_induce_alert_status === 'number'
          ? (signals.particulate_induce_alert_status as number)
          : 2
      const minSpd =
        typeof signals.particulate_induce_alert_speed_min_kmh === 'number'
          ? (signals.particulate_induce_alert_speed_min_kmh as number)
          : 20
      const spdOk = typeof induceAlertSpeed === 'number' && induceAlertSpeed >= minSpd
      if (spdOk && induceStatusAlert >= alertStatus && !recentlyAlerted(deviceId, 'particulate_induce_alert', 120)) {
        insertAlert(deviceId, 'particulate_induce_alert', 'critical', `Inducement partículas activo · estado ${induceStatusAlert}`, {
          particulate_induce_status: induceStatusAlert,
          particulate_induce_alert: induceAlertObj ?? null,
        })
        raised.push('particulate_induce_alert')
      }
    }

    // DPF removal/block counter (OBD PID 01C6 bytes B/C)
    const dpfRemovalObj = signals.dpf_removal as Record<string, unknown> | undefined
    const dpfRemovalCount =
      typeof dpfRemovalObj?.count === 'number'
        ? (dpfRemovalObj.count as number)
        : typeof signals.dpf_removal_counter === 'number'
          ? (signals.dpf_removal_counter as number)
          : null
    const dpfRemovalSpeed =
      typeof dpfRemovalObj?.speed_kmh === 'number'
        ? (dpfRemovalObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof dpfRemovalCount === 'number') {
      const warnCount =
        typeof signals.dpf_removal_warn_count === 'number' ? (signals.dpf_removal_warn_count as number) : 100
      const alertCount =
        typeof signals.dpf_removal_alert_count === 'number' ? (signals.dpf_removal_alert_count as number) : 200
      const minSpd =
        typeof signals.dpf_removal_speed_min_kmh === 'number' ? (signals.dpf_removal_speed_min_kmh as number) : 20
      const spdOk = typeof dpfRemovalSpeed === 'number' && dpfRemovalSpeed >= minSpd
      if (spdOk && dpfRemovalCount >= alertCount && !recentlyAlerted(deviceId, 'dpf_removal_alert', 120)) {
        insertAlert(deviceId, 'dpf_removal_alert', 'critical', `Contador remoción DPF crítico · ${Math.round(dpfRemovalCount)}`, {
          dpf_removal_counter: dpfRemovalCount,
          dpf_removal: dpfRemovalObj ?? null,
        })
        raised.push('dpf_removal_alert')
      } else if (
        spdOk &&
        dpfRemovalCount >= warnCount &&
        dpfRemovalCount < alertCount &&
        !recentlyAlerted(deviceId, 'dpf_removal_warn', 120)
      ) {
        insertAlert(deviceId, 'dpf_removal_warn', 'warn', `Contador remoción DPF alto · ${Math.round(dpfRemovalCount)}`, {
          dpf_removal_counter: dpfRemovalCount,
          dpf_removal: dpfRemovalObj ?? null,
        })
        raised.push('dpf_removal_warn')
      }
    }

    // Reagent injection failure counter (OBD PID 01C6 bytes D/E)
    const reagentFailObj = signals.reagent_fail as Record<string, unknown> | undefined
    const reagentFailCount =
      typeof reagentFailObj?.count === 'number'
        ? (reagentFailObj.count as number)
        : typeof signals.reagent_injection_fail_counter === 'number'
          ? (signals.reagent_injection_fail_counter as number)
          : null
    const reagentFailSpeed =
      typeof reagentFailObj?.speed_kmh === 'number'
        ? (reagentFailObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof reagentFailCount === 'number') {
      const warnCount =
        typeof signals.reagent_fail_warn_count === 'number' ? (signals.reagent_fail_warn_count as number) : 50
      const alertCount =
        typeof signals.reagent_fail_alert_count === 'number' ? (signals.reagent_fail_alert_count as number) : 80
      const minSpd =
        typeof signals.reagent_fail_speed_min_kmh === 'number' ? (signals.reagent_fail_speed_min_kmh as number) : 20
      const spdOk = typeof reagentFailSpeed === 'number' && reagentFailSpeed >= minSpd
      if (spdOk && reagentFailCount >= alertCount && !recentlyAlerted(deviceId, 'reagent_fail_alert', 120)) {
        insertAlert(deviceId, 'reagent_fail_alert', 'critical', `Fallos inyección reactivo críticos · ${Math.round(reagentFailCount)}`, {
          reagent_injection_fail_counter: reagentFailCount,
          reagent_fail: reagentFailObj ?? null,
        })
        raised.push('reagent_fail_alert')
      } else if (
        spdOk &&
        reagentFailCount >= warnCount &&
        reagentFailCount < alertCount &&
        !recentlyAlerted(deviceId, 'reagent_fail_warn', 120)
      ) {
        insertAlert(deviceId, 'reagent_fail_warn', 'warn', `Fallos inyección reactivo altos · ${Math.round(reagentFailCount)}`, {
          reagent_injection_fail_counter: reagentFailCount,
          reagent_fail: reagentFailObj ?? null,
        })
        raised.push('reagent_fail_warn')
      }
    }

    // Particulate monitor malfunction counter (OBD PID 01C6 bytes F/G)
    const particulateMalfObj = signals.particulate_malf as Record<string, unknown> | undefined
    const particulateMalfCount =
      typeof particulateMalfObj?.count === 'number'
        ? (particulateMalfObj.count as number)
        : typeof signals.particulate_monitor_malfunction_counter === 'number'
          ? (signals.particulate_monitor_malfunction_counter as number)
          : null
    const particulateMalfSpeed =
      typeof particulateMalfObj?.speed_kmh === 'number'
        ? (particulateMalfObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof particulateMalfCount === 'number') {
      const warnCount =
        typeof signals.particulate_malf_warn_count === 'number' ? (signals.particulate_malf_warn_count as number) : 50
      const alertCount =
        typeof signals.particulate_malf_alert_count === 'number' ? (signals.particulate_malf_alert_count as number) : 80
      const minSpd =
        typeof signals.particulate_malf_speed_min_kmh === 'number' ? (signals.particulate_malf_speed_min_kmh as number) : 20
      const spdOk = typeof particulateMalfSpeed === 'number' && particulateMalfSpeed >= minSpd
      if (spdOk && particulateMalfCount >= alertCount && !recentlyAlerted(deviceId, 'particulate_malf_alert', 120)) {
        insertAlert(deviceId, 'particulate_malf_alert', 'critical', `Malfunction partículas crítico · ${Math.round(particulateMalfCount)}`, {
          particulate_monitor_malfunction_counter: particulateMalfCount,
          particulate_malf: particulateMalfObj ?? null,
        })
        raised.push('particulate_malf_alert')
      } else if (
        spdOk &&
        particulateMalfCount >= warnCount &&
        particulateMalfCount < alertCount &&
        !recentlyAlerted(deviceId, 'particulate_malf_warn', 120)
      ) {
        insertAlert(deviceId, 'particulate_malf_warn', 'warn', `Malfunction partículas alto · ${Math.round(particulateMalfCount)}`, {
          particulate_monitor_malfunction_counter: particulateMalfCount,
          particulate_malf: particulateMalfObj ?? null,
        })
        raised.push('particulate_malf_warn')
      }
    }

    // Engine fuel rate g/s (OBD PID 019D)
    const fuelGpsObj = signals.engine_fuel_rate_gps as Record<string, unknown> | undefined
    const fuelGpsRate =
      typeof fuelGpsObj?.rate_gps === 'number'
        ? (fuelGpsObj.rate_gps as number)
        : typeof signals.engine_fuel_rate_gps_rate === 'number'
          ? (signals.engine_fuel_rate_gps_rate as number)
          : null
    const fuelGpsSpeed =
      typeof fuelGpsObj?.speed_kmh === 'number'
        ? (fuelGpsObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelGpsRate === 'number') {
      const warnGps =
        typeof signals.engine_fuel_rate_gps_warn === 'number' ? (signals.engine_fuel_rate_gps_warn as number) : 3
      const alertGps =
        typeof signals.engine_fuel_rate_gps_alert === 'number' ? (signals.engine_fuel_rate_gps_alert as number) : 5
      const minSpd =
        typeof signals.engine_fuel_rate_gps_speed_min_kmh === 'number'
          ? (signals.engine_fuel_rate_gps_speed_min_kmh as number)
          : 20
      const spdOk = typeof fuelGpsSpeed === 'number' && fuelGpsSpeed >= minSpd
      if (spdOk && fuelGpsRate >= alertGps && !recentlyAlerted(deviceId, 'engine_fuel_rate_gps_alert', 120)) {
        insertAlert(deviceId, 'engine_fuel_rate_gps_alert', 'critical', `Tasa combustible motor crítica · ${fuelGpsRate.toFixed(1)} g/s`, {
          engine_fuel_rate_gps_rate: fuelGpsRate,
          engine_fuel_rate_gps: fuelGpsObj ?? null,
        })
        raised.push('engine_fuel_rate_gps_alert')
      } else if (
        spdOk &&
        fuelGpsRate >= warnGps &&
        fuelGpsRate < alertGps &&
        !recentlyAlerted(deviceId, 'engine_fuel_rate_gps_warn', 120)
      ) {
        insertAlert(deviceId, 'engine_fuel_rate_gps_warn', 'warn', `Tasa combustible motor alta · ${fuelGpsRate.toFixed(1)} g/s`, {
          engine_fuel_rate_gps_rate: fuelGpsRate,
          engine_fuel_rate_gps: fuelGpsObj ?? null,
        })
        raised.push('engine_fuel_rate_gps_warn')
      }
    }

    // Engine exhaust flow kg/h (OBD PID 019E)
    const exhaustFlowObj = signals.exhaust_flow as Record<string, unknown> | undefined
    const exhaustFlowKgh =
      typeof exhaustFlowObj?.flow_kgh === 'number'
        ? (exhaustFlowObj.flow_kgh as number)
        : typeof signals.engine_exhaust_flow_kgh === 'number'
          ? (signals.engine_exhaust_flow_kgh as number)
          : null
    const exhaustFlowSpeed =
      typeof exhaustFlowObj?.speed_kmh === 'number'
        ? (exhaustFlowObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof exhaustFlowKgh === 'number') {
      const warnKgh = typeof signals.exhaust_flow_warn_kgh === 'number' ? (signals.exhaust_flow_warn_kgh as number) : 35
      const alertKgh = typeof signals.exhaust_flow_alert_kgh === 'number' ? (signals.exhaust_flow_alert_kgh as number) : 50
      const minSpd =
        typeof signals.exhaust_flow_speed_min_kmh === 'number' ? (signals.exhaust_flow_speed_min_kmh as number) : 20
      const spdOk = typeof exhaustFlowSpeed === 'number' && exhaustFlowSpeed >= minSpd
      if (spdOk && exhaustFlowKgh >= alertKgh && !recentlyAlerted(deviceId, 'exhaust_flow_alert', 120)) {
        insertAlert(deviceId, 'exhaust_flow_alert', 'critical', `Flujo exhaustivo crítico · ${Math.round(exhaustFlowKgh)} kg/h`, {
          engine_exhaust_flow_kgh: exhaustFlowKgh,
          exhaust_flow: exhaustFlowObj ?? null,
        })
        raised.push('exhaust_flow_alert')
      } else if (
        spdOk &&
        exhaustFlowKgh >= warnKgh &&
        exhaustFlowKgh < alertKgh &&
        !recentlyAlerted(deviceId, 'exhaust_flow_warn', 120)
      ) {
        insertAlert(deviceId, 'exhaust_flow_warn', 'warn', `Flujo exhaustivo alto · ${Math.round(exhaustFlowKgh)} kg/h`, {
          engine_exhaust_flow_kgh: exhaustFlowKgh,
          exhaust_flow: exhaustFlowObj ?? null,
        })
        raised.push('exhaust_flow_warn')
      }
    }

    // Fuel system use % 1 (OBD PID 019F byte B)
    const fuelSysUse1Obj = signals.fuel_sys_use1 as Record<string, unknown> | undefined
    const fuelSysUse1Pct =
      typeof fuelSysUse1Obj?.use_pct === 'number'
        ? (fuelSysUse1Obj.use_pct as number)
        : typeof signals.fuel_sys_use_pct1 === 'number'
          ? (signals.fuel_sys_use_pct1 as number)
          : null
    const fuelSysUse1Speed =
      typeof fuelSysUse1Obj?.speed_kmh === 'number'
        ? (fuelSysUse1Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelSysUse1Pct === 'number') {
      const warnPct = typeof signals.fuel_sys_use1_warn_pct === 'number' ? (signals.fuel_sys_use1_warn_pct as number) : 70
      const alertPct = typeof signals.fuel_sys_use1_alert_pct === 'number' ? (signals.fuel_sys_use1_alert_pct as number) : 85
      const minSpd =
        typeof signals.fuel_sys_use1_speed_min_kmh === 'number' ? (signals.fuel_sys_use1_speed_min_kmh as number) : 20
      const spdOk = typeof fuelSysUse1Speed === 'number' && fuelSysUse1Speed >= minSpd
      if (spdOk && fuelSysUse1Pct >= alertPct && !recentlyAlerted(deviceId, 'fuel_sys_use1_alert', 120)) {
        insertAlert(deviceId, 'fuel_sys_use1_alert', 'critical', `Uso combustible sistema 1 crítico · ${Math.round(fuelSysUse1Pct)}%`, {
          fuel_sys_use_pct1: fuelSysUse1Pct,
          fuel_sys_use1: fuelSysUse1Obj ?? null,
        })
        raised.push('fuel_sys_use1_alert')
      } else if (
        spdOk &&
        fuelSysUse1Pct >= warnPct &&
        fuelSysUse1Pct < alertPct &&
        !recentlyAlerted(deviceId, 'fuel_sys_use1_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_sys_use1_warn', 'warn', `Uso combustible sistema 1 alto · ${Math.round(fuelSysUse1Pct)}%`, {
          fuel_sys_use_pct1: fuelSysUse1Pct,
          fuel_sys_use1: fuelSysUse1Obj ?? null,
        })
        raised.push('fuel_sys_use1_warn')
      }
    }

    // Fuel system use % 2 (OBD PID 019F byte C)
    const fuelSysUse2Obj = signals.fuel_sys_use2 as Record<string, unknown> | undefined
    const fuelSysUse2Pct =
      typeof fuelSysUse2Obj?.use_pct === 'number'
        ? (fuelSysUse2Obj.use_pct as number)
        : typeof signals.fuel_sys_use_pct2 === 'number'
          ? (signals.fuel_sys_use_pct2 as number)
          : null
    const fuelSysUse2Speed =
      typeof fuelSysUse2Obj?.speed_kmh === 'number'
        ? (fuelSysUse2Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelSysUse2Pct === 'number') {
      const warnPct = typeof signals.fuel_sys_use2_warn_pct === 'number' ? (signals.fuel_sys_use2_warn_pct as number) : 70
      const alertPct = typeof signals.fuel_sys_use2_alert_pct === 'number' ? (signals.fuel_sys_use2_alert_pct as number) : 85
      const minSpd =
        typeof signals.fuel_sys_use2_speed_min_kmh === 'number' ? (signals.fuel_sys_use2_speed_min_kmh as number) : 20
      const spdOk = typeof fuelSysUse2Speed === 'number' && fuelSysUse2Speed >= minSpd
      if (spdOk && fuelSysUse2Pct >= alertPct && !recentlyAlerted(deviceId, 'fuel_sys_use2_alert', 120)) {
        insertAlert(deviceId, 'fuel_sys_use2_alert', 'critical', `Uso combustible sistema 2 crítico · ${Math.round(fuelSysUse2Pct)}%`, {
          fuel_sys_use_pct2: fuelSysUse2Pct,
          fuel_sys_use2: fuelSysUse2Obj ?? null,
        })
        raised.push('fuel_sys_use2_alert')
      } else if (
        spdOk &&
        fuelSysUse2Pct >= warnPct &&
        fuelSysUse2Pct < alertPct &&
        !recentlyAlerted(deviceId, 'fuel_sys_use2_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_sys_use2_warn', 'warn', `Uso combustible sistema 2 alto · ${Math.round(fuelSysUse2Pct)}%`, {
          fuel_sys_use_pct2: fuelSysUse2Pct,
          fuel_sys_use2: fuelSysUse2Obj ?? null,
        })
        raised.push('fuel_sys_use2_warn')
      }
    }

    // Fuel system use % 3 (OBD PID 019F byte D)
    const fuelSysUse3Obj = signals.fuel_sys_use3 as Record<string, unknown> | undefined
    const fuelSysUse3Pct =
      typeof fuelSysUse3Obj?.use_pct === 'number'
        ? (fuelSysUse3Obj.use_pct as number)
        : typeof signals.fuel_sys_use_pct3 === 'number'
          ? (signals.fuel_sys_use_pct3 as number)
          : null
    const fuelSysUse3Speed =
      typeof fuelSysUse3Obj?.speed_kmh === 'number'
        ? (fuelSysUse3Obj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelSysUse3Pct === 'number') {
      const warnPct = typeof signals.fuel_sys_use3_warn_pct === 'number' ? (signals.fuel_sys_use3_warn_pct as number) : 70
      const alertPct = typeof signals.fuel_sys_use3_alert_pct === 'number' ? (signals.fuel_sys_use3_alert_pct as number) : 85
      const minSpd =
        typeof signals.fuel_sys_use3_speed_min_kmh === 'number' ? (signals.fuel_sys_use3_speed_min_kmh as number) : 20
      const spdOk = typeof fuelSysUse3Speed === 'number' && fuelSysUse3Speed >= minSpd
      if (spdOk && fuelSysUse3Pct >= alertPct && !recentlyAlerted(deviceId, 'fuel_sys_use3_alert', 120)) {
        insertAlert(deviceId, 'fuel_sys_use3_alert', 'critical', `Uso combustible sistema 3 crítico · ${Math.round(fuelSysUse3Pct)}%`, {
          fuel_sys_use_pct3: fuelSysUse3Pct,
          fuel_sys_use3: fuelSysUse3Obj ?? null,
        })
        raised.push('fuel_sys_use3_alert')
      } else if (
        spdOk &&
        fuelSysUse3Pct >= warnPct &&
        fuelSysUse3Pct < alertPct &&
        !recentlyAlerted(deviceId, 'fuel_sys_use3_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_sys_use3_warn', 'warn', `Uso combustible sistema 3 alto · ${Math.round(fuelSysUse3Pct)}%`, {
          fuel_sys_use_pct3: fuelSysUse3Pct,
          fuel_sys_use3: fuelSysUse3Obj ?? null,
        })
        raised.push('fuel_sys_use3_warn')
      }
    }

    // WWH-OBD continuous MI hours (OBD PID 0190)
    const wwhContMiObj = signals.wwh_continuous_mi as Record<string, unknown> | undefined
    const wwhContMiH =
      typeof wwhContMiObj?.mi_hours === 'number'
        ? (wwhContMiObj.mi_hours as number)
        : typeof signals.wwh_obd_continuous_mi_hours === 'number'
          ? (signals.wwh_obd_continuous_mi_hours as number)
          : null
    if (typeof wwhContMiH === 'number') {
      const warnH = typeof signals.wwh_cont_mi_warn_h === 'number' ? (signals.wwh_cont_mi_warn_h as number) : 24
      const alertH = typeof signals.wwh_cont_mi_alert_h === 'number' ? (signals.wwh_cont_mi_alert_h as number) : 48
      if (wwhContMiH >= alertH && !recentlyAlerted(deviceId, 'wwh_continuous_mi_alert', 120)) {
        insertAlert(deviceId, 'wwh_continuous_mi_alert', 'critical', `MI continuo WWH crítico · ${Math.round(wwhContMiH)}h`, {
          wwh_obd_continuous_mi_hours: wwhContMiH,
          wwh_continuous_mi: wwhContMiObj ?? null,
        })
        raised.push('wwh_continuous_mi_alert')
      } else if (
        wwhContMiH >= warnH &&
        wwhContMiH < alertH &&
        !recentlyAlerted(deviceId, 'wwh_continuous_mi_warn', 120)
      ) {
        insertAlert(deviceId, 'wwh_continuous_mi_warn', 'warn', `MI continuo WWH alto · ${Math.round(wwhContMiH)}h`, {
          wwh_obd_continuous_mi_hours: wwhContMiH,
          wwh_continuous_mi: wwhContMiObj ?? null,
        })
        raised.push('wwh_continuous_mi_warn')
      }
    }

    // WWH-OBD ECU B1 hours (OBD PID 0191)
    const wwhEcuB1Obj = signals.wwh_ecu_b1 as Record<string, unknown> | undefined
    const wwhEcuB1H =
      typeof wwhEcuB1Obj?.b1_hours === 'number'
        ? (wwhEcuB1Obj.b1_hours as number)
        : typeof signals.wwh_obd_ecu_b1_hours === 'number'
          ? (signals.wwh_obd_ecu_b1_hours as number)
          : null
    if (typeof wwhEcuB1H === 'number') {
      const warnH = typeof signals.wwh_ecu_b1_warn_h === 'number' ? (signals.wwh_ecu_b1_warn_h as number) : 100
      const alertH = typeof signals.wwh_ecu_b1_alert_h === 'number' ? (signals.wwh_ecu_b1_alert_h as number) : 200
      if (wwhEcuB1H >= alertH && !recentlyAlerted(deviceId, 'wwh_ecu_b1_alert', 120)) {
        insertAlert(deviceId, 'wwh_ecu_b1_alert', 'critical', `ECU B1 WWH crítico · ${Math.round(wwhEcuB1H)}h`, {
          wwh_obd_ecu_b1_hours: wwhEcuB1H,
          wwh_ecu_b1: wwhEcuB1Obj ?? null,
        })
        raised.push('wwh_ecu_b1_alert')
      } else if (
        wwhEcuB1H >= warnH &&
        wwhEcuB1H < alertH &&
        !recentlyAlerted(deviceId, 'wwh_ecu_b1_warn', 120)
      ) {
        insertAlert(deviceId, 'wwh_ecu_b1_warn', 'warn', `ECU B1 WWH alto · ${Math.round(wwhEcuB1H)}h`, {
          wwh_obd_ecu_b1_hours: wwhEcuB1H,
          wwh_ecu_b1: wwhEcuB1Obj ?? null,
        })
        raised.push('wwh_ecu_b1_warn')
      }
    }

    // Fuel system closed-loop count (OBD PID 0192)
    const fuelSysCtlObj = signals.fuel_sys_ctl as Record<string, unknown> | undefined
    const fuelSysCtlCount =
      typeof fuelSysCtlObj?.closed_count === 'number'
        ? (fuelSysCtlObj.closed_count as number)
        : typeof signals.fuel_sys_ctl_closed_count === 'number'
          ? (signals.fuel_sys_ctl_closed_count as number)
          : null
    const fuelSysCtlSpeed =
      typeof fuelSysCtlObj?.speed_kmh === 'number'
        ? (fuelSysCtlObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof fuelSysCtlCount === 'number') {
      const warnMin = typeof signals.fuel_sys_ctl_warn_min === 'number' ? (signals.fuel_sys_ctl_warn_min as number) : 3
      const alertMin = typeof signals.fuel_sys_ctl_alert_min === 'number' ? (signals.fuel_sys_ctl_alert_min as number) : 2
      const minSpd =
        typeof signals.fuel_sys_ctl_speed_min_kmh === 'number' ? (signals.fuel_sys_ctl_speed_min_kmh as number) : 20
      const spdOk = typeof fuelSysCtlSpeed === 'number' && fuelSysCtlSpeed >= minSpd
      if (spdOk && fuelSysCtlCount < alertMin && !recentlyAlerted(deviceId, 'fuel_sys_ctl_alert', 120)) {
        insertAlert(deviceId, 'fuel_sys_ctl_alert', 'critical', `Control combustible lazo abierto · ${Math.round(fuelSysCtlCount)}`, {
          fuel_sys_ctl_closed_count: fuelSysCtlCount,
          fuel_sys_ctl: fuelSysCtlObj ?? null,
        })
        raised.push('fuel_sys_ctl_alert')
      } else if (
        spdOk &&
        fuelSysCtlCount < warnMin &&
        fuelSysCtlCount >= alertMin &&
        !recentlyAlerted(deviceId, 'fuel_sys_ctl_warn', 120)
      ) {
        insertAlert(deviceId, 'fuel_sys_ctl_warn', 'warn', `Pocos controles combustible cerrados · ${Math.round(fuelSysCtlCount)}`, {
          fuel_sys_ctl_closed_count: fuelSysCtlCount,
          fuel_sys_ctl: fuelSysCtlObj ?? null,
        })
        raised.push('fuel_sys_ctl_warn')
      }
    }

    // WWH-OBD cumulative MI hours (OBD PID 0193)
    const wwhCumMiObj = signals.wwh_cumulative_mi as Record<string, unknown> | undefined
    const wwhCumMiH =
      typeof wwhCumMiObj?.mi_hours === 'number'
        ? (wwhCumMiObj.mi_hours as number)
        : typeof signals.wwh_obd_cumulative_mi_hours === 'number'
          ? (signals.wwh_obd_cumulative_mi_hours as number)
          : null
    if (typeof wwhCumMiH === 'number') {
      const warnH = typeof signals.wwh_cum_mi_warn_h === 'number' ? (signals.wwh_cum_mi_warn_h as number) : 100
      const alertH = typeof signals.wwh_cum_mi_alert_h === 'number' ? (signals.wwh_cum_mi_alert_h as number) : 200
      if (wwhCumMiH >= alertH && !recentlyAlerted(deviceId, 'wwh_cumulative_mi_alert', 120)) {
        insertAlert(deviceId, 'wwh_cumulative_mi_alert', 'critical', `MI acumulado WWH crítico · ${Math.round(wwhCumMiH)}h`, {
          wwh_obd_cumulative_mi_hours: wwhCumMiH,
          wwh_cumulative_mi: wwhCumMiObj ?? null,
        })
        raised.push('wwh_cumulative_mi_alert')
      } else if (
        wwhCumMiH >= warnH &&
        wwhCumMiH < alertH &&
        !recentlyAlerted(deviceId, 'wwh_cumulative_mi_warn', 120)
      ) {
        insertAlert(deviceId, 'wwh_cumulative_mi_warn', 'warn', `MI acumulado WWH alto · ${Math.round(wwhCumMiH)}h`, {
          wwh_obd_cumulative_mi_hours: wwhCumMiH,
          wwh_cumulative_mi: wwhCumMiObj ?? null,
        })
        raised.push('wwh_cumulative_mi_warn')
      }
    }

    // Hybrid/EV pack voltage (OBD PID 019A)
    const hevBattObj = signals.hybrid_ev_batt as Record<string, unknown> | undefined
    const hevBattV =
      typeof hevBattObj?.volts === 'number'
        ? (hevBattObj.volts as number)
        : typeof signals.hybrid_ev_batt_voltage_v === 'number'
          ? (signals.hybrid_ev_batt_voltage_v as number)
          : null
    if (typeof hevBattV === 'number') {
      const warnV = typeof signals.hev_volt_warn_v === 'number' ? (signals.hev_volt_warn_v as number) : 280
      const alertV = typeof signals.hev_volt_alert_v === 'number' ? (signals.hev_volt_alert_v as number) : 260
      if (hevBattV < alertV && !recentlyAlerted(deviceId, 'hybrid_ev_batt_alert', 120)) {
        insertAlert(deviceId, 'hybrid_ev_batt_alert', 'critical', `Voltaje batería híbrida crítico · ${Math.round(hevBattV)}V`, {
          hybrid_ev_batt_voltage_v: hevBattV,
          hybrid_ev_batt: hevBattObj ?? null,
        })
        raised.push('hybrid_ev_batt_alert')
      } else if (
        hevBattV < warnV &&
        hevBattV >= alertV &&
        !recentlyAlerted(deviceId, 'hybrid_ev_batt_warn', 120)
      ) {
        insertAlert(deviceId, 'hybrid_ev_batt_warn', 'warn', `Voltaje batería híbrida bajo · ${Math.round(hevBattV)}V`, {
          hybrid_ev_batt_voltage_v: hevBattV,
          hybrid_ev_batt: hevBattObj ?? null,
        })
        raised.push('hybrid_ev_batt_warn')
      }
    }

    // RPM over-rev
    const rpm =
      typeof signals.rpm === 'number' ? (signals.rpm as number) : null
    if (typeof rpm === 'number' && rpm > 0) {
      const warnRpm =
        typeof signals.rpm_warn === 'number' ? (signals.rpm_warn as number) : 4500
      const alertRpm =
        typeof signals.rpm_alert === 'number' ? (signals.rpm_alert as number) : 5500
      if (rpm >= alertRpm && !recentlyAlerted(deviceId, 'rpm_alert', 120)) {
        insertAlert(
          deviceId,
          'rpm_alert',
          'critical',
          `RPM críticas · ${Math.round(rpm)}`,
          { rpm, alert_rpm: alertRpm },
        )
        raised.push('rpm_alert')
      } else if (
        rpm >= warnRpm &&
        rpm < alertRpm &&
        !recentlyAlerted(deviceId, 'rpm_warn', 120)
      ) {
        insertAlert(deviceId, 'rpm_warn', 'warn', `RPM altas · ${Math.round(rpm)}`, {
          rpm,
          warn_rpm: warnRpm,
        })
        raised.push('rpm_warn')
      }
    }

    // Calculated engine load (OBD PID 0104)
    const engLoad = signals.engine_load as Record<string, unknown> | undefined
    let loadPct: number | null =
      typeof engLoad?.load_pct === 'number'
        ? (engLoad.load_pct as number)
        : typeof signals.engine_load_pct === 'number'
          ? (signals.engine_load_pct as number)
          : null
    const loadSpeedKmh =
      typeof engLoad?.speed_kmh === 'number'
        ? (engLoad.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof loadPct === 'number') {
      const warnLoadPct =
        typeof signals.engine_load_warn_pct === 'number'
          ? (signals.engine_load_warn_pct as number)
          : 80
      const alertLoadPct =
        typeof signals.engine_load_alert_pct === 'number'
          ? (signals.engine_load_alert_pct as number)
          : 92
      const loadMinSpd =
        typeof signals.engine_load_speed_min_kmh === 'number'
          ? (signals.engine_load_speed_min_kmh as number)
          : 20
      const loadSpdOk = typeof loadSpeedKmh === 'number' && loadSpeedKmh >= loadMinSpd
      if (
        loadSpdOk &&
        loadPct >= alertLoadPct &&
        !recentlyAlerted(deviceId, 'load_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'load_alert',
          'critical',
          `Carga motor crítica · ${Math.round(loadPct)}%`,
          {
            engine_load_pct: loadPct,
            alert_pct: alertLoadPct,
            engine_load: engLoad ?? null,
          },
        )
        raised.push('load_alert')
      } else if (
        loadSpdOk &&
        loadPct >= warnLoadPct &&
        loadPct < alertLoadPct &&
        !recentlyAlerted(deviceId, 'load_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'load_warn',
          'warn',
          `Carga motor alta · ${Math.round(loadPct)}%`,
          {
            engine_load_pct: loadPct,
            warn_pct: warnLoadPct,
            engine_load: engLoad ?? null,
          },
        )
        raised.push('load_warn')
      }
    }

    // Short-term fuel trim (OBD PID 0106)
    const stftObj = signals.fuel_trim_stft as Record<string, unknown> | undefined
    let stftPct: number | null =
      typeof stftObj?.trim_pct === 'number'
        ? (stftObj.trim_pct as number)
        : typeof signals.fuel_trim_stft_pct === 'number'
          ? (signals.fuel_trim_stft_pct as number)
          : null
    const stftSpeedKmh =
      typeof stftObj?.speed_kmh === 'number'
        ? (stftObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof stftPct === 'number') {
      const warnStftPct =
        typeof signals.stft_warn_pct === 'number'
          ? (signals.stft_warn_pct as number)
          : 12
      const alertStftPct =
        typeof signals.stft_alert_pct === 'number'
          ? (signals.stft_alert_pct as number)
          : 20
      const stftMinSpd =
        typeof signals.stft_speed_min_kmh === 'number'
          ? (signals.stft_speed_min_kmh as number)
          : 20
      const stftSpdOk = typeof stftSpeedKmh === 'number' && stftSpeedKmh >= stftMinSpd
      const absStft = Math.abs(stftPct)
      if (
        stftSpdOk &&
        absStft >= alertStftPct &&
        !recentlyAlerted(deviceId, 'stft_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'stft_alert',
          'critical',
          `STFT crítico · ${stftPct > 0 ? '+' : ''}${Math.round(stftPct)}%`,
          {
            fuel_trim_stft_pct: stftPct,
            alert_pct: alertStftPct,
            fuel_trim_stft: stftObj ?? null,
          },
        )
        raised.push('stft_alert')
      } else if (
        stftSpdOk &&
        absStft >= warnStftPct &&
        absStft < alertStftPct &&
        !recentlyAlerted(deviceId, 'stft_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'stft_warn',
          'warn',
          `STFT fuera de rango · ${stftPct > 0 ? '+' : ''}${Math.round(stftPct)}%`,
          {
            fuel_trim_stft_pct: stftPct,
            warn_pct: warnStftPct,
            fuel_trim_stft: stftObj ?? null,
          },
        )
        raised.push('stft_warn')
      }
    }

    // Long-term fuel trim (OBD PID 0107)
    const ltftObj = signals.fuel_trim_ltft as Record<string, unknown> | undefined
    let ltftPct: number | null =
      typeof ltftObj?.trim_pct === 'number'
        ? (ltftObj.trim_pct as number)
        : typeof signals.fuel_trim_ltft_pct === 'number'
          ? (signals.fuel_trim_ltft_pct as number)
          : null
    const ltftSpeedKmh =
      typeof ltftObj?.speed_kmh === 'number'
        ? (ltftObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof ltftPct === 'number') {
      const warnLtftPct =
        typeof signals.ltft_warn_pct === 'number'
          ? (signals.ltft_warn_pct as number)
          : 12
      const alertLtftPct =
        typeof signals.ltft_alert_pct === 'number'
          ? (signals.ltft_alert_pct as number)
          : 20
      const ltftMinSpd =
        typeof signals.ltft_speed_min_kmh === 'number'
          ? (signals.ltft_speed_min_kmh as number)
          : 20
      const ltftSpdOk = typeof ltftSpeedKmh === 'number' && ltftSpeedKmh >= ltftMinSpd
      const absLtft = Math.abs(ltftPct)
      if (
        ltftSpdOk &&
        absLtft >= alertLtftPct &&
        !recentlyAlerted(deviceId, 'ltft_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'ltft_alert',
          'critical',
          `LTFT crítico · ${ltftPct > 0 ? '+' : ''}${Math.round(ltftPct)}%`,
          {
            fuel_trim_ltft_pct: ltftPct,
            alert_pct: alertLtftPct,
            fuel_trim_ltft: ltftObj ?? null,
          },
        )
        raised.push('ltft_alert')
      } else if (
        ltftSpdOk &&
        absLtft >= warnLtftPct &&
        absLtft < alertLtftPct &&
        !recentlyAlerted(deviceId, 'ltft_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'ltft_warn',
          'warn',
          `LTFT fuera de rango · ${ltftPct > 0 ? '+' : ''}${Math.round(ltftPct)}%`,
          {
            fuel_trim_ltft_pct: ltftPct,
            warn_pct: warnLtftPct,
            fuel_trim_ltft: ltftObj ?? null,
          },
        )
        raised.push('ltft_warn')
      }
    }

    // Intake MAP (OBD PID 010B)
    const mapObj = signals.map_pressure as Record<string, unknown> | undefined
    let mapKpa: number | null =
      typeof mapObj?.map_kpa === 'number'
        ? (mapObj.map_kpa as number)
        : typeof signals.map_kpa === 'number'
          ? (signals.map_kpa as number)
          : null
    const mapSpeedKmh =
      typeof mapObj?.speed_kmh === 'number'
        ? (mapObj.speed_kmh as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : null
    if (typeof mapKpa === 'number') {
      const warnMapKpa =
        typeof signals.map_warn_kpa === 'number'
          ? (signals.map_warn_kpa as number)
          : 95
      const alertMapKpa =
        typeof signals.map_alert_kpa === 'number'
          ? (signals.map_alert_kpa as number)
          : 105
      const mapMinSpd =
        typeof signals.map_speed_min_kmh === 'number'
          ? (signals.map_speed_min_kmh as number)
          : 20
      const mapSpdOk = typeof mapSpeedKmh === 'number' && mapSpeedKmh >= mapMinSpd
      if (
        mapSpdOk &&
        mapKpa >= alertMapKpa &&
        !recentlyAlerted(deviceId, 'map_alert', 120)
      ) {
        insertAlert(
          deviceId,
          'map_alert',
          'critical',
          `MAP crítico · ${Math.round(mapKpa)} kPa`,
          {
            map_kpa: mapKpa,
            alert_kpa: alertMapKpa,
            map_pressure: mapObj ?? null,
          },
        )
        raised.push('map_alert')
      } else if (
        mapSpdOk &&
        mapKpa >= warnMapKpa &&
        mapKpa < alertMapKpa &&
        !recentlyAlerted(deviceId, 'map_warn', 120)
      ) {
        insertAlert(
          deviceId,
          'map_warn',
          'warn',
          `MAP alto · ${Math.round(mapKpa)} kPa`,
          {
            map_kpa: mapKpa,
            warn_kpa: warnMapKpa,
            map_pressure: mapObj ?? null,
          },
        )
        raised.push('map_warn')
      }
    }

    // High throttle / WOT
    const throttlePct =
      typeof signals.throttle_pct === 'number'
        ? (signals.throttle_pct as number)
        : typeof (signals.throttle as Record<string, unknown> | undefined)?.throttle_pct ===
            'number'
          ? ((signals.throttle as Record<string, unknown>).throttle_pct as number)
          : null
    if (typeof throttlePct === 'number') {
      const thrObj = signals.throttle as Record<string, unknown> | undefined
      const highSec =
        typeof signals.throttle_high_sec === 'number'
          ? (signals.throttle_high_sec as number)
          : typeof thrObj?.high_for_sec === 'number'
            ? (thrObj.high_for_sec as number)
            : 0
      const warnPct =
        typeof signals.throttle_warn_pct === 'number'
          ? (signals.throttle_warn_pct as number)
          : 70
      const alertPct =
        typeof signals.throttle_alert_pct === 'number'
          ? (signals.throttle_alert_pct as number)
          : 85
      const alertHold =
        typeof signals.throttle_alert_hold_sec === 'number'
          ? (signals.throttle_alert_hold_sec as number)
          : 8
      const spd =
        typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : typeof speedMps === 'number'
            ? speedMps * 3.6
            : 40
      const bandFromClient =
        typeof thrObj?.band === 'string' ? (thrObj.band as string) : null
      const band =
        bandFromClient === 'alert' || bandFromClient === 'warn'
          ? bandFromClient
          : throttlePct >= alertPct || (throttlePct >= warnPct && highSec >= alertHold)
            ? 'alert'
            : throttlePct >= warnPct && spd >= 20
              ? 'warn'
              : 'ok'
      if (band === 'alert' && !recentlyAlerted(deviceId, 'throttle_alert', 120)) {
        insertAlert(
          deviceId,
          'throttle_alert',
          'critical',
          `Acelerador alto · ${Math.round(throttlePct)}%`,
          { throttle_pct: throttlePct, high_for_sec: highSec, alert_pct: alertPct },
        )
        raised.push('throttle_alert')
      } else if (band === 'warn' && !recentlyAlerted(deviceId, 'throttle_warn', 120)) {
        insertAlert(
          deviceId,
          'throttle_warn',
          'warn',
          `Acelerador abierto · ${Math.round(throttlePct)}%`,
          { throttle_pct: throttlePct, warn_pct: warnPct },
        )
        raised.push('throttle_warn')
      }
    }

    // Unauthorized movement / tow — ignition off (or parking) + speed
    const parkingBrake = signals.parking_brake === true
    const secured = !ignOn || parkingBrake
    const towMovingSec =
      typeof signals.tow_moving_sec === 'number' ? (signals.tow_moving_sec as number) : null
    const towSpdMps =
      speedMps ??
      (typeof signals.speed_mps === 'number'
        ? (signals.speed_mps as number)
        : typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number) / 3.6
          : undefined)
    const towKmh = typeof towSpdMps === 'number' ? towSpdMps * 3.6 : null
    const towSpeedMin =
      typeof signals.tow_speed_min_kmh === 'number' ? (signals.tow_speed_min_kmh as number) : 3
    const towWarnSec =
      typeof signals.tow_warn_sec === 'number' ? (signals.tow_warn_sec as number) : 3
    const towAlertSec =
      typeof signals.tow_alert_sec === 'number' ? (signals.tow_alert_sec as number) : 8
    if (
      secured &&
      typeof towKmh === 'number' &&
      towKmh >= towSpeedMin &&
      typeof towMovingSec === 'number'
    ) {
      if (towMovingSec >= towAlertSec && !recentlyAlerted(deviceId, 'tow_alert', 180)) {
        insertAlert(
          deviceId,
          'tow_alert',
          'critical',
          `Movimiento sin ignición · ${Math.round(towKmh)} km/h (~${Math.round(towMovingSec)}s)`,
          {
            speed_kmh: Math.round(towKmh),
            tow_moving_sec: towMovingSec,
            ignition,
            parking_brake: parkingBrake,
          },
        )
        raised.push('tow_alert')
      } else if (
        towMovingSec >= towWarnSec &&
        towMovingSec < towAlertSec &&
        !recentlyAlerted(deviceId, 'tow_warn', 180)
      ) {
        insertAlert(
          deviceId,
          'tow_warn',
          'warn',
          `Posible remolque · ${Math.round(towKmh)} km/h`,
          {
            speed_kmh: Math.round(towKmh),
            tow_moving_sec: towMovingSec,
            ignition,
            parking_brake: parkingBrake,
          },
        )
        raised.push('tow_warn')
      }
    }

    // Parking brake while moving (driver error)
    if (parkingBrake) {
      const pbrakeKmh =
        typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : typeof speedMps === 'number'
            ? speedMps * 3.6
            : typeof towKmh === 'number'
              ? towKmh
              : null
      if (typeof pbrakeKmh === 'number') {
        const pWarn =
          typeof signals.pbrake_warn_kmh === 'number'
            ? (signals.pbrake_warn_kmh as number)
            : 5
        const pAlert =
          typeof signals.pbrake_alert_kmh === 'number'
            ? (signals.pbrake_alert_kmh as number)
            : 15
        if (pbrakeKmh >= pAlert && !recentlyAlerted(deviceId, 'pbrake_alert', 120)) {
          insertAlert(
            deviceId,
            'pbrake_alert',
            'critical',
            `Freno estacionamiento · ${Math.round(pbrakeKmh)} km/h`,
            { speed_kmh: Math.round(pbrakeKmh), parking_brake: true, alert_kmh: pAlert },
          )
          raised.push('pbrake_alert')
        } else if (
          pbrakeKmh >= pWarn &&
          pbrakeKmh < pAlert &&
          !recentlyAlerted(deviceId, 'pbrake_warn', 120)
        ) {
          insertAlert(
            deviceId,
            'pbrake_warn',
            'warn',
            `Freno estacionamiento · ${Math.round(pbrakeKmh)} km/h`,
            { speed_kmh: Math.round(pbrakeKmh), parking_brake: true, warn_kmh: pWarn },
          )
          raised.push('pbrake_warn')
        }
      }
    }

    // Gear roll — P/N while moving
    const gearName =
      typeof signals.gear === 'string' ? (signals.gear as string).toUpperCase() : ''
    if (gearName === 'P' || gearName === 'N') {
      const rollKmh =
        typeof signals.speed_kmh === 'number'
          ? (signals.speed_kmh as number)
          : typeof speedMps === 'number'
            ? speedMps * 3.6
            : typeof towKmh === 'number'
              ? towKmh
              : null
      if (typeof rollKmh === 'number') {
        const gWarn =
          typeof signals.gear_roll_warn_kmh === 'number'
            ? (signals.gear_roll_warn_kmh as number)
            : 5
        const gAlert =
          typeof signals.gear_roll_alert_kmh === 'number'
            ? (signals.gear_roll_alert_kmh as number)
            : 20
        if (rollKmh >= gAlert && !recentlyAlerted(deviceId, 'gear_roll_alert', 120)) {
          insertAlert(
            deviceId,
            'gear_roll_alert',
            'critical',
            `Rodando en ${gearName} · ${Math.round(rollKmh)} km/h`,
            { gear: gearName, speed_kmh: Math.round(rollKmh), alert_kmh: gAlert },
          )
          raised.push('gear_roll_alert')
        } else if (
          rollKmh >= gWarn &&
          rollKmh < gAlert &&
          !recentlyAlerted(deviceId, 'gear_roll_warn', 120)
        ) {
          insertAlert(
            deviceId,
            'gear_roll_warn',
            'warn',
            `Rodando en ${gearName} · ${Math.round(rollKmh)} km/h`,
            { gear: gearName, speed_kmh: Math.round(rollKmh), warn_kmh: gWarn },
          )
          raised.push('gear_roll_warn')
        }
      }
    }

    // Turn signal stuck (forgotten blinker)
    const turnStuckSec =
      typeof signals.turn_stuck_sec === 'number' ? (signals.turn_stuck_sec as number) : null
    if (typeof turnStuckSec === 'number' && turnStuckSec > 0) {
      const warnSec =
        typeof signals.turn_stuck_warn_sec === 'number'
          ? (signals.turn_stuck_warn_sec as number)
          : 30
      const alertSec =
        typeof signals.turn_stuck_alert_sec === 'number'
          ? (signals.turn_stuck_alert_sec as number)
          : 60
      const side =
        typeof signals.turn_stuck_side === 'string' ? (signals.turn_stuck_side as string) : ''
      const sideLabel = side === 'right' ? 'derecha' : side === 'left' ? 'izquierda' : ''
      if (turnStuckSec >= alertSec && !recentlyAlerted(deviceId, 'turn_stuck_alert', 180)) {
        insertAlert(
          deviceId,
          'turn_stuck_alert',
          'critical',
          `Intermitente olvidado${sideLabel ? ` · ${sideLabel}` : ''} · ${Math.round(turnStuckSec)}s`,
          { turn_stuck_sec: turnStuckSec, side, alert_sec: alertSec },
        )
        raised.push('turn_stuck_alert')
      } else if (
        turnStuckSec >= warnSec &&
        turnStuckSec < alertSec &&
        !recentlyAlerted(deviceId, 'turn_stuck_warn', 180)
      ) {
        insertAlert(
          deviceId,
          'turn_stuck_warn',
          'warn',
          `Intermitente encendido${sideLabel ? ` · ${sideLabel}` : ''} · ${Math.round(turnStuckSec)}s`,
          { turn_stuck_sec: turnStuckSec, side, warn_sec: warnSec },
        )
        raised.push('turn_stuck_warn')
      }
    }

    // Hazard stuck (forgotten emergency lights)
    const hazardStuckSec =
      typeof signals.hazard_stuck_sec === 'number' ? (signals.hazard_stuck_sec as number) : null
    if (typeof hazardStuckSec === 'number' && hazardStuckSec > 0) {
      const warnSec =
        typeof signals.hazard_stuck_warn_sec === 'number'
          ? (signals.hazard_stuck_warn_sec as number)
          : 45
      const alertSec =
        typeof signals.hazard_stuck_alert_sec === 'number'
          ? (signals.hazard_stuck_alert_sec as number)
          : 90
      if (hazardStuckSec >= alertSec && !recentlyAlerted(deviceId, 'hazard_stuck_alert', 180)) {
        insertAlert(
          deviceId,
          'hazard_stuck_alert',
          'critical',
          `Hazard olvidado · ${Math.round(hazardStuckSec)}s`,
          { hazard_stuck_sec: hazardStuckSec, alert_sec: alertSec },
        )
        raised.push('hazard_stuck_alert')
      } else if (
        hazardStuckSec >= warnSec &&
        hazardStuckSec < alertSec &&
        !recentlyAlerted(deviceId, 'hazard_stuck_warn', 180)
      ) {
        insertAlert(
          deviceId,
          'hazard_stuck_warn',
          'warn',
          `Hazard encendido · ${Math.round(hazardStuckSec)}s`,
          { hazard_stuck_sec: hazardStuckSec, warn_sec: warnSec },
        )
        raised.push('hazard_stuck_warn')
      }
    }

    // Sudden fuel drop (theft / leak)
    const fuelDropPct =
      typeof signals.fuel_drop_pct === 'number' ? (signals.fuel_drop_pct as number) : null
    if (typeof fuelDropPct === 'number' && fuelDropPct > 0) {
      const dropWarn =
        typeof signals.fuel_drop_warn_pct === 'number'
          ? (signals.fuel_drop_warn_pct as number)
          : 8
      const dropAlert =
        typeof signals.fuel_drop_alert_pct === 'number'
          ? (signals.fuel_drop_alert_pct as number)
          : 15
      const fuelNow =
        typeof signals.fuel_pct === 'number' ? Math.round(signals.fuel_pct as number) : null
      if (fuelDropPct >= dropAlert && !recentlyAlerted(deviceId, 'fuel_drop_alert', 300)) {
        insertAlert(
          deviceId,
          'fuel_drop_alert',
          'critical',
          `Caída brusca de combustible · −${Math.round(fuelDropPct)}%` +
            (fuelNow != null ? ` (ahora ${fuelNow}%)` : ''),
          {
            fuel_drop_pct: fuelDropPct,
            fuel_pct: fuelNow,
            alert_pct: dropAlert,
          },
        )
        raised.push('fuel_drop_alert')
      } else if (
        fuelDropPct >= dropWarn &&
        fuelDropPct < dropAlert &&
        !recentlyAlerted(deviceId, 'fuel_drop_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'fuel_drop_warn',
          'warn',
          `Combustible bajando rápido · −${Math.round(fuelDropPct)}%` +
            (fuelNow != null ? ` (ahora ${fuelNow}%)` : ''),
          {
            fuel_drop_pct: fuelDropPct,
            fuel_pct: fuelNow,
            warn_pct: dropWarn,
          },
        )
        raised.push('fuel_drop_warn')
      }
    }

    // 12V battery voltage (OBD PID 0142 / CAN)
    const battV =
      typeof signals.battery_voltage_v === 'number'
        ? (signals.battery_voltage_v as number)
        : typeof signals.battery_v === 'number'
          ? (signals.battery_v as number)
          : null
    if (typeof battV === 'number') {
      const warnV =
        typeof signals.battery_warn_v === 'number' ? (signals.battery_warn_v as number) : 12.0
      const alertV =
        typeof signals.battery_alert_v === 'number' ? (signals.battery_alert_v as number) : 11.5
      if (battV < alertV && !recentlyAlerted(deviceId, 'battery_crit', 300)) {
        insertAlert(
          deviceId,
          'battery_crit',
          'critical',
          `Batería crítica · ${battV.toFixed(1)} V`,
          { battery_voltage_v: battV, alert_v: alertV },
        )
        raised.push('battery_crit')
      } else if (
        battV < warnV &&
        battV >= alertV &&
        !recentlyAlerted(deviceId, 'battery_warn', 300)
      ) {
        insertAlert(
          deviceId,
          'battery_warn',
          'warn',
          `Batería baja · ${battV.toFixed(1)} V`,
          { battery_voltage_v: battV, warn_v: warnV },
        )
        raised.push('battery_warn')
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

const INCIDENT_LABELS: Record<string, string> = {
  accident: 'Accidente',
  breakdown: 'Avería',
  traffic: 'Tráfico',
  other: 'Incidente',
}

/** Driver-reported incident (not SOS). Soft-dedupes same category within cooldown. */
export function raiseIncident(
  deviceId: string,
  input: {
    category?: string | null
    note?: string | null
    lat?: number | null
    lng?: number | null
    source?: string | null
    driver_code?: string | null
    driver_name?: string | null
    clip_url?: string | null
  } = {},
): { id: number; deduped: boolean } {
  const rawCat = (input.category || 'other').toLowerCase().trim()
  const category = INCIDENT_LABELS[rawCat] ? rawCat : 'other'
  if (recentlyAlerted(deviceId, 'incident', 45)) {
    const last = db
      .prepare(
        `SELECT id FROM fleet_alerts WHERE device_id = ? AND kind = 'incident' ORDER BY id DESC LIMIT 1`,
      )
      .get(deviceId) as { id: number } | undefined
    return { id: last?.id ?? 0, deduped: true }
  }
  const note = (input.note || '').trim().slice(0, 280)
  const who =
    input.driver_name || input.driver_code
      ? ` (${[input.driver_code, input.driver_name].filter(Boolean).join(' · ')})`
      : ''
  const label = INCIDENT_LABELS[category] || 'Incidente'
  const severity = category === 'accident' ? 'critical' : 'warn'
  const id = insertAlert(
    deviceId,
    'incident',
    severity,
    `${label}${who}${note ? `: ${note}` : ''}`,
    {
      category,
      lat: input.lat ?? null,
      lng: input.lng ?? null,
      note: note || null,
      source: input.source || 'device',
      driver_code: input.driver_code || null,
      driver_name: input.driver_name || null,
      clip_url: input.clip_url || null,
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

/** Merge dashcam clip metadata into an alert payload (panic by default). */
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
         FROM fleet_alerts WHERE id = ? AND device_id = ? LIMIT 1`,
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
