#!/usr/bin/env node
/**
 * MAF airflow smoke (VePlayer 0.79 · Fase 15).
 * OBD PID 0110 — mass air flow g/s.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(mafGps, speedKmh = 40, warnGps = 80, alertGps = 110, speedMin = 20) {
  if (mafGps == null) return { band: 'idle', showWarn: false, label: '' }
  const gps = Math.max(0, mafGps)
  const warn = Math.max(20, Math.min(300, warnGps))
  const alert = Math.min(400, Math.max(warn + 10, alertGps))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  if (speedKmh < minSpd) {
    return {
      mafGps: gps,
      band: 'ok',
      showWarn: false,
      label: gps >= 25 ? `MAF · ${Math.trunc(gps)} g/s` : '',
    }
  }
  let band = 'ok'
  if (gps >= alert) band = 'alert'
  else if (gps >= warn) band = 'warn'
  return {
    mafGps: gps,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: `MAF · ${Math.trunc(gps)} g/s`,
  }
}

function voicePhrase(st) {
  const g = st.mafGps != null ? `${Math.trunc(st.mafGps)} gramos por segundo` : 'alta'
  if (st.band === 'alert') return `Atención. Flujo de aire MAF crítico. ${g}. Reduce demanda.`
  if (st.band === 'warn') return `Cuidado. Flujo de aire alto. ${g}.`
  return `Flujo MAF a ${g}.`
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
  console.log('maf-airflow-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(50, 40).band === 'ok', 'ok')
  assert(evaluate(90, 40).band === 'warn' && evaluate(90, 40).showWarn, 'warn')
  assert(
    evaluate(120, 40).band === 'alert' && voicePhrase(evaluate(120, 40)).includes('crítico'),
    'alert',
  )
  assert(evaluate(120, 10).band === 'ok', 'low speed skip')

  // OBD PID 0110: 41 10 23 28 → (0x2328)/100 = 90 g/s
  const a = 0x23
  const b = 0x28
  assert((a * 256 + b) / 100 === 90, 'pid 0110')

  const deviceId = `maf-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'MAF smoke',
      app_version: '0.79.0',
      version_code: 81,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.79.0',
      version_code: 81,
      vehicle_signals: {
        maf_gps: 90,
        speed_kmh: 45,
        maf_warn_gps: 80,
        maf_alert_gps: 110,
        maf_speed_min_kmh: 20,
        maf_airflow: { maf_gps: 90, speed_kmh: 45, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  assert(
    (hbWarn.body.alerts_raised || []).includes('maf_warn'),
    `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`,
  )

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.79.0',
      version_code: 81,
      vehicle_signals: {
        maf_gps: 120,
        speed_kmh: 50,
        maf_warn_gps: 80,
        maf_alert_gps: 110,
        maf_speed_min_kmh: 20,
        maf_airflow: { maf_gps: 120, speed_kmh: 50, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  assert(
    (hbAlert.body.alerts_raised || []).includes('maf_alert'),
    `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`,
  )

  console.log('OK maf-airflow-smoke · warn+alert')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
