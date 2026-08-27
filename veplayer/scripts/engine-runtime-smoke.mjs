#!/usr/bin/env node
/**
 * Engine runtime smoke (VePlayer 0.68 · Fase 12).
 * OBD PID 011F — run time since engine start.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(runtimeSec, warnSec = 2 * 3600, alertSec = 4 * 3600) {
  if (runtimeSec == null) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.max(600, warnSec)
  const alert = Math.max(warn + 60, alertSec)
  let band = 'ok'
  if (runtimeSec >= alert) band = 'alert'
  else if (runtimeSec >= warn) band = 'warn'
  const h = Math.floor(runtimeSec / 3600)
  const m = Math.floor((runtimeSec % 3600) / 60)
  const label = h > 0 ? `Motor · ${h}h ${String(m).padStart(2, '0')}m` : `Motor · ${m}m`
  return {
    runtimeSec,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label,
  }
}

function voicePhrase(st) {
  const sec = st.runtimeSec || 0
  const hLabel =
    sec >= 3600 ? `${(sec / 3600).toFixed(1)} horas` : `${Math.max(1, Math.floor(sec / 60))} minutos`
  if (st.band === 'alert')
    return `Atención. Motor encendido ${hLabel}. Apaga el motor si estás parado.`
  if (st.band === 'warn')
    return `Cuidado. El motor lleva ${hLabel} en marcha. Considera apagarlo en parado.`
  return `Tiempo de motor. ${hLabel}.`
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
  console.log('engine-runtime-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(3600).band === 'ok', 'ok 1h')
  assert(evaluate(2 * 3600).band === 'warn' && evaluate(2 * 3600).showWarn, 'warn 2h')
  assert(
    evaluate(4 * 3600).band === 'alert' && voicePhrase(evaluate(4 * 3600)).includes('Atención'),
    'alert 4h',
  )

  // OBD PID 011F decode: 41 1F 1C 20 → 0x1C20 = 7200 s = 2 h
  const a = 0x1c
  const b = 0x20
  assert(a * 256 + b === 7200, 'pid 011F')

  const deviceId = `runtime-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Runtime smoke',
      app_version: '0.68.0',
      version_code: 70,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.68.0',
      version_code: 70,
      vehicle_signals: {
        runtime_sec: 2 * 3600,
        runtime_warn_sec: 2 * 3600,
        runtime_alert_sec: 4 * 3600,
        engine_runtime: {
          runtime_sec: 2 * 3600,
          band: 'warn',
          show_warn: true,
          label: 'Motor · 2h 00m',
        },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('runtime_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.68.0',
      version_code: 70,
      vehicle_signals: {
        runtime_sec: 4 * 3600,
        runtime_warn_sec: 2 * 3600,
        runtime_alert_sec: 4 * 3600,
        engine_runtime: {
          runtime_sec: 4 * 3600,
          band: 'alert',
          show_warn: true,
          label: 'Motor · 4h 00m',
        },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('runtime_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK engine-runtime-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
