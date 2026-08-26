#!/usr/bin/env node
/**
 * Speed HUD math smoke (VePlayer 0.26).
 */

function evaluate(speedKmh, limitKmh, warnMarginKmh = 5) {
  const limit = Math.max(10, Math.min(160, limitKmh))
  const speed = Math.max(0, speedKmh)
  const overBy = speed - limit
  let band = 'ok'
  if (overBy > 0) band = 'over'
  else if (overBy > -warnMarginKmh) band = 'near'
  return { speedKmh: speed, limitKmh: limit, overBy, band, showWarn: overBy > 0 }
}

function voicePhrase(state) {
  const lim = state.limitKmh
  const spd = Math.floor(state.speedKmh)
  if (state.overBy >= 20) return `Exceso grave de velocidad. Vas a ${spd}, límite ${lim}.`
  if (state.overBy > 0) return `Reduce velocidad. Límite ${lim}, vas a ${spd}.`
  return `Límite de velocidad ${lim} kilómetros por hora.`
}

function accentArgb(band) {
  if (band === 'over') return 0xe11d48
  if (band === 'near') return 0xf59e0b
  return 0xe8f2ee
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

const ok = evaluate(42, 50)
assert(ok.band === 'ok' && !ok.showWarn, 'ok')
const near = evaluate(47, 50)
assert(near.band === 'near', 'near')
const over = evaluate(58, 50)
assert(over.band === 'over' && over.showWarn && over.overBy === 8, 'over')
const grave = evaluate(80, 50)
assert(voicePhrase(grave).includes('grave'), 'grave phrase')
assert(voicePhrase(over).startsWith('Reduce'), 'reduce')
assert(accentArgb('over') === 0xe11d48, 'color')

console.log('OK speed-hud ·', voicePhrase(over))
console.log('OK speed-hud-smoke')
