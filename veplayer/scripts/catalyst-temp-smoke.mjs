#!/usr/bin/env node
/**
 * Catalyst temp smoke (VePlayer 0.78 · Fase 14).
 * OBD PID 0134 — ((256*A)+B)/10 - 40 °C.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(catalystTempC, warnC = 750, alertC = 850) {
  if (catalystTempC == null) return { band: 'idle', showWarn: false, label: '' }
  const c = Math.max(-40, Math.min(1200, catalystTempC))
  const warn = Math.max(400, warnC)
  const alert = Math.max(warn + 10, alertC)
  let band = 'ok'
  if (c >= alert) band = 'alert'
  else if (c >= warn) band = 'warn'
  return {
    catalystTempC: c,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `Cat · ${Math.trunc(c)}°C`,
  }
}

function voicePhrase(st) {
  const c = st.catalystTempC != null ? `${Math.trunc(st.catalystTempC)} grados` : 'elevada'
  if (st.band === 'alert') {
    return `Atención. Catalizador crítico. ${c}. Reduce carga y revisa motor.`
  }
  if (st.band === 'warn') return `Cuidado. Catalizador muy caliente. ${c}.`
  return `Catalizador a ${c}.`
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
  console.log('catalyst-temp-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(600).band === 'ok', 'ok')
  assert(evaluate(780).band === 'warn' && evaluate(780).showWarn, 'warn')
  assert(
    evaluate(900).band === 'alert' && voicePhrase(evaluate(900)).includes('crítico'),
    'alert',
  )

  // OBD PID 0134: 41 34 1F 40 → (7936+64)/10 - 40 = 760 °C
  const a = 0x1f
  const b = 0x40
  assert((a * 256 + b) / 10 - 40 === 760, 'pid 0134')

  const deviceId = `catalyst-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Catalyst smoke',
      app_version: '0.78.0',
      version_code: 80,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.78.0',
      version_code: 80,
      vehicle_signals: {
        catalyst_temp_c: 780,
        catalyst_warn_c: 750,
        catalyst_alert_c: 850,
        catalyst_temp: {
          catalyst_temp_c: 780,
          band: 'warn',
          show_warn: true,
          label: 'Cat · 780°C',
        },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('catalyst_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.78.0',
      version_code: 80,
      vehicle_signals: {
        catalyst_temp_c: 900,
        catalyst_warn_c: 750,
        catalyst_alert_c: 850,
        catalyst_temp: {
          catalyst_temp_c: 900,
          band: 'alert',
          show_warn: true,
          label: 'Cat · 900°C',
        },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('catalyst_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK catalyst-temp-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
