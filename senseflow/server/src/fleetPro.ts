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
    if (signals.abs_active === true && !recentlyAlerted(deviceId, 'abs', 120)) {
      insertAlert(deviceId, 'abs', 'warn', 'ABS activo', { abs_active: true })
      raised.push('abs')
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
