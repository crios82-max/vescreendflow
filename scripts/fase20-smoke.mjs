#!/usr/bin/env node
/** Fase 20 smoke — cat B2S4 + secondary O2 trims B1/B2. */
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
  console.log('fase20-smoke →', BASE)
  assert(((0x24 * 256 + 0x54) / 10) - 40 === 890, 'pid 0176')
  assert(((0xB8 - 128) * 100) / 128 === 43.75, 'pid 0155')
  assert(((0x60 - 128) * 100) / 128 === -25, 'pid 0156')
  assert(((0xA8 - 128) * 100) / 128 === 31.25, 'pid 0157')
  assert(((0x50 - 128) * 100) / 128 === -37.5, 'pid 0158')

  const deviceId = `fase20-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, name: 'Fase 20', app_version: '1.09.0', version_code: 111 }),
  })

  const hb1 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        catalyst_b2s4_temp_c: 900,
        cat_b2s4_alert_c: 850,
        catalyst_b2s4: { catalyst_temp_c: 900, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb1.body.alerts_raised || []).includes('cat_b2s4_alert'), 'cat_b2s4_alert')

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_trim_stft2_b1_pct: 25,
        speed_kmh: 45,
        stft2_b1_alert_pct: 20,
        stft2_b1: { trim_pct: 25, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb2.body.alerts_raised || []).includes('stft2_b1_alert'), 'stft2_b1_alert')

  const hb3 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_trim_ltft2_b1_pct: -25,
        speed_kmh: 45,
        ltft2_b1_alert_pct: 20,
        ltft2_b1: { trim_pct: -25, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb3.body.alerts_raised || []).includes('ltft2_b1_alert'), 'ltft2_b1_alert')

  const hb4 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_trim_stft2_b2_pct: 22,
        speed_kmh: 45,
        stft2_b2_alert_pct: 20,
        stft2_b2: { trim_pct: 22, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb4.body.alerts_raised || []).includes('stft2_b2_alert'), 'stft2_b2_alert')

  const hb5 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      vehicle_signals: {
        fuel_trim_ltft2_b2_pct: -22,
        speed_kmh: 45,
        ltft2_b2_alert_pct: 20,
        ltft2_b2: { trim_pct: -22, band: 'alert', show_warn: true },
      },
    }),
  })
  assert((hb5.body.alerts_raised || []).includes('ltft2_b2_alert'), 'ltft2_b2_alert')

  console.log('OK fase20-smoke · 5 monitors')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
