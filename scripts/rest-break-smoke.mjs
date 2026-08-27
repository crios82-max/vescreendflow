#!/usr/bin/env node
/**
 * Rest break reminder smoke (VePlayer 0.56 · Fase 10).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(drivingSec, warnSec = 7200, alertSec = 9000) {
  const drive = Math.max(0, drivingSec)
  if (drive <= 0) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.max(600, warnSec)
  const alert = Math.max(warn + 60, alertSec)
  let band = 'ok'
  if (drive >= alert) band = 'alert'
  else if (drive >= warn) band = 'warn'
  const h = Math.floor(drive / 3600)
  const m = Math.floor((drive % 3600) / 60)
  const label =
    band === 'ok'
      ? `Conduciendo · ${h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}m`}`
      : `Descanso · ${h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}m`}`
  return { band, showWarn: band === 'warn' || band === 'alert', label, drivingSec: drive }
}

function voicePhrase(st) {
  const hLabel =
    st.drivingSec >= 3600
      ? `${(st.drivingSec / 3600).toFixed(1)} horas`
      : `${Math.trunc(st.drivingSec / 60)} minutos`
  if (st.band === 'alert') {
    return `Atención. Llevas ${hLabel} al volante sin pausa. Es hora de un descanso.`
  }
  if (st.band === 'warn') {
    return `Cuidado. Llevas ${hLabel} conduciendo. Toma una pausa pronto.`
  }
  return `Conducción continua. ${hLabel}.`
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
  console.log('rest-break-smoke →', BASE)
  assert(evaluate(0).band === 'idle', 'idle')
  assert(evaluate(3600).band === 'ok', 'ok')
  assert(evaluate(2 * 3600 + 60).band === 'warn' && evaluate(2 * 3600 + 60).showWarn, 'warn')
  assert(
    evaluate(2.5 * 3600 + 10).band === 'alert' &&
      voicePhrase(evaluate(2.5 * 3600 + 10)).includes('descanso'),
    'alert',
  )

  const deviceId = `rest-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Rest break smoke',
      app_version: '0.56.0',
      version_code: 58,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.56.0',
      version_code: 58,
      vehicle_signals: {
        rest_drive_sec: 2 * 3600 + 120,
        rest_warn_sec: 2 * 3600,
        rest_alert_sec: 2.5 * 3600,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('rest_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.56.0',
      version_code: 58,
      vehicle_signals: {
        rest_drive_sec: 3 * 3600,
        rest_warn_sec: 2 * 3600,
        rest_alert_sec: 2.5 * 3600,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('rest_break'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK rest-break-smoke · warn+break')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
