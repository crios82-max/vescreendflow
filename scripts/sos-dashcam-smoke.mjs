#!/usr/bin/env node
/**
 * SOS dashcam clip smoke (VePlayer 0.43 · Fase 7).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
const fs = await import('fs')

// Minimal valid 1x1 JPEG
const TINY_JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGfAP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Bf//Z',
  'base64',
)

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
  console.log('sos-dashcam-smoke →', BASE)
  assert(TINY_JPEG.length > 32, 'jpeg bytes')

  const deviceId = `sos-clip-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'SOS clip smoke',
      app_version: '0.43.0',
      version_code: 45,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const before = await j('/api/fleet/panic/clip', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      kind: 'jpeg',
      data_base64: TINY_JPEG.toString('base64'),
      sim: true,
    }),
  })
  assert(before.status === 409, `need panic first got ${before.status}`)

  const panic = await j('/api/fleet/panic', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      lat: 10.5,
      lng: -66.9,
      note: 'clip smoke',
      driver_code: 'D001',
    }),
  })
  assert(panic.status === 201 || panic.ok, `panic ${panic.status}`)
  const alertId = panic.body.alert?.id
  assert(alertId, 'alert id')

  const clip = await j('/api/fleet/panic/clip', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      alert_id: alertId,
      kind: 'jpeg',
      data_base64: TINY_JPEG.toString('base64'),
      camera: 'front_sim',
      duration_sec: 8,
      sim: true,
    }),
  })
  assert(clip.status === 201 && clip.body.clip_url, `clip ${clip.status} ${JSON.stringify(clip.body)}`)
  assert(String(clip.body.clip_url).startsWith('/clips/'), 'url path')

  const file = await fetch(BASE + clip.body.clip_url)
  assert(file.ok, `static ${file.status}`)
  const buf = Buffer.from(await file.arrayBuffer())
  assert(buf.length >= 32, 'served bytes')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, app_version: '0.43.0', version_code: 45 }),
  })
  assert(hb.body.panic?.open === true, 'panic open')
  assert(hb.body.panic?.clip_url === clip.body.clip_url, `hb clip ${JSON.stringify(hb.body.panic)}`)

  const open = await j('/api/fleet/alerts?open=1')
  const row = (open.body.alerts || []).find((a) => a.id === alertId)
  assert(row?.payload?.clip_url === clip.body.clip_url, 'alert payload clip')

  console.log('OK sos-dashcam-smoke ·', clip.body.clip_url, '·', buf.length, 'B')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
