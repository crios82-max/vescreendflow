#!/usr/bin/env node
/**
 * Driver profiles smoke (VePlayer 0.23).
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
  console.log('driver-smoke →', BASE)

  const list = await j('/api/fleet/drivers')
  assert(list.ok && Array.isArray(list.body.drivers), 'list drivers')
  assert(list.body.drivers.length >= 3, 'seeded drivers')
  const d001 = list.body.drivers.find((d) => d.code === 'D001')
  assert(d001 && d001.has_pin && d001.preferred_dest === 'Altamira', 'D001 seed')

  const deviceId = `drv-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Driver smoke',
      app_version: '0.23.0',
      version_code: 25,
    }),
  })

  const badPin = await j('/api/fleet/drivers/login', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, code: 'D001', pin: '0000' }),
  })
  assert(badPin.status === 401, 'bad pin')

  const login = await j('/api/fleet/drivers/login', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, code: 'D001', pin: '1234' }),
  })
  assert(login.ok && login.body.driver.code === 'D001', 'login D001')

  const cur = await j(`/api/fleet/drivers/current?device_id=${deviceId}`)
  assert(cur.body.driver?.code === 'D001', 'current')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.23.0',
      version_code: 25,
      driver_id: login.body.driver.id,
      driver_code: 'D001',
    }),
  })
  assert(hb.ok && hb.body.driver?.code === 'D001', 'heartbeat driver')

  const cmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'set_driver',
        payload: { code: 'D003' },
      }),
    },
    'fleet-dispatch-demo',
  )
  assert(cmd.ok, 'set_driver cmd')

  const cur3 = await j(`/api/fleet/drivers/current?device_id=${deviceId}`)
  assert(cur3.body.driver?.code === 'D003', 'assigned D003 via cmd')

  const ops = await j('/api/fleet/ops/drivers', {}, 'fleet-viewer-demo')
  assert(ops.ok && ops.body.drivers.length >= 3, 'ops list')

  const clear = await j(
    '/api/fleet/ops/drivers/assign',
    {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, driver_id: null }),
    },
    'fleet-dispatch-demo',
  )
  assert(clear.ok && clear.body.driver == null, 'clear assign')

  const guest = await j('/api/fleet/drivers/login', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, code: 'D003' }),
  })
  assert(guest.ok, 'guest no pin')

  console.log('OK driver-smoke ·', login.body.driver.name, '→', guest.body.driver.code)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
