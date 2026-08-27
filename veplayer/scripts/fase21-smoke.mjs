#!/usr/bin/env node
/** Fase 21 smoke — cat B1S5/B2S5, inject timing, hybrid batt, ref torque. */
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
  console.log('fase21-smoke →', BASE)
  assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0177')
  assert((0x14 * 256) / 128 === 40, 'pid 015D')
  assert((0x28 * 100) / 255 < 15, 'pid 015B')
  assert((0x02 * 256 + 0x26) === 550, 'pid 0163')

  const deviceId = `fase21-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 21', app_version: '1.14.0', version_code: 116 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b1s5_temp_c: 900,
        cat_b1s5_alert_c: 850,
        catalyst_b1s5: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b1s5_alert'), 'cat_b1s5_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s5_temp_c: 900,
        cat_b2s5_alert_c: 850,
        catalyst_b2s5: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('cat_b2s5_alert'), 'cat_b2s5_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_inject_timing_deg: 45,
        speed_kmh: 45,
        inject_alert_deg: 40,
        fuel_inject: { timing_deg: 45, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('inject_alert'), 'inject_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        hybrid_batt_life_pct: 10,
        speed_kmh: 5,
        hybrid_alert_pct: 15,
        hybrid_batt: { life_pct: 10, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('hybrid_batt_alert'), 'hybrid_batt_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        engine_ref_torque_nm: 550,
        ref_torque_alert_high_nm: 520,
        ref_torque: { torque_nm: 550, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('ref_torque_alert'), 'ref_torque_alert')

  console.log('OK fase21-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
