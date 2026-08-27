#!/usr/bin/env node
/**
 * Coolant overheat smoke (VePlayer 0.47 · Fase 8).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(coolantC, warnC = 105, alertC = 115) {
  if (coolantC == null) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.max(80, warnC)
  const alert = Math.max(warn + 1, alertC)
  let band = 'ok'
  if (coolantC >= alert) band = 'alert'
  else if (coolantC >= warn) band = 'warn'
  return {
    coolantC,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `${Math.trunc(coolantC)}°C`,
  }
}

function voicePhrase(st) {
  const c = st.coolantC != null ? `${Math.trunc(st.coolantC)} grados` : 'elevada'
  if (st.band === 'alert') {
    return `Atención. Temperatura del motor crítica. ${c}. Detén el vehículo con seguridad.`
  }
  if (st.band === 'warn') return `Cuidado. Motor caliente. Refrigerante a ${c}.`
  return `Refrigerante a ${c}.`
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
  console.log('coolant-overheat-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(90).band === 'ok', 'ok')
  assert(evaluate(108).band === 'warn' && evaluate(108).showWarn, 'warn')
  assert(evaluate(118).band === 'alert' && voicePhrase(evaluate(118)).includes('crítica'), 'alert')

  const deviceId = `coolant-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Coolant smoke',
      app_version: '0.47.0',
      version_code: 49,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.47.0',
      version_code: 49,
      vehicle_signals: {
        coolant_c: 108,
        coolant_warn_c: 105,
        coolant_alert_c: 115,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('coolant_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.47.0',
      version_code: 49,
      vehicle_signals: {
        coolant_c: 120,
        coolant_warn_c: 105,
        coolant_alert_c: 115,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('coolant_overheat'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK coolant-overheat-smoke · warn+overheat')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
