#!/usr/bin/env node
/**
 * RPM over-rev smoke (VePlayer 0.59 · Fase 11).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(rpm, warnRpm = 4500, alertRpm = 5500) {
  if (rpm == null) return { band: 'idle', showWarn: false, label: '' }
  const warn = Math.max(2500, Math.min(7000, warnRpm))
  const alert = Math.max(warn + 100, alertRpm)
  let band = 'ok'
  if (rpm >= alert) band = 'alert'
  else if (rpm >= warn) band = 'warn'
  return {
    rpm,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `${Math.trunc(rpm)} rpm`,
  }
}

function voicePhrase(st) {
  const r = st.rpm != null ? `${Math.trunc(st.rpm)} revoluciones` : 'elevadas'
  if (st.band === 'alert') return `Atención. Régimen del motor crítico. ${r}. Reduce aceleración.`
  if (st.band === 'warn') return `Cuidado. Revoluciones altas. ${r}.`
  return `Motor a ${r}.`
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
  console.log('rpm-overrev-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(2200).band === 'ok', 'ok')
  assert(evaluate(4800).band === 'warn' && evaluate(4800).showWarn, 'warn')
  assert(
    evaluate(5800).band === 'alert' && voicePhrase(evaluate(5800)).includes('crítico'),
    'alert',
  )

  // OBD PID 010C: 41 0C 1A F0 → ((0x1A << 8) + 0xF0) / 4 = 1724 rpm
  const a = 0x1a
  const b = 0xf0
  assert(Math.trunc(((a << 8) + b) / 4) === 1724, 'pid 010C')

  const deviceId = `rpm-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'RPM smoke',
      app_version: '0.59.0',
      version_code: 61,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.59.0',
      version_code: 61,
      vehicle_signals: {
        rpm: 4800,
        rpm_warn: 4500,
        rpm_alert: 5500,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('rpm_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.59.0',
      version_code: 61,
      vehicle_signals: {
        rpm: 6000,
        rpm_warn: 4500,
        rpm_alert: 5500,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('rpm_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK rpm-overrev-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
