#!/usr/bin/env node
/**
 * Parking distance HUD smoke (VePlayer 0.38).
 */
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'

function evaluate(zones, reverse, warnM = 1.5, critM = 0.6, nearM = 2.5) {
  if (!reverse) return { active: false, band: 'ok', showWarn: false }
  const vals = [zones.rearL, zones.rearC, zones.rearR].filter((v) => typeof v === 'number' && v > 0)
  if (!vals.length) return { active: true, band: 'ok', showWarn: false, label: 'PDC…' }
  const closest = Math.min(...vals)
  let band = 'ok'
  if (closest <= critM) band = 'crit'
  else if (closest <= warnM) band = 'warn'
  else if (closest <= nearM) band = 'near'
  return {
    active: true,
    closestM: closest,
    band,
    showWarn: band === 'warn' || band === 'crit',
    label: `${closest.toFixed(1)} m`,
  }
}

function barFill(meters, maxM = 4) {
  if (meters == null || meters <= 0) return 0
  return Math.max(0, Math.min(1, 1 - meters / maxM))
}

function voicePhrase(st) {
  const m = st.closestM
  if (m == null) return 'Atención. Obstáculo detrás.'
  if (st.band === 'crit') return `Alto. Obstáculo muy cerca, ${m} metros.`
  return `Cuidado. Obstáculo atrás, ${m} metros.`
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
  console.log('parking-hud-smoke →', BASE)
  const idle = evaluate({ rearL: 2, rearC: 2, rearR: 2 }, false)
  assert(!idle.active, 'idle')
  const ok = evaluate({ rearL: 3.5, rearC: 3.2, rearR: 3.4 }, true)
  assert(ok.band === 'ok' && !ok.showWarn, 'ok band')
  const warn = evaluate({ rearL: 1.2, rearC: 1.1, rearR: 1.4 }, true)
  assert(warn.band === 'warn' && warn.showWarn, 'warn')
  const crit = evaluate({ rearL: 0.8, rearC: 0.4, rearR: 0.9 }, true)
  assert(crit.band === 'crit' && crit.closestM === 0.4, 'crit')
  assert(voicePhrase(crit).includes('Alto'), 'voice')
  assert(barFill(0.4) > barFill(2), 'bar fill closer')

  const deviceId = `pdc-smoke-${Date.now().toString(36)}`
  const reg = await j('/api/fleet/register', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      name: 'PDC smoke',
      app_version: '0.38.0',
      version_code: 40,
    }),
  })
  assert(reg.ok || reg.status === 201, 'register')

  const hb = await j('/api/fleet/heartbeat', {
    method: 'POST',
    body: JSON.stringify({
      device_id: deviceId,
      app_version: '0.38.0',
      version_code: 40,
      vehicle_signals: {
        gear: 'R',
        reverse: true,
        uss: { rear_l_m: 0.9, rear_c_m: 0.45, rear_r_m: 1.0 },
        source: 'obd_sim',
      },
    }),
  })
  assert(hb.ok, `hb ${hb.status}`)
  const raised = hb.body.alerts_raised || []
  assert(raised.includes('parking_crit'), `raised ${JSON.stringify(raised)}`)

  console.log('OK parking-hud-smoke ·', crit.label, '· raised', raised.filter((k) => k.startsWith('parking')))
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
