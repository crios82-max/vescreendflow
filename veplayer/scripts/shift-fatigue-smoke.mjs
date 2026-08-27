#!/usr/bin/env node
/**
 * Shift fatigue smoke (VePlayer 0.40 · Fase 7).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function formatDuration(sec) {
  const s = Math.max(0, Math.floor(sec))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}m`
}

function evaluate(open, durationSec, warnHours = 4, alertHours = 8) {
  if (!open) return { open: false, band: 'idle', showWarn: false, label: '' }
  const dur = Math.max(0, durationSec)
  const warnSec = Math.max(0.25, warnHours) * 3600
  const alertSec = Math.max(warnHours + 0.25, alertHours) * 3600
  let band = 'ok'
  if (dur >= alertSec) band = 'alert'
  else if (dur >= warnSec) band = 'warn'
  return {
    open: true,
    durationSec: dur,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: formatDuration(dur),
  }
}

function voicePhrase(st) {
  const hours = st.durationSec / 3600
  const hLabel = hours >= 1 ? `${hours.toFixed(1)} horas` : `${Math.floor(st.durationSec / 60)} minutos`
  if (st.band === 'alert') {
    return `Atención. Turno prolongado. Llevas ${hLabel}. Es momento de un descanso.`
  }
  if (st.band === 'warn') return `Cuidado. Llevas ${hLabel} de turno. Considera una pausa.`
  return `Turno en curso. ${hLabel}.`
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
  console.log('shift-fatigue-smoke →', BASE)
  assert(evaluate(false, 99999).band === 'idle', 'idle')
  const ok = evaluate(true, 2 * 3600)
  assert(ok.band === 'ok' && !ok.showWarn && ok.label.includes('2h'), 'ok')
  const warn = evaluate(true, 5 * 3600)
  assert(warn.band === 'warn' && warn.showWarn, 'warn')
  const alert = evaluate(true, 9 * 3600)
  assert(alert.band === 'alert' && voicePhrase(alert).includes('descanso'), 'alert')

  const deviceId = `fatigue-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Fatigue smoke',
      app_version: '0.40.0',
      version_code: 42,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.40.0',
      version_code: 42,
      vehicle_signals: {
        shift_duration_sec: 5 * 3600,
        shift_warn_sec: 4 * 3600,
        shift_alert_sec: 8 * 3600,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  const raisedWarn = hbWarn.body.alerts_raised || []
  assert(raisedWarn.includes('shift_warn'), `warn raised ${JSON.stringify(raisedWarn)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.40.0',
      version_code: 42,
      vehicle_signals: {
        shift_duration_sec: 9 * 3600,
        shift_warn_sec: 4 * 3600,
        shift_alert_sec: 8 * 3600,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  const raisedAlert = hbAlert.body.alerts_raised || []
  assert(raisedAlert.includes('shift_fatigue'), `fatigue raised ${JSON.stringify(raisedAlert)}`)

  console.log(
    'OK shift-fatigue-smoke ·',
    alert.label,
    '· raised',
    [...raisedWarn, ...raisedAlert].filter((k) => k.startsWith('shift')),
  )
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
