#!/usr/bin/env node
/**
 * OEM brand smoke (VePlayer 0.81 · set_brand).
 * SenseFlow static /brands/* + fleet command queue.
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function assert(c, m) {
  if (!c) throw new Error(m)
}

function parseAccent(raw) {
  const s = String(raw || '').trim().replace(/^#/, '')
  if (s.length !== 6 && s.length !== 8) return 0xff2dd4bf
  const n = parseInt(s, 16)
  return s.length === 6 ? (0xff000000 | n) >>> 0 : n >>> 0
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
  console.log('brand-smoke →', BASE)

  assert(parseAccent('#E11D48') === 0xffe11d48, 'accent hex6')
  assert(parseAccent('E11D48') === 0xffe11d48, 'accent no hash')
  assert(parseAccent('bad') === 0xff2dd4bf, 'accent default')

  const logoUrl = `${BASE}/brands/demo/logo.png`
  const lr = await fetch(logoUrl)
  assert(lr.ok, `static logo ${lr.status}`)
  const bytes = await lr.arrayBuffer()
  assert(bytes.byteLength > 20, 'logo bytes')

  const deviceId = `brand-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Brand smoke',
      app_version: '0.81.0',
      version_code: 83,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const cmd = await j('/api/fleet/command', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      command: 'set_brand',
      payload: {
        brand_id: 'demo',
        name: 'Marca Demo',
        logo_url: logoUrl,
        accent: '#E11D48',
      },
    }),
  })
  assert(cmd.ok && cmd.body.id, `set_brand ${JSON.stringify(cmd.body)}`)
  console.log('set_brand queued', cmd.body.id)

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.81.0',
      version_code: 83,
      vehicle_signals: {
        brand: {
          brand_id: 'demo',
          name: 'Marca Demo',
          accent: 0xffe11d48,
          has_logo: true,
        },
      },
    }),
  })
  assert(hb.ok, `heartbeat ${hb.status}`)

  const clear = await j('/api/fleet/command', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      command: 'set_brand',
      payload: { clear: true },
    }),
  })
  assert(clear.ok && clear.body.id, 'set_brand clear')

  console.log('OK brand-smoke · static + set_brand + clear')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
