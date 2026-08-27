#!/usr/bin/env node
/**
 * FM band math + SimFmTuner seek smoke (mirrors Kotlin FmFreq / SimFmTuner).
 */

const ITU2 = { min: 87500, max: 108000, step: 200 }

function snap(khz, region = ITU2) {
  const clamped = Math.min(region.max, Math.max(region.min, khz))
  const steps = Math.round((clamped - region.min) / region.step)
  return Math.min(region.max, Math.max(region.min, region.min + steps * region.step))
}

function step(khz, up, region = ITU2) {
  let next = snap(khz, region) + (up ? region.step : -region.step)
  if (next > region.max) next = region.min
  if (next < region.min) next = region.max
  return next
}

function formatMhz(khz) {
  return (khz / 1000).toFixed(1) + ' MHz'
}

const presets = [
  { id: 'fm-955', name: 'La Mega', freqKhz: 95500 },
  { id: 'fm-917', name: 'Éxitos', freqKhz: 91700 },
  { id: 'fm-997', name: 'RCR', freqKhz: 99700 },
  { id: 'fm-1053', name: 'Hot', freqKhz: 105300 },
]

function seek(freq, up) {
  const sorted = presets.map((p) => p.freqKhz).sort((a, b) => a - b)
  if (up) return sorted.find((f) => f > freq) ?? sorted[0]
  return [...sorted].reverse().find((f) => f < freq) ?? sorted[sorted.length - 1]
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

assert(snap(95499) === 95500, 'snap 95.5')
assert(snap(95300) === 95300 || snap(95300) === 95300, 'snap mid')
assert(formatMhz(95500) === '95.5 MHz', 'label')
assert(step(108000, true) === 87500, 'wrap up')
assert(step(87500, false) === 108000, 'wrap down')

let f = 95500
f = seek(f, true)
assert(f === 99700, `seek up from 95.5 got ${f}`)
f = seek(f, true)
assert(f === 105300, `seek up got ${f}`)
f = seek(f, true)
assert(f === 91700, `seek wrap got ${f}`)

console.log('OK fm-smoke', presets.length, 'presets ·', formatMhz(95500))

const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
try {
  const deviceId = `fm-smoke-${Date.now().toString(36)}`
  await fetch(BASE + '/api/fleet/register', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      device_id: deviceId,
      name: 'FM smoke',
      app_version: '0.15.0',
      version_code: 17,
    }),
  })
  const r = await fetch(BASE + '/api/fleet/command', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      device_id: deviceId,
      command: 'fm_tune',
      payload: { mhz: 95.5 },
    }),
  })
  const j = await r.json()
  if (r.ok && j.id) console.log('fm_tune queued', j.id)
  else console.log('SenseFlow cmd skip', j)
} catch {
  console.log('SenseFlow not up — local smoke only')
}
