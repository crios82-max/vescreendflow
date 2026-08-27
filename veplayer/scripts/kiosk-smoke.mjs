#!/usr/bin/env node
/**
 * SenseFlow-side smoke for VePlayer kiosk hardening (v0.12):
 * - OTA release row
 * - commands: ota (silent), lock_task, apply_kiosk
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

const deviceId = process.env.DEVICE_ID || `kiosk-smoke-${Date.now().toString(36)}`

async function main() {
  console.log('kiosk-smoke →', BASE)

  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Kiosk smoke',
      app_version: '0.12.0',
      version_code: 14,
    }),
  })

  const otaPost = await j('/api/fleet/ota', {
    method: 'POST',
    body: JSON.stringify({
      version_name: '0.12.0',
      version_code: 14,
      apk_url: 'https://example.com/veplayer-0.12.0.apk',
      notes: 'kiosk hardening smoke',
    }),
  })
  assert(otaPost.ok, 'ota post')

  const latest = await j('/api/fleet/ota/latest')
  assert(latest.release?.version_code >= 14, 'ota latest')

  for (const [command, payload] of [
    ['apply_kiosk', {}],
    ['lock_task', {}],
    [
      'ota',
      {
        apk_url: latest.release.apk_url,
        silent: true,
        version_code: latest.release.version_code,
      },
    ],
  ]) {
    const r = await j('/api/fleet/command', {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command, payload }),
    })
    assert(r.id || r.ok || r.command_id, `cmd ${command} → ${JSON.stringify(r)}`)
    console.log('cmd ok', command, r.id ?? r.command_id ?? r)
  }

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.11.0',
      version_code: 13,
      vehicle_signals: {
        kiosk: { device_owner: true, lock_task: true, auto_ota: true },
      },
    }),
  })
  assert(hb.ota?.update_available === true, 'heartbeat should offer OTA')
  assert(Array.isArray(hb.commands) && hb.commands.length >= 1, 'pending cmds')
  console.log('heartbeat ota', hb.ota)
  console.log('pending cmds', hb.commands.map((c) => c.command).join(','))
  console.log('OK kiosk-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
