#!/usr/bin/env node
/**
 * Fuel / range HUD math + SenseFlow alert smoke (VePlayer 0.29).
 */

function evaluate(
  fuelPct,
  socPct,
  rangeKm,
  warnPct = 20,
  criticalPct = 10,
  warnRangeKm = 40,
  criticalRangeKm = 20,
) {
  const kind = fuelPct != null ? 'fuel' : socPct != null ? 'soc' : 'none'
  const level =
    kind === 'fuel' ? Math.max(0, Math.min(100, fuelPct)) : kind === 'soc' ? Math.max(0, Math.min(100, socPct)) : null
  const range = rangeKm != null ? Math.max(0, rangeKm) : null
  let levelBand = 'ok'
  if (level != null) {
    if (level <= criticalPct) levelBand = 'low'
    else if (level <= warnPct) levelBand = 'near'
  }
  let rangeBand = 'ok'
  if (range != null) {
    if (range <= criticalRangeKm) rangeBand = 'low'
    else if (range <= warnRangeKm) rangeBand = 'near'
  }
  let band = 'ok'
  if (levelBand === 'low' || rangeBand === 'low') band = 'low'
  else if (levelBand === 'near' || rangeBand === 'near') band = 'near'
  let reason = 'none'
  if (levelBand !== 'ok' && rangeBand !== 'ok') reason = 'both'
  else if (levelBand !== 'ok') reason = 'level'
  else if (rangeBand !== 'ok') reason = 'range'
  return {
    kind,
    levelPct: level,
    rangeKm: range,
    band,
    showWarn: band === 'low',
    reason,
  }
}

function voicePhrase(state) {
  const pct = state.levelPct != null ? Math.floor(state.levelPct) : null
  const rng = state.rangeKm != null ? Math.floor(state.rangeKm) : null
  const label = state.kind === 'fuel' ? 'combustible' : 'batería'
  if (state.band === 'low' && state.reason === 'both' && pct != null && rng != null) {
    return `Nivel crítico de ${label}: ${pct} por ciento. Autonomía ${rng} kilómetros.`
  }
  if (state.band === 'low' && state.reason === 'range' && rng != null) {
    return `Autonomía crítica: ${rng} kilómetros.`
  }
  if (state.band === 'low' && pct != null) return `Nivel crítico de ${label}: ${pct} por ciento.`
  if (state.band === 'near' && pct != null) return `Nivel bajo de ${label}: ${pct} por ciento.`
  if (state.band === 'near' && rng != null) return `Autonomía baja: ${rng} kilómetros.`
  if (pct != null) return `${label} ${pct} por ciento.`
  return 'Energía del vehículo.'
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

const ok = evaluate(55, null, 180)
assert(ok.band === 'ok' && !ok.showWarn, 'ok')
const near = evaluate(15, null, 100)
assert(near.band === 'near' && near.kind === 'fuel', 'near fuel')
const low = evaluate(8, null, 100)
assert(low.band === 'low' && low.showWarn, 'low fuel')
const socNear = evaluate(null, 18, 50)
assert(socNear.kind === 'soc' && socNear.band === 'near', 'soc near')
const rangeLow = evaluate(50, null, 15)
assert(rangeLow.band === 'low' && rangeLow.reason === 'range', 'range low')
assert(voicePhrase(low).includes('crítico'), 'phrase')
assert(voicePhrase(rangeLow).includes('Autonomía crítica'), 'range phrase')

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
  console.log('fuel-hud-smoke →', BASE)
  const deviceId = `fuel-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Fuel smoke',
      app_version: '0.29.0',
      version_code: 31,
    }),
  })
  assert(reg.ok, 'register')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.29.0',
      version_code: 31,
      vehicle_signals: { fuel_pct: 8, range_km: 18, speed_mps: 2 },
    }),
  })
  assert(hb.ok, 'hb')
  const raised = hb.body.alerts_raised || []
  assert(raised.includes('fuel_low'), `fuel_low ${JSON.stringify(raised)}`)
  assert(raised.includes('range_low'), `range_low ${JSON.stringify(raised)}`)

  const cmd = await j(
    '/api/fleet/command',
    {
      method: 'POST',
      body: JSON.stringify({
        device_id: deviceId,
        command: 'set_fuel_warn',
        payload: { pct: 18, range_km: 45 },
      }),
    },
    'fleet-dispatcher-demo',
  )
  assert(cmd.ok, `cmd ${JSON.stringify(cmd.body)}`)

  console.log('OK fuel-hud ·', voicePhrase(low))
  console.log('OK fuel-hud-smoke')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
