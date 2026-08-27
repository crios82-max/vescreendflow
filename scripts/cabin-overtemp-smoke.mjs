#!/usr/bin/env node
/**
 * Cabin overtemp smoke (VePlayer 0.42 · Fase 7).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(cabinC, outdoorC = null, warnC = 32, alertC = 38) {
  if (cabinC == null) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.max(20, warnC)
  const alert = Math.max(warn + 1, alertC)
  let band = 'ok'
  if (cabinC >= alert) band = 'alert'
  else if (cabinC >= warn) band = 'warn'
  return {
    cabinC,
    outdoorC,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `${Math.trunc(cabinC)}°C`,
  }
}

function voicePhrase(st) {
  const c = st.cabinC != null ? `${Math.trunc(st.cabinC)} grados` : 'elevada'
  if (st.band === 'alert') {
    return `Atención. Temperatura de cabina crítica. ${c}. Ventila o enciende el aire.`
  }
  if (st.band === 'warn') return `Cuidado. Cabina caliente. ${c}.`
  return `Cabina a ${c}.`
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
  console.log('cabin-overtemp-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(28).band === 'ok' && !evaluate(28).showWarn, 'ok')
  const warn = evaluate(34)
  assert(warn.band === 'warn' && warn.showWarn, 'warn')
  const alert = evaluate(40)
  assert(alert.band === 'alert' && voicePhrase(alert).includes('crítica'), 'alert')

  const deviceId = `cabin-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Cabin overtemp smoke',
      app_version: '0.42.0',
      version_code: 44,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.42.0',
      version_code: 44,
      vehicle_signals: {
        hvac: { cabin_c: 34, target_c: 22, ac_on: false, fan: 0 },
        cabin_warn_c: 32,
        cabin_alert_c: 38,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  const raisedWarn = hbWarn.body.alerts_raised || []
  assert(raisedWarn.includes('cabin_warn'), `warn ${JSON.stringify(raisedWarn)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.42.0',
      version_code: 44,
      vehicle_signals: {
        hvac: { cabin_c: 41, target_c: 22, ac_on: true, fan: 3 },
        cabin_warn_c: 32,
        cabin_alert_c: 38,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  const raisedAlert = hbAlert.body.alerts_raised || []
  assert(raisedAlert.includes('cabin_overtemp'), `alert ${JSON.stringify(raisedAlert)}`)

  console.log(
    'OK cabin-overtemp-smoke ·',
    alert.label,
    '· raised',
    [...raisedWarn, ...raisedAlert].filter((k) => k.startsWith('cabin')),
  )
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
