#!/usr/bin/env node
/**
 * Sudden fuel drop smoke (VePlayer 0.50 · Fase 9).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(fuelPct, dropPct, warnPct = 8, alertPct = 15, windowSec = 60) {
  if (fuelPct == null) return { band: 'idle', showWarn: false, label: '' }
  const drop = Math.max(0, dropPct)
  const warn = Math.max(2, warnPct)
  const alert = Math.max(warn + 1, alertPct)
  let band = 'ok'
  if (drop >= alert) band = 'alert'
  else if (drop >= warn) band = 'warn'
  return {
    fuelPct,
    dropPct: drop,
    windowSec,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: band === 'ok' ? `${Math.trunc(fuelPct)}%` : `−${Math.trunc(drop)}% · ${Math.trunc(fuelPct)}%`,
  }
}

function voicePhrase(st) {
  const drop = Math.trunc(st.dropPct)
  const fuel = st.fuelPct != null ? `${Math.trunc(st.fuelPct)} por ciento` : 'desconocido'
  if (st.band === 'alert') {
    return `Atención. Caída brusca de combustible. Menos ${drop} por ciento. Nivel actual ${fuel}.`
  }
  if (st.band === 'warn') {
    return `Cuidado. Combustible bajando rápido. Menos ${drop} por ciento.`
  }
  return `Combustible a ${fuel}.`
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
  console.log('fuel-drop-smoke →', BASE)
  assert(evaluate(null, 0).band === 'idle', 'idle')
  assert(evaluate(80, 2).band === 'ok', 'ok')
  assert(evaluate(70, 10).band === 'warn' && evaluate(70, 10).showWarn, 'warn')
  assert(
    evaluate(60, 20).band === 'alert' && voicePhrase(evaluate(60, 20)).includes('brusca'),
    'alert',
  )

  const deviceId = `fuel-drop-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Fuel drop smoke',
      app_version: '0.50.0',
      version_code: 52,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.50.0',
      version_code: 52,
      vehicle_signals: {
        fuel_pct: 72,
        fuel_drop_pct: 10,
        fuel_drop_warn_pct: 8,
        fuel_drop_alert_pct: 15,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('fuel_drop_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.50.0',
      version_code: 52,
      vehicle_signals: {
        fuel_pct: 55,
        fuel_drop_pct: 20,
        fuel_drop_warn_pct: 8,
        fuel_drop_alert_pct: 15,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('fuel_drop_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK fuel-drop-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
