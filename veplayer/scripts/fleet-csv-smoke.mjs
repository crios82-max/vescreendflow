#!/usr/bin/env node
/**
 * Fleet CSV export smoke (VePlayer 0.22 / SenseFlow ops).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

async function raw(path, token) {
  const headers = {}
  if (token) headers['x-fleet-token'] = token
  const r = await fetch(BASE + path, { headers })
  const text = await r.text()
  return { ok: r.ok, status: r.status, headers: r.headers, text }
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

function parseCsv(text) {
  // strip BOM
  const t = text.replace(/^\uFEFF/, '')
  const lines = t.trim().split(/\n/)
  return lines.map((l) => l.split(',').map((c) => c.replace(/^"|"$/g, '')))
}

async function main() {
  console.log('fleet-csv-smoke →', BASE)

  const deviceId = `csv-smoke-${Date.now().toString(36)}`
  await fetch(BASE + '/api/fleet/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      device_id: deviceId,
      name: 'CSV smoke',
      app_version: '0.22.0',
      version_code: 24,
    }),
  })

  const bad = await raw('/api/fleet/ops/reports/export?kind=nope', 'fleet-viewer-demo')
  assert(bad.status === 400, 'bad kind')
  assert(String(bad.text).includes('kinds'), 'kinds hint')

  for (const kind of ['summary', 'devices', 'commands', 'alerts', 'telemetry']) {
    const r = await raw(
      `/api/fleet/ops/reports/export?kind=${kind}&limit=50`,
      'fleet-viewer-demo',
    )
    assert(r.ok, `${kind} ok`)
    const ct = r.headers.get('content-type') || ''
    assert(ct.includes('text/csv'), `${kind} content-type ${ct}`)
    const cd = r.headers.get('content-disposition') || ''
    assert(cd.includes('.csv'), `${kind} disposition`)
    assert(r.text.charCodeAt(0) === 0xfeff || r.text.startsWith('\uFEFF') || r.text.includes(','), 'csv body')
    const rows = parseCsv(r.text)
    assert(rows.length >= 1, `${kind} header`)
    assert(rows[0].length >= 2, `${kind} cols`)
    console.log('OK', kind, 'rows', rows.length, 'cols', rows[0].length)
  }

  const devices = await raw('/api/fleet/ops/reports/export?kind=devices', 'fleet-viewer-demo')
  assert(devices.text.includes(deviceId) || devices.text.includes('device_id'), 'has device header/id')

  console.log('OK fleet-csv-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
