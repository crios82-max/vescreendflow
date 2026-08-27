#!/usr/bin/env node
/**
 * Engine load smoke (VePlayer 0.69 · Fase 13).
 * OBD PID 0104 — calculated engine load %.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(loadPct, speedKmh = 40, warnPct = 80, alertPct = 92, speedMin = 20) {
  if (loadPct == null) return { band: 'idle', showWarn: false, label: '' }
  const load = Math.max(0, Math.min(100, loadPct))
  const warn = Math.max(50, Math.min(98, warnPct))
  const alert = Math.min(100, Math.max(warn + 3, alertPct))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  if (speedKmh < minSpd) {
    return {
      loadPct: load,
      band: 'ok',
      showWarn: false,
      label: load >= 50 ? `Carga · ${Math.trunc(load)}%` : '',
    }
  }
  let band = 'ok'
  if (load >= alert) band = 'alert'
  else if (load >= warn) band = 'warn'
  return {
    loadPct: load,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `Carga · ${Math.trunc(load)}%`,
  }
}

function voicePhrase(st) {
  const p = st.loadPct != null ? `${Math.trunc(st.loadPct)} por ciento` : 'alta'
  if (st.band === 'alert') return `Atención. Carga del motor crítica. ${p}. Reduce demanda.`
  if (st.band === 'warn') return `Cuidado. Carga del motor alta. ${p}.`
  return `Carga del motor a ${p}.`
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
  console.log('engine-load-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(55, 40).band === 'ok', 'ok')
  assert(evaluate(85, 40).band === 'warn' && evaluate(85, 40).showWarn, 'warn')
  assert(
    evaluate(95, 40).band === 'alert' && voicePhrase(evaluate(95, 40)).includes('crítica'),
    'alert',
  )
  assert(evaluate(95, 10).band === 'ok', 'low speed skip')

  // OBD PID 0104: 41 04 CC → 0xCC * 100/255 ≈ 80%
  const a = 0xcc
  assert(Math.round((a * 100) / 255) === 80, 'pid 0104')

  const deviceId = `load-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Load smoke',
      app_version: '0.69.0',
      version_code: 71,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.69.0',
      version_code: 71,
      vehicle_signals: {
        engine_load_pct: 85,
        speed_kmh: 45,
        engine_load_warn_pct: 80,
        engine_load_alert_pct: 92,
        engine_load_speed_min_kmh: 20,
        engine_load: { load_pct: 85, speed_kmh: 45, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('load_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.69.0',
      version_code: 71,
      vehicle_signals: {
        engine_load_pct: 96,
        speed_kmh: 50,
        engine_load_warn_pct: 80,
        engine_load_alert_pct: 92,
        engine_load_speed_min_kmh: 20,
        engine_load: { load_pct: 96, speed_kmh: 50, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('load_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK engine-load-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
