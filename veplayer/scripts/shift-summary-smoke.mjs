#!/usr/bin/env node
/**
 * End-of-shift summary smoke (VePlayer 0.52 · Fase 9).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function buildSummary(shift) {
  const ended = shift.ended_at ?? Math.floor(Date.now() / 1000)
  const durationSec = Math.max(0, ended - shift.started_at)
  const hours = durationSec / 3600
  const durationLabel =
    hours >= 1 ? `${hours.toFixed(1)} h` : `${Math.max(1, Math.round(durationSec / 60))} min`
  const message = [
    `Turno #${shift.id}`,
    durationLabel,
    `${Number(shift.distance_km).toFixed(1)} km`,
    shift.eco_score != null ? `eco ${Math.round(shift.eco_score)}` : null,
  ]
    .filter(Boolean)
    .join(' · ')
  return {
    shift_id: shift.id,
    duration_sec: durationSec,
    duration_label: durationLabel,
    distance_km: Math.round(shift.distance_km * 10) / 10,
    eco_score: shift.eco_score != null ? Math.round(shift.eco_score) : null,
    eco_band: shift.eco_band,
    idle_min: Math.round((shift.idle_sec || 0) / 60),
    message,
  }
}

function voicePhrase(st) {
  const km = Number(st.distance_km).toFixed(1)
  const eco = st.eco_score != null ? ` Eco ${st.eco_score}.` : ''
  return `Turno cerrado. ${st.duration_label}. ${km} kilómetros.${eco}`
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

async function j(path, init = {}) {
  const r = await fetch(BASE + path, {
    ...init,
    headers: { 'content-type': 'application/json', ...(init.headers || {}) },
  })
  const text = await r.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { ok: r.ok, status: r.status, body }
}

async function main() {
  console.log('shift-summary-smoke →', BASE)
  const local = buildSummary({
    id: 1,
    started_at: Math.floor(Date.now() / 1000) - 5400,
    ended_at: Math.floor(Date.now() / 1000),
    distance_km: 42.3,
    eco_score: 88,
    eco_band: 'good',
    idle_sec: 600,
  })
  assert(local.duration_label.includes('h'), 'duration label')
  assert(local.message.includes('42.3'), 'message km')
  assert(voicePhrase(local).includes('cerrado'), 'voice')

  const deviceId = `shift-sum-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Shift summary smoke',
      app_version: '0.52.0',
      version_code: 54,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const start = await j('/api/fleet/shifts/start', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, odo_km: 1000 }),
  })
  assert(start.ok || start.status === 201, `start ${start.status}`)
  const shiftId = start.body.shift?.id
  assert(shiftId > 0, 'shift id')

  await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.52.0',
      version_code: 54,
      odo_km: 1018,
      vehicle_signals: {
        odometer_km: 1018,
        speed_mps: 12,
        idle_sec: 90,
        speed_limit_kmh: 50,
      },
    }),
  })

  const end = await j('/api/fleet/shifts/end', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, odo_km: 1025, distance_km: 25 }),
  })
  assert(end.ok, `end ${end.status}`)
  assert(end.body.shift?.status === 'closed', 'closed')
  assert(end.body.summary?.shift_id === shiftId, 'summary id')
  assert(typeof end.body.summary?.duration_sec === 'number', 'duration')
  assert(end.body.summary?.distance_km >= 25, `km ${end.body.summary?.distance_km}`)
  assert(String(end.body.summary?.message || '').includes('Turno'), 'message')

  const getSum = await j(`/api/fleet/shifts/${shiftId}/summary`)
  assert(getSum.ok, `get summary ${getSum.status}`)
  assert(getSum.body.summary?.shift_id === shiftId, 'get summary id')

  console.log('OK shift-summary-smoke ·', end.body.summary.message)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
