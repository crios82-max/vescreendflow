#!/usr/bin/env node
/**
 * Phone Link / AA / CarPlay status smoke (VePlayer 0.36).
 * Validates heartbeat phone_link payload shape (device-side sim mirrors this).
 */
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
  console.log('phone-link-smoke →', BASE)
  const deviceId = `phone-link-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Phone link smoke',
      app_version: '0.36.0',
      version_code: 38,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  for (const protocol of ['android_auto', 'carplay', 'bt_media']) {
    const hb = await j('/api/fleet/heartbeat', {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        vehicle_signals: {
          phone_link: {
            connected: true,
            protocol,
            device_name: protocol === 'carplay' ? 'iPhone' : 'Pixel',
            media_title: 'Demo track',
            playing: true,
            aa_host: protocol === 'android_auto',
            carplay_host: protocol === 'carplay',
            simulated: true,
            status: `Sim · ${protocol}`,
          },
          source: 'obd_sim',
        },
      }),
    })
    assert(hb.ok, `hb ${protocol} ${hb.status}`)
    const telem = hb.body // stored on device; verify via devices list if available
  }

  const devices = await j('/api/fleet/devices')
  assert(devices.ok, 'devices')
  const mine = (devices.body.devices || []).find((d) => d.device_id === deviceId)
  assert(mine, 'device row')
  let signals = mine.vehicle_signals || mine.telemetry_json
  if (typeof signals === 'string') {
    try {
      signals = JSON.parse(signals)
    } catch {
      signals = {}
    }
  }
  const pl = signals?.phone_link
  assert(pl?.connected === true, `phone_link ${JSON.stringify(pl)}`)
  assert(
    ['android_auto', 'carplay', 'bt_media'].includes(pl.protocol),
    `protocol ${pl.protocol}`,
  )

  console.log('OK phone-link-smoke ·', pl.protocol, pl.device_name)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
