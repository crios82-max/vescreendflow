#!/usr/bin/env node
/** Fase 16 smoke — abs load, rel throttle, accel pedal, O2 B1S2, EGR error. */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

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
  console.log('fase16-smoke →', BASE)

  // PID parsers
  assert(Math.abs(((0x00 * 256 + 0x7f) * 100) / 255 - 50) < 0.5, 'pid 0143')
  assert((0x80 * 100) / 255 === 50.19607843137255, 'pid 0145')
  assert((0x5a / 200) === 0.45, 'pid 014B')
  assert(((0x60 - 128) * 100) / 128 === -25, 'pid 014D')

  const deviceId = `fase16-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Fase 16 smoke',
      app_version: '0.89.0',
      version_code: 91,
    }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.89.0',
      version_code: 91,
      vehicle_signals: {
        absolute_load_pct: 88,
        speed_kmh: 45,
        abs_load_warn_pct: 85,
        abs_load_alert_pct: 95,
        abs_load_speed_min_kmh: 20,
        absolute_load: { load_pct: 88, band: 'warn', show_warn: true },
      },
    }),
  })
  assert(hb1.ok, 'hb abs_load')
  assert((hb1.body.alerts_raised || []).includes('abs_load_warn'), 'abs_load_warn')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        relative_throttle_pct: 92,
        speed_kmh: 50,
        rel_thr_warn_pct: 75,
        rel_thr_alert_pct: 90,
        rel_thr_speed_min_kmh: 20,
        relative_throttle: { throttle_pct: 92, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('rel_thr_alert'), 'rel_thr_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        accel_pedal_pct: 85,
        speed_kmh: 45,
        accel_pedal_warn_pct: 80,
        accel_pedal_alert_pct: 92,
        accel_pedal: { pedal_pct: 85, band: 'warn', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('accel_pedal_warn'), 'accel_pedal_warn')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        o2_b1s2_volts: 0.04,
        speed_kmh: 45,
        rpm: 2000,
        o2_b2_alert_low_v: 0.06,
        o2_b2_voltage: { o2_volts: 0.04, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('o2_b2_alert'), 'o2_b2_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egr_error_pct: -28,
        speed_kmh: 45,
        egr_warn_pct: 15,
        egr_alert_pct: 25,
        egr_error: { error_pct: -28, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('egr_error_alert'), 'egr_error_alert')

  console.log('OK fase16-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
