#!/usr/bin/env node
/**
 * Seatbelt HUD smoke (VePlayer 0.44 · Fase 8).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(buckled, speedKmh, reverse = false, warnKmh = 5, alertKmh = 15) {
  if (buckled) return { band: 'ok', showWarn: false, label: '' }
  let band = 'unlatched'
  if (reverse || speedKmh >= alertKmh) band = 'alert'
  else if (speedKmh >= warnKmh) band = 'warn'
  return {
    buckled: false,
    band,
    showWarn: band === 'warn' || band === 'alert',
    label: 'Cinturón',
  }
}

function voicePhrase(st) {
  if (st.band === 'alert') return 'Atención. Abróchate el cinturón. Vehículo en movimiento.'
  if (st.band === 'warn') return 'Cuidado. Cinturón desabrochado.'
  return 'Cinturón desabrochado.'
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
  console.log('seatbelt-smoke →', BASE)
  assert(evaluate(true, 40).band === 'ok', 'ok')
  assert(evaluate(false, 0).band === 'unlatched' && !evaluate(false, 0).showWarn, 'unlatched')
  assert(evaluate(false, 8).band === 'warn' && evaluate(false, 8).showWarn, 'warn')
  assert(evaluate(false, 20).band === 'alert' && voicePhrase(evaluate(false, 20)).includes('Abróchate'), 'alert')
  assert(evaluate(false, 0, true).band === 'alert', 'reverse')

  const deviceId = `belt-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'Seatbelt smoke',
      app_version: '0.44.0',
      version_code: 46,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hbWarn = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.44.0',
      version_code: 46,
      vehicle_signals: {
        speed_mps: 0,
        gear: 'P',
        seatbelt_driver: false,
      },
    }),
  })
  assert(hbWarn.ok, `hb warn ${hbWarn.status}`)
  const raisedWarn = hbWarn.body.alerts_raised || []
  assert(raisedWarn.includes('seatbelt_warn'), `warn ${JSON.stringify(raisedWarn)}`)

  const hbAlert = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.44.0',
      version_code: 46,
      vehicle_signals: {
        speed_mps: 12,
        gear: 'D',
        seatbelt_driver: false,
      },
    }),
  })
  assert(hbAlert.ok, `hb alert ${hbAlert.status}`)
  const raisedAlert = hbAlert.body.alerts_raised || []
  assert(raisedAlert.includes('seatbelt_alert'), `alert ${JSON.stringify(raisedAlert)}`)

  console.log(
    'OK seatbelt-smoke · raised',
    [...raisedWarn, ...raisedAlert].filter((k) => k.startsWith('seatbelt')),
  )
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
