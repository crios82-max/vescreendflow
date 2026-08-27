#!/usr/bin/env node
/**
 * Fuel trim STFT smoke (VePlayer 0.75 · Fase 14).
 * OBD PID 0106 — short-term fuel trim % (signed).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function formatTrimPct(pct) {
  const rounded = Math.trunc(pct)
  return rounded >= 0 ? `+${rounded}%` : `${rounded}%`
}

function evaluate(trimPct, speedKmh = 40, warnPct = 12, alertPct = 20, speedMin = 20) {
  if (trimPct == null) return { band: 'idle', showWarn: false, label: '' }
  const trim = Math.max(-50, Math.min(50, trimPct))
  const warn = Math.max(5, Math.min(40, warnPct))
  const alert = Math.min(50, Math.max(warn + 3, alertPct))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  const absTrim = Math.abs(trim)
  if (speedKmh < minSpd) {
    return {
      trimPct: trim,
      band: 'ok',
      showWarn: false,
      label: absTrim >= 8 ? `STFT · ${formatTrimPct(trim)}` : '',
    }
  }
  let band = 'ok'
  if (absTrim >= alert) band = 'alert'
  else if (absTrim >= warn) band = 'warn'
  return {
    trimPct: trim,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `STFT · ${formatTrimPct(trim)}`,
  }
}

function voicePhrase(st) {
  const p = st.trimPct != null ? formatTrimPct(st.trimPct).replace('+', 'más ') : 'alto'
  if (st.band === 'alert') return `Atención. Corrección de combustible STFT crítica. ${p}. Revisa motor.`
  if (st.band === 'warn') return `Cuidado. Corrección STFT fuera de rango. ${p}.`
  return `Corrección STFT a ${p}.`
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
  console.log('fuel-trim-stft-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(8, 40).band === 'ok', 'ok')
  assert(evaluate(15, 40).band === 'warn' && evaluate(15, 40).showWarn, 'warn pos')
  assert(evaluate(-16, 40).band === 'warn' && evaluate(-16, 40).showWarn, 'warn neg')
  assert(
    evaluate(25, 40).band === 'alert' && voicePhrase(evaluate(25, 40)).includes('crítica'),
    'alert',
  )
  assert(evaluate(25, 10).band === 'ok', 'low speed skip')

  // OBD PID 0106: 41 06 A0 → (160-128)*100/128 = +25%
  const a = 0xa0
  assert(Math.round(((a - 128) * 100) / 128) === 25, 'pid 0106')

  const deviceId = `stft-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'STFT smoke',
      app_version: '0.75.0',
      version_code: 77,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.75.0',
      version_code: 77,
      vehicle_signals: {
        fuel_trim_stft_pct: 15,
        speed_kmh: 45,
        stft_warn_pct: 12,
        stft_alert_pct: 20,
        stft_speed_min_kmh: 20,
        fuel_trim_stft: { trim_pct: 15, speed_kmh: 45, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('stft_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.75.0',
      version_code: 77,
      vehicle_signals: {
        fuel_trim_stft_pct: -24,
        speed_kmh: 50,
        stft_warn_pct: 12,
        stft_alert_pct: 20,
        stft_speed_min_kmh: 20,
        fuel_trim_stft: { trim_pct: -24, speed_kmh: 50, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('stft_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK fuel-trim-stft-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
