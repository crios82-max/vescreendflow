#!/usr/bin/env node
/**
 * Incident report smoke (VePlayer 0.48 · Fase 8).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

const TINY_JPEG = Buffer.from(
  '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGfAP/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAQUCf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQMBAT8Bf//EABQRAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQIBAT8Bf//Z',
  'base64',
)

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

function assert(c, m) {
  if (!c) throw new Error(m)
}

async function main() {
  console.log('incident-report-smoke →', BASE)
  const deviceId = `inc-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Incident smoke',
      app_version: '0.48.0',
      version_code: 50,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const inc = await j('/api/fleet/incident', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      category: 'breakdown',
      note: 'Humo en motor — smoke test',
      lat: 10.49,
      lng: -66.88,
      driver_code: 'D001',
      driver_name: 'Carlos',
      clip_kind: 'jpeg',
      clip_base64: TINY_JPEG.toString('base64'),
      clip_sim: true,
    }),
  })
  assert(inc.status === 201 || inc.ok, `incident ${inc.status} ${JSON.stringify(inc.body)}`)
  assert(inc.body.alert?.kind === 'incident', 'kind')
  assert(inc.body.alert?.severity === 'warn', 'severity warn')
  assert(String(inc.body.alert?.message || '').includes('Avería'), 'label')
  assert(inc.body.alert?.clip_url, 'clip_url')
  const alertId = inc.body.alert.id

  const file = await fetch(BASE + inc.body.alert.clip_url)
  assert(file.ok, `static clip ${file.status}`)

  const dup = await j('/api/fleet/incident', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      category: 'traffic',
      note: 'dup soon',
    }),
  })
  assert(dup.body.deduped === true, 'soft dedupe')

  const open = await j('/api/fleet/alerts?open=1')
  assert(
    (open.body.alerts || []).some((a) => a.id === alertId && a.kind === 'incident'),
    'open list',
  )

  // After cooldown window we'd allow another — accident is critical
  // Skip waiting; just verify accident category on a fresh device
  const device2 = `inc-acc-${Date.now().toString(36)}`
  await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: device2,
      name: 'Accident smoke',
      app_version: '0.48.0',
      version_code: 50,
    }),
  })
  const acc = await j('/api/fleet/incident', {
    method: 'POST',
    body: JSON.stringify({
      device_id: device2,
      category: 'accident',
      note: 'Colisión leve',
    }),
  })
  assert(acc.body.alert?.severity === 'critical', 'accident critical')

  console.log('OK incident-report-smoke ·', alertId, '·', inc.body.alert.clip_url)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
