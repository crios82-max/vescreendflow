#!/usr/bin/env node
/**
 * Outdoor ice / frost smoke (VePlayer 0.60 · Fase 11).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(outdoorC, warnC = 3, alertC = 0) {
  if (outdoorC == null) return { band: 'idle', showWarn: false, label: '' }
  const alert = Math.max(-20, Math.min(5, alertC))
  const warn = Math.min(10, Math.max(alert + 0.5, warnC))
  let band = 'ok'
  if (outdoorC <= alert) band = 'alert'
  else if (outdoorC <= warn) band = 'warn'
  return {
    outdoorC,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `${Math.round(outdoorC)}°C`,
  }
}

function voicePhrase(st) {
  const c = st.outdoorC != null ? `${Math.round(st.outdoorC)} grados` : 'baja'
  if (st.band === 'alert') {
    return `Atención. Riesgo de hielo. Temperatura exterior ${c}. Reduce velocidad.`
  }
  if (st.band === 'warn') return `Cuidado. Posible escarcha. Exterior a ${c}.`
  return `Exterior a ${c}.`
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
  console.log('ice-frost-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(12).band === 'ok', 'ok')
  assert(evaluate(2).band === 'warn' && evaluate(2).showWarn, 'warn')
  assert(
    evaluate(-1).band === 'alert' && voicePhrase(evaluate(-1)).includes('hielo'),
    'alert',
  )
  assert(evaluate(0).band === 'alert', 'zero alert')

  // OBD PID 0146 ambient: 41 46 02 → A-40 = -38? Actually single byte A-40
  // 41 46 2B → 0x2B - 40 = 3 °C
  assert(0x2b - 40 === 3, 'pid 0146')

  const deviceId = `ice-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Ice frost smoke',
      app_version: '0.60.0',
      version_code: 62,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.60.0',
      version_code: 62,
      vehicle_signals: {
        outdoor_temp_c: 2,
        ice_warn_c: 3,
        ice_alert_c: 0,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('ice_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.60.0',
      version_code: 62,
      vehicle_signals: {
        outdoor_temp_c: -2,
        ice_warn_c: 3,
        ice_alert_c: 0,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('ice_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK ice-frost-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
