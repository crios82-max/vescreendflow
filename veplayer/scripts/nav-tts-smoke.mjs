#!/usr/bin/env node
/**
 * Nav TTS guidance math smoke (VePlayer 0.20).
 * Mirrors NavGuidance.kt — phrases, thresholds, step index.
 */

const THRESHOLDS = [800, 400, 150, 50]

function haversineM(a, b) {
  const R = 6371000
  const toR = (d) => (d * Math.PI) / 180
  const dLat = toR(b.lat - a.lat)
  const dLng = toR(b.lng - a.lng)
  const x =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toR(a.lat)) * Math.cos(toR(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(x))
}

function progressAlong(path, ego) {
  let best = Infinity
  let bestIdx = 0
  path.forEach((p, i) => {
    const d = haversineM(ego, p)
    if (d < best) {
      best = d
      bestIdx = i
    }
  })
  let total = 0
  let done = 0
  for (let i = 0; i < path.length - 1; i++) {
    const seg = haversineM(path[i], path[i + 1])
    total += seg
    if (i < bestIdx) done += seg
  }
  return total <= 0 ? 0 : done / total
}

function currentStepIndex(route, ego) {
  if (!route.steps.length) return -1
  const path = route.geometry
  const progress = path.length >= 2 ? progressAlong(path, ego) : 0
  const traveled = progress * Math.max(0, route.distanceM)
  let cum = 0
  for (let i = 0; i < route.steps.length; i++) {
    cum += route.steps[i].distanceM
    if (traveled < cum - 8) return i
  }
  return route.steps.length - 1
}

function remainOnStepM(route, ego, stepIndex) {
  if (stepIndex < 0 || stepIndex >= route.steps.length) return 0
  const path = route.geometry
  const progress = path.length >= 2 ? progressAlong(path, ego) : 0
  const traveled = progress * Math.max(0, route.distanceM)
  let before = 0
  for (let i = 0; i < stepIndex; i++) before += route.steps[i].distanceM
  const into = Math.max(0, traveled - before)
  return Math.max(0, route.steps[stepIndex].distanceM - into)
}

function formatDistanceM(m) {
  if (m >= 1000) {
    const tenths = Math.round(m / 100)
    if (tenths % 10 === 0) {
      const km = tenths / 10
      return km === 1 ? '1 kilómetro' : `${km} kilómetros`
    }
    return `${Math.floor(tenths / 10)},${tenths % 10} kilómetros`
  }
  const rounded = Math.max(10, Math.round(m / 10) * 10)
  return `${rounded} metros`
}

function phraseFor(instruction, remainM) {
  const instr = instruction.trim().replace(/\.$/, '')
  if (remainM <= 55) return `${instr}.`
  const lower = instr.charAt(0).toLowerCase() + instr.slice(1)
  return `En ${formatDistanceM(remainM)}, ${lower}.`
}

function nextCue(route, stepIndex, remainM, spoken, destKey) {
  if (stepIndex < 0 || stepIndex >= route.steps.length) return null
  const step = route.steps[stepIndex]
  const instr = (step.instruction || 'Continuar').trim()

  if (step.type === 'arrive' || /llegaste/i.test(instr)) {
    const key = `arrive:${destKey}`
    if (spoken.has(key)) return null
    if (remainM > 80 && stepIndex < route.steps.length - 1) return null
    return {
      key,
      phrase: route.destinationName
        ? `Llegaste a ${route.destinationName}.`
        : 'Llegaste al destino.',
    }
  }

  const band = Math.min(...THRESHOLDS.filter((t) => remainM <= t))
  if (!Number.isFinite(band)) {
    if (remainM > THRESHOLDS[0]) {
      const key = `step:${destKey}:${stepIndex}:start`
      if (spoken.has(key)) return null
      return { key, phrase: phraseFor(instr, remainM) }
    }
    return null
  }
  const key = `step:${destKey}:${stepIndex}:t${band}`
  if (spoken.has(key)) return null
  return { key, phrase: phraseFor(instr, remainM), band }
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

const route = {
  destinationName: 'Altamira',
  distanceM: 3500,
  durationS: 600,
  geometry: [
    { lat: 10.496, lng: -66.898 },
    { lat: 10.4962, lng: -66.88 },
    { lat: 10.4965, lng: -66.8492 },
  ],
  steps: [
    { instruction: 'Girá a la derecha', distanceM: 2000, type: 'turn', modifier: 'right' },
    { instruction: 'Girá a la izquierda', distanceM: 1400, type: 'turn', modifier: 'left' },
    { instruction: 'Llegaste al destino', distanceM: 100, type: 'arrive', modifier: '' },
  ],
}

assert(formatDistanceM(200) === '200 metros', 'fmt 200')
assert(formatDistanceM(1500).includes('kilómetro'), 'fmt km')
assert(phraseFor('Girá a la izquierda', 200).startsWith('En 200 metros'), 'phrase mid')
assert(phraseFor('Girá a la izquierda', 40) === 'Girá a la izquierda.', 'phrase near')

const egoStart = route.geometry[0]
const idx0 = currentStepIndex(route, egoStart)
assert(idx0 === 0, `step0 got ${idx0}`)
const remain0 = remainOnStepM(route, egoStart, 0)
assert(remain0 > 1500, `remain0 ${remain0}`)

const spoken = new Set()
const destKey = 'Altamira:3500'
const cueFar = nextCue(route, 0, remain0, spoken, destKey)
assert(cueFar && cueFar.key.includes(':start'), `far cue ${cueFar?.key}`)
spoken.add(cueFar.key)

const cueMid = nextCue(route, 0, 350, spoken, destKey)
assert(cueMid && cueMid.band === 400, `mid ${cueMid?.key}`)
spoken.add(cueMid.key)
const again = nextCue(route, 0, 350, spoken, destKey)
assert(again == null, 'dedupe')

const cueNear = nextCue(route, 0, 40, spoken, destKey)
assert(cueNear && cueNear.band === 50, `near ${cueNear?.key}`)

const egoEnd = route.geometry[2]
const idxEnd = currentStepIndex(route, egoEnd)
assert(idxEnd >= 1, `end step ${idxEnd}`)

console.log('OK nav-tts ·', cueFar.phrase)
console.log('OK', cueMid.phrase)
console.log('OK nav-tts-smoke')
