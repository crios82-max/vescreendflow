#!/usr/bin/env node
/** Fase 18 smoke — EGR cmd, rel pedal, driver/act torque, catalyst B2. */
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
  console.log('fase18-smoke →', BASE)
  assert((0xB4 * 100) / 255 > 70, 'pid 014C')
  assert((0xE6 * 100) / 255 > 90, 'pid 015A')
  assert(0xB9 - 125 === 60, 'pid 0161')
  assert(0x41 - 125 === -60, 'pid 0162')
  assert(((0x22 * 256 + 0xC4) / 10) - 40 === 850, 'pid 0170')

  const deviceId = `fase18-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 18', app_version: '0.99.0', version_code: 101 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        egr_cmd_pct: 75,
        speed_kmh: 45,
        egr_cmd_alert_pct: 70,
        egr_cmd: { egr_pct: 75, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('egr_cmd_alert'), 'egr_cmd_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        rel_accel_pedal_pct: 92,
        speed_kmh: 45,
        rel_aped_alert_pct: 90,
        rel_aped: { pedal_pct: 92, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('rel_aped_alert'), 'rel_aped_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        driver_torque_pct: 60,
        speed_kmh: 45,
        drv_torque_alert_pct: 55,
        drv_torque: { torque_pct: 60, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('drv_torque_alert'), 'drv_torque_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        actual_torque_pct: -60,
        speed_kmh: 45,
        act_torque_alert_pct: 55,
        act_torque: { torque_pct: -60, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('act_torque_alert'), 'act_torque_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2_temp_c: 900,
        cat_b2_alert_c: 850,
        catalyst_b2: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('cat_b2_alert'), 'cat_b2_alert')

  console.log('OK fase18-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
