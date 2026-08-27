#!/usr/bin/env node
/**
 * Fleet auth smoke (v0.18): login session, hashed API token, role gates, open-mode toggle.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

async function j(path, init = {}, headers = {}) {
  const r = await fetch(BASE + path, {
    ...init,
    headers: { 'content-type': 'application/json', ...headers, ...(init.headers || {}) },
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

function assert(c, m) {
  if (!c) throw new Error(m)
}

async function main() {
  console.log('fleet-auth-smoke →', BASE)

  const cfg = await j('/api/fleet/ops/auth/config')
  assert(typeof cfg.body.open_mode === 'boolean', 'auth config')

  const bad = await j('/api/fleet/ops/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'admin', password: 'wrong' }),
  })
  assert(bad.status === 401, 'bad password')

  const login = await j('/api/fleet/ops/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  assert(login.ok && login.body.session, 'admin login')
  const session = login.body.session

  const me = await j('/api/fleet/ops/me', {}, { 'x-fleet-session': session })
  assert(me.body.authenticated === true && me.body.role === 'admin', 'session me')

  const tokMe = await j('/api/fleet/ops/me', {}, { 'x-fleet-token': 'fleet-admin-demo' })
  assert(tokMe.body.authenticated === true, 'hashed api token still works')

  const deviceId = `auth-smoke-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Auth smoke',
      app_version: '0.18.0',
      version_code: 20,
    }),
  })

  const viewerLogin = await j('/api/fleet/ops/login', {
    method: 'POST',
    body: JSON.stringify({ username: 'viewer', password: 'viewer123' }),
  })
  const vSession = viewerLogin.body.session
  const denied = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({ device_id: deviceId, command: 'restart' }),
    },
    { 'x-fleet-session': vSession },
  )
  assert(denied.status === 403, 'viewer cannot restart')

  const ok = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'message',
        payload: { text: 'auth ok' },
      }),
    },
    { 'x-fleet-session': session },
  )
  assert(ok.ok && ok.body.id, 'admin message via session')

  const logout = await j('/api/fleet/ops/logout', { method: 'POST' }, { 'x-fleet-session': session })
  assert(logout.ok, 'logout')

  console.log('OK fleet-auth-smoke · open_mode=', cfg.body.open_mode)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
