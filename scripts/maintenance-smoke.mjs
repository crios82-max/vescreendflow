#!/usr/bin/env node
/**
 * Odometer maintenance smoke (VePlayer 0.28) — math + SenseFlow API.
 */

function evaluate(item, odoKm) {
  const dueAt = item.last_service_odo_km + item.interval_km
  if (!item.enabled) {
    return {
      ...item,
      odo_km: odoKm ?? null,
      due_at_km: dueAt,
      remaining_km: odoKm != null ? dueAt - odoKm : null,
      band: 'off',
    }
  }
  if (odoKm == null || !Number.isFinite(odoKm)) {
    return { ...item, odo_km: null, due_at_km: dueAt, remaining_km: null, band: 'ok' }
  }
  const remaining = dueAt - odoKm
  let band = 'ok'
  if (remaining <= 0) band = 'due'
  else if (remaining <= item.warn_km) band = 'warn'
  return { ...item, odo_km: odoKm, due_at_km: dueAt, remaining_km: remaining, band }
}

function voicePhrase(st) {
  const label = st.label
  if (st.band === 'due') {
    const over = Math.round(Math.abs(st.remaining_km || 0))
    return `Mantenimiento vencido: ${label}. ${over} kilómetros de atraso.`
  }
  if (st.band === 'warn') {
    const rem = Math.round(st.remaining_km || 0)
    return `Próximo servicio: ${label} en ${rem} kilómetros.`
  }
  return `Servicio ${label} al día.`
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

const oil = {
  kind: 'oil',
  label: 'Aceite',
  interval_km: 5000,
  last_service_odo_km: 10000,
  warn_km: 500,
  enabled: true,
}
assert(evaluate(oil, 14000).band === 'ok', 'ok')
assert(evaluate(oil, 14600).band === 'warn', 'warn')
assert(evaluate(oil, 15000).band === 'due', 'due')
assert(evaluate(oil, 15200).remaining_km === -200, 'over')
assert(voicePhrase(evaluate(oil, 15200)).includes('vencido'), 'phrase due')
assert(voicePhrase(evaluate(oil, 14600)).includes('Próximo'), 'phrase warn')

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

async function main() {
  console.log('maintenance-smoke →', BASE)
  const deviceId = `maint-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Maint smoke',
      app_version: '0.28.0',
      version_code: 30,
    }),
  })
  assert(reg.ok, 'register')

  const list = await j(`/api/fleet/maintenance?device_id=${deviceId}&odo_km=12450`)
  assert(list.ok && list.body.items?.length >= 5, 'seeded items')
  assert(list.body.items.every((i) => i.band === 'ok' || i.band === 'off'), 'fresh ok')

  // Push oil near due: last=8000, interval=5000 → due at 13000; odo 12800 = warn
  const put = await j('/api/fleet/maintenance', {
    method: 'PUT',
    body: JSON.stringify({
      device_id: deviceId,
      kind: 'oil',
      last_service_odo_km: 8000,
      interval_km: 5000,
      warn_km: 500,
    }),
  })
  assert(put.ok, 'put oil')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.28.0',
      version_code: 30,
      odo_km: 12800,
      vehicle_signals: { odometer_km: 12800, speed_mps: 5 },
    }),
  })
  assert(hb.ok, 'hb')
  assert(hb.body.maintenance?.warn >= 1, `warn ${hb.body.maintenance?.warn}`)
  assert(
    (hb.body.alerts_raised || []).some((k) => String(k).startsWith('maint_warn:oil')),
    'raised warn',
  )

  const hbDue = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      odo_km: 13100,
      vehicle_signals: { odometer_km: 13100 },
    }),
  })
  assert(hbDue.body.maintenance?.due >= 1, 'due')
  assert(
    (hbDue.body.alerts_raised || []).some((k) => String(k).startsWith('maint_due:oil')),
    'raised due',
  )

  const svc = await j('/api/fleet/maintenance/service', {
    method: 'POST',
    body: JSON.stringify({ device_id: deviceId, kind: 'oil', odo_km: 13100 }),
  })
  assert(svc.ok && svc.body.item.band === 'ok', 'service resets')

  const cmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'service_done',
        payload: { kind: 'tires', odo_km: 13100 },
      }),
    },
    'fleet-dispatcher-demo',
  )
  assert(cmd.ok, `cmd ${JSON.stringify(cmd.body)}`)

  const ops = await j('/api/fleet/ops/maintenance?device_id=' + deviceId, {}, 'fleet-viewer-demo')
  assert(ops.ok && Array.isArray(ops.body.items), 'ops list')

  const csv = await fetch(BASE + '/api/fleet/ops/reports/export?kind=maintenance&limit=50', {
    headers: { 'x-fleet-token': 'fleet-viewer-demo' },
  })
  assert(csv.ok, 'csv')
  const text = await csv.text()
  assert(text.includes('remaining_km') && text.includes('oil'), 'csv header/rows')

  console.log('OK maintenance-smoke · due cleared after service')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
