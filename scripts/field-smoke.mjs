#!/usr/bin/env node
/**
 * Field commissioning smoke (SenseFlow side) for VePlayer 0.13.
 * Registers a unit, queues apply_kiosk + run_diag, checks heartbeat accepts field payload.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

async function j(path, init) {
  const r = await fetch(BASE + path, {
    headers: { 'content-type': 'application/json', ...(init?.headers || {}) },
    ...init,
  })
  const text = await r.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  if (!r.ok) throw new Error(`${path} → ${r.status} ${text}`)
  return body
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg)
}

const deviceId = process.env.DEVICE_ID || `field-smoke-${Date.now().toString(36)}`

async function main() {
  console.log('field-smoke →', BASE, 'device', deviceId)

  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Campo HU-01',
      app_version: '0.13.0',
      version_code: 15,
    }),
  })

  for (const [command, payload] of [
    ['apply_kiosk', {}],
    ['lock_task', {}],
    ['run_diag', {}],
  ]) {
    const r = await j('/api/fleet/command', {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command, payload }),
    })
    assert(r.id, `cmd ${command}`)
    console.log('cmd', command, r.id)
  }

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.13.0',
      version_code: 15,
      lat: 10.496,
      lng: -66.898,
      speed_mps: 0,
      vehicle_signals: {
        source: 'can',
        field: {
          package: 'com.veplayer.app',
          sense_ok: true,
          cams: 2,
          usb: 1,
          device_owner: true,
          lock_task: true,
        },
      },
    }),
  })
  assert(hb.ok, 'heartbeat')
  assert(hb.commands.some((c) => c.command === 'run_diag'), 'run_diag pending')
  console.log('pending', hb.commands.map((c) => c.command).join(','))
  console.log('OK field-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
