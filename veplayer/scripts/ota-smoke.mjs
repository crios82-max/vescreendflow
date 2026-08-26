#!/usr/bin/env node
/**
 * OTA prod smoke: register release on /ota/ URL, list releases, rollout to outdated unit.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
const fs = await import('fs')
const path = await import('path')
const { fileURLToPath } = await import('url')

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const otaDir = path.join(root, 'senseflow/ota')
fs.mkdirSync(otaDir, { recursive: true })

async function j(p, init) {
  const r = await fetch(BASE + p, {
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
  if (!r.ok) throw new Error(`${p} → ${r.status} ${text}`)
  return body
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg)
}

const deviceId = `ota-smoke-${Date.now().toString(36)}`
const file = 'veplayer-0.13.0-smoke.apk'
const dest = path.join(otaDir, file)
// minimal fake apk bytes (devices won't install this; URL must 200)
fs.writeFileSync(dest, Buffer.from('PK\x03\x04veplayer-ota-smoke'))

async function main() {
  console.log('ota-smoke →', BASE)

  // static serve
  const apkUrl = `${BASE}/ota/${file}`
  const head = await fetch(apkUrl)
  assert(head.ok, `static /ota/ ${head.status}`)
  console.log('static ok', apkUrl)

  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'OTA smoke',
      app_version: '0.12.0',
      version_code: 14,
    }),
  })

  await j('/api/fleet/ota', {
    method: 'POST',
    body: JSON.stringify({
      version_name: '0.13.0',
      version_code: 15,
      apk_url: apkUrl,
      notes: 'ota prod smoke',
    }),
  })

  const list = await j('/api/fleet/ota/releases')
  assert(list.releases?.some((r) => r.version_code === 15), 'releases list')

  const roll = await j('/api/fleet/ota/rollout', {
    method: 'POST',
    body: JSON.stringify({ version_code: 15, silent: true }),
  })
  assert(roll.queued >= 1, 'rollout queued')
  assert(roll.device_ids.includes(deviceId), 'device in rollout')
  console.log('rollout', roll.queued, 'devices')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.12.0',
      version_code: 14,
    }),
  })
  assert(hb.ota?.update_available === true, 'update available')
  assert(hb.commands.some((c) => c.command === 'ota'), 'ota cmd pending')
  console.log('OK ota-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
