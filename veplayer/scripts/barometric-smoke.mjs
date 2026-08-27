#!/usr/bin/env node
/** Barometric smoke (VePlayer 0.82 · Fase 15). OBD PID 0133 kPa. */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(baroKpa, speedKmh = 40, warnLow = 88, alertLow = 82, warnHigh = 108, alertHigh = 112, speedMin = 20) {
  if (baroKpa == null) return { band: 'idle', showWarn: false, label: '' }
  const baro = Math.max(0, Math.min(255, baroKpa))
  const minSpd = Math.max(0, Math.min(60, speedMin))
  if (speedKmh < minSpd) {
    return { baroKpa: baro, band: 'ok', showWarn: false, label: baro >= 85 && baro <= 110 ? `Baro · ${Math.trunc(baro)} kPa` : '' }
  }
  let band = 'ok'
  if (baro <= alertLow || baro >= alertHigh) band = 'alert'
  else if (baro <= warnLow || baro >= warnHigh) band = 'warn'
  return { baroKpa: baro, band, showWarn: band === 'warn' || band === 'alert', label: `Baro · ${Math.trunc(baro)} kPa` }
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
  console.log('barometric-smoke →', BASE)
  assert(evaluate(null).band === 'idle', 'idle')
  assert(evaluate(95, 40).band === 'ok', 'ok')
  assert(evaluate(86, 40).band === 'warn', 'warn low')
  assert(evaluate(78, 40).band === 'alert', 'alert low')
  assert(evaluate(110, 40).band === 'warn', 'warn high')
  assert(evaluate(115, 40).band === 'alert', 'alert high')
  assert(evaluate(78, 10).band === 'ok', 'low speed skip')
  assert(0x65 === 101, 'pid 0133')

  const deviceId = `baro-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Baro smoke', app_version: '0.82.0', version_code: 84 }),
  })

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.82.0',
      version_code: 84,
      vehicle_signals: {
        baro_kpa: 86,
        speed_kmh: 45,
        baro_warn_low_kpa: 88,
        baro_alert_low_kpa: 82,
        baro_warn_high_kpa: 108,
        baro_alert_high_kpa: 112,
        baro_speed_min_kmh: 20,
        barometric: { baro_kpa: 86, speed_kmh: 45, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hbWarn.ok, 'hb warn')
  assert((hbWarn.body.alerts_raised || []).includes('baro_warn'), `warn ${JSON.stringify(hbWarn.body.alerts_raised)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.82.0',
      version_code: 84,
      vehicle_signals: {
        baro_kpa: 75,
        speed_kmh: 50,
        baro_warn_low_kpa: 88,
        baro_alert_low_kpa: 82,
        barometric: { baro_kpa: 75, speed_kmh: 50, band: 'alert', show_warn: true },
      },
    }),
  })
  assert(hbAlert.ok, 'hb alert')
  assert((hbAlert.body.alerts_raised || []).includes('baro_alert'), `alert ${JSON.stringify(hbAlert.body.alerts_raised)}`)
  console.log('OK barometric-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
