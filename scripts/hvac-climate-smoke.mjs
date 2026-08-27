#!/usr/bin/env node
/**
 * HVAC climate panel smoke (VePlayer 0.41 · Fase 7).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(cabinC, targetC, acOn, fanLevel, comfortDeltaC = 2.5) {
  if (cabinC == null && targetC == null) {
    return { band: 'idle', label: '', showPanel: false, deltaC: 0 }
  }
  const cabin = cabinC
  const target = targetC ?? cabinC
  const delta = cabin != null && target != null ? cabin - target : 0
  let band = 'unknown'
  if (cabin != null && target != null) {
    if (Math.abs(delta) <= comfortDeltaC) band = 'comfort'
    else if (delta > comfortDeltaC) band = 'heat'
    else band = 'cool'
  }
  const cabinTxt = cabin != null ? `${Math.trunc(cabin)}°` : '—'
  const targetTxt = target != null ? `${Math.trunc(target)}°` : '—'
  const ac = acOn ? 'AC' : 'AC off'
  const fan = fanLevel > 0 ? `fan ${fanLevel}` : 'fan off'
  return {
    cabinC: cabin,
    targetC: target,
    acOn,
    fanLevel,
    band,
    deltaC: delta,
    label: `${cabinTxt} → ${targetTxt} · ${ac} · ${fan}`,
    showPanel: true,
  }
}

function dockLabel(st) {
  const cabin = st.cabinC != null ? `${Math.round(st.cabinC)}°` : '—'
  return st.acOn ? `${cabin} AC` : cabin
}

function assert(c, m) {
  if (!c) throw new Error(m)
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
  console.log('hvac-climate-smoke →', BASE)
  assert(evaluate(null, null, false, 0).band === 'idle', 'idle')
  const comfort = evaluate(22.5, 22, true, 2)
  assert(comfort.band === 'comfort' && comfort.label.includes('AC'), 'comfort')
  const heat = evaluate(28, 22, false, 1)
  assert(heat.band === 'heat' && heat.deltaC === 6, 'heat')
  const cool = evaluate(18, 23, true, 3)
  assert(cool.band === 'cool', 'cool')
  assert(dockLabel(heat) === '28°', 'dock off')
  assert(dockLabel(comfort) === '23° AC' || dockLabel(comfort).includes('AC'), 'dock ac')

  const deviceId = `hvac-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'HVAC smoke',
      app_version: '0.41.0',
      version_code: 43,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.41.0',
      version_code: 43,
      vehicle_signals: {
        hvac: { cabin_c: 27.2, target_c: 22, ac_on: true, fan: 3 },
        outdoor_temp_c: 34,
        source: 'obd_sim',
      },
    }),
  })
  assert(hb.ok, `hb ${hb.status}`)

  console.log('OK hvac-climate-smoke ·', heat.label, '·', comfort.band)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
