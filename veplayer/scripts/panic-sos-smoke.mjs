#!/usr/bin/env node
/**
 * Panic / SOS smoke (VePlayer 0.31).
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
  console.log('panic-sos-smoke →', BASE)
  const deviceId = `panic-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Panic smoke',
      app_version: '0.31.0',
      version_code: 33,
    }),
  })
  assert(reg.ok, 'register')

  const panic = await j('/api/fleet/panic', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.496,
      lng: -66.898,
      note: 'smoke test',
      driver_code: 'D001',
      driver_name: 'Carlos',
    }),
  })
  assert(panic.status === 201 || panic.ok, `panic ${panic.status}`)
  assert(panic.body.alert?.severity === 'critical' || panic.body.alert?.kind === 'panic', 'critical')
  assert(String(panic.body.alert?.message || '').includes('SOS'), 'message')
  const alertId = panic.body.alert.id

  const dup = await j('/api/fleet/panic', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, note: 'dup' }),
  })
  assert(dup.body.deduped === true, 'dedupe')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.31.0',
      version_code: 33,
      vehicle_signals: { speed_mps: 0 },
    }),
  })
  assert(hb.body.panic?.open === true, 'hb panic open')
  assert(hb.body.panic?.id === alertId, 'hb panic id')

  const open = await j('/api/fleet/alerts?open=1')
  assert(
    (open.body.alerts || []).some((a) => a.kind === 'panic' && a.device_id === deviceId),
    'open list',
  )
  // critical first
  const first = open.body.alerts[0]
  assert(first.severity === 'critical' || first.kind === 'panic', 'sorted critical')

  const cmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command: 'panic_ack', payload: {} }),
    },
    'fleet-dispatcher-demo',
  )
  assert(cmd.ok, `cmd ${JSON.stringify(cmd.body)}`)

  const hb2 = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId }),
  })
  assert(hb2.body.panic?.open === false, 'acked closed')

  console.log('OK panic-sos-smoke · alert', alertId)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
