#!/usr/bin/env node
/**
 * Per-wheel TPMS HUD smoke (VePlayer 0.51 · Fase 9).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(fl, fr, rl, rr, warnPsi = 28, alertPsi = 24) {
  const raw = [
    ['FL', fl],
    ['FR', fr],
    ['RL', rl],
    ['RR', rr],
  ]
  if (raw.every(([, p]) => p == null)) return { band: 'idle', showWarn: false, label: '', lowWheels: [] }
  const warn = Math.min(40, Math.max(15, warnPsi))
  const alert = Math.min(warn - 0.5, Math.max(10, alertPsi))
  const readings = raw
    .filter(([, p]) => p != null)
    .map(([id, p]) => {
      let band = 'ok'
      if (p < alert) band = 'alert'
      else if (p < warn) band = 'warn'
      return { id, psi: p, band }
    })
  const low = readings.filter((r) => r.band === 'warn' || r.band === 'alert')
  let band = 'ok'
  if (readings.some((r) => r.band === 'alert')) band = 'alert'
  else if (readings.some((r) => r.band === 'warn')) band = 'warn'
  const minPsi = Math.min(...readings.map((r) => r.psi))
  const lowWheels = low.map((r) => r.id)
  return {
    band,
    showWarn: band === 'warn' || band === 'alert',
    lowWheels,
    minPsi,
    label:
      band === 'ok'
        ? `TPMS ${Math.trunc(minPsi)}`
        : `TPMS ${lowWheels.join('·')} · ${Math.trunc(minPsi)} psi`,
    detail: readings.map((r) => `${r.id} ${Math.trunc(r.psi)}`).join(' · '),
  }
}

function voicePhrase(st) {
  const which = st.lowWheels.length ? st.lowWheels.join(', ') : 'neumáticos'
  const psi = st.minPsi != null ? `${Math.trunc(st.minPsi)} psi` : 'baja'
  if (st.band === 'alert') return `Atención. Presión crítica en ${which}. ${psi}.`
  if (st.band === 'warn') return `Cuidado. Presión baja en ${which}. ${psi}.`
  return 'Presión de neumáticos normal.'
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
  console.log('tpms-hud-smoke →', BASE)
  assert(evaluate(null, null, null, null).band === 'idle', 'idle')
  assert(evaluate(32, 32, 33, 32).band === 'ok', 'ok')
  assert(evaluate(26, 32, 33, 32).band === 'warn' && evaluate(26, 32, 33, 32).lowWheels.includes('FL'), 'warn')
  assert(
    evaluate(20, 32, 33, 32).band === 'alert' &&
      voicePhrase(evaluate(20, 32, 33, 32)).includes('crítica'),
    'alert',
  )

  const deviceId = `tpms-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'TPMS smoke',
      app_version: '0.51.0',
      version_code: 53,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.51.0',
      version_code: 53,
      vehicle_signals: {
        tpms_warn_psi: 28,
        tpms_alert_psi: 24,
        tpms: { fl_psi: 26, fr_psi: 32, rl_psi: 33, rr_psi: 32, low: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('tpms_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.51.0',
      version_code: 53,
      vehicle_signals: {
        tpms_warn_psi: 28,
        tpms_alert_psi: 24,
        tpms: { fl_psi: 20, fr_psi: 32, rl_psi: 22, rr_psi: 32, low: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('tpms_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK tpms-hud-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
