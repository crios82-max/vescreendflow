#!/usr/bin/env node
/**
 * 12V battery voltage smoke (VePlayer 0.54 · Fase 10).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(volts, warnV = 12.0, alertV = 11.5) {
  if (volts == null) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.min(13.5, Math.max(10, warnV))
  const alert = Math.min(warn - 0.05, Math.max(9, alertV))
  let band = 'ok'
  if (volts < alert) band = 'alert'
  else if (volts < warn) band = 'warn'
  return {
    volts,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `${volts.toFixed(1)} V`,
  }
}

function voicePhrase(st) {
  const v = st.volts != null ? `${st.volts.toFixed(1)} voltios` : 'baja'
  if (st.band === 'alert') return `Atención. Batería crítica. ${v}. Revisa el sistema eléctrico.`
  if (st.band === 'warn') return `Cuidado. Voltaje de batería bajo. ${v}.`
  return `Batería a ${v}.`
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
  console.log('battery-voltage-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(13.8).band === 'ok', 'ok')
  assert(evaluate(11.8).band === 'warn' && evaluate(11.8).showWarn, 'warn')
  assert(
    evaluate(11.0).band === 'alert' && voicePhrase(evaluate(11.0)).includes('crítica'),
    'alert',
  )

  // OBD PID 0142 decode: 41 42 2E E0 → (0x2EE0)/1000 = 12.0 V
  const a = 0x2e
  const b = 0xe0
  assert(((a * 256 + b) / 1000).toFixed(1) === '12.0', 'pid 0142')

  const deviceId = `batt-v-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Batt V smoke',
      app_version: '0.54.0',
      version_code: 56,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.54.0',
      version_code: 56,
      vehicle_signals: {
        battery_voltage_v: 11.8,
        battery_warn_v: 12.0,
        battery_alert_v: 11.5,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('battery_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.54.0',
      version_code: 56,
      vehicle_signals: {
        battery_voltage_v: 11.0,
        battery_warn_v: 12.0,
        battery_alert_v: 11.5,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('battery_crit'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK battery-voltage-smoke · warn+crit')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
