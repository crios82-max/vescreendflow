#!/usr/bin/env node
/**
 * Fleet ops smoke: roles, reports, command/OTA history (VePlayer 0.17).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

async function j(path, init = {}, token) {
  const headers = { 'content-type': 'application/json', ...(init.headers || {}) }
  if (token) headers['x-fleet-token'] = token
  const r = await fetch(BASE + path, { ...init, headers })
  const text = await r.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { ok: r.ok, status: r.status, body }
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

async function main() {
  console.log('fleet-ops-smoke →', BASE)

  const meV = await j('/api/fleet/ops/me', {}, 'fleet-viewer-demo')
  assert(meV.body.role === 'viewer', 'viewer role')
  assert(meV.body.can_dispatch === false, 'viewer no dispatch')

  const meA = await j('/api/fleet/ops/me', {}, 'fleet-admin-demo')
  assert(meA.body.role === 'admin' && meA.body.can_wipe, 'admin wipe')

  const deviceId = `ops-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Ops smoke',
      app_version: '0.17.0',
      version_code: 19,
    }),
  })

  const denied = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command: 'restart' }),
    },
    'fleet-viewer-demo',
  )
  assert(denied.status === 403, 'viewer cannot restart')

  const wipeDenied = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command: 'wipe' }),
    },
    'fleet-dispatch-demo',
  )
  assert(wipeDenied.status === 403, 'dispatcher cannot wipe')

  const okCmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'message',
        payload: { text: 'ops smoke' },
      }),
    },
    'fleet-dispatch-demo',
  )
  assert(okCmd.ok && okCmd.body.id, 'dispatcher message')
  assert(String(okCmd.body.issued_by || '').includes('dispatcher'), 'issued_by')

  const hist = await j('/api/fleet/ops/commands/history?limit=20', {}, 'fleet-viewer-demo')
  assert(hist.body.commands?.some((c) => c.id === okCmd.body.id), 'history has cmd')

  const rep = await j('/api/fleet/ops/reports/summary', {}, 'fleet-viewer-demo')
  assert(rep.body.fleet?.devices >= 1, 'report devices')
  assert(rep.body.versions, 'report versions')

  // OTA history endpoint
  const otaH = await j('/api/fleet/ops/ota/history', {}, 'fleet-admin-demo')
  assert(Array.isArray(otaH.body.releases), 'ota releases list')

  console.log('OK fleet-ops-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
