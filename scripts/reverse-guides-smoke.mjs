#!/usr/bin/env node
/**
 * Reverse parking guidelines math smoke (VePlayer 0.21).
 * Mirrors ReverseGuidelines.kt
 */

function compute(steeringDeg = 0, trackWidth = 0.46, farWidth = 0.22, depth = 0.78, segments = 28, curveGain = 0.28) {
  const steer = Math.max(-45, Math.min(45, steeringDeg)) / 45
  const halfNear = Math.max(0.12, Math.min(0.4, trackWidth / 2))
  const halfFar = Math.max(0.05, Math.min(halfNear, farWidth / 2))
  const d = Math.max(0.45, Math.min(0.92, depth))
  const n = segments

  function sample(side) {
    const pts = []
    for (let i = 0; i <= n; i++) {
      const t = i / n
      const y = 1 - t * d
      const half = halfNear + (halfFar - halfNear) * t
      const bend = steer * curveGain * t * t
      const x = Math.max(0.02, Math.min(0.98, 0.5 + side * half + bend))
      pts.push({ x, y })
    }
    return pts
  }

  const bands = [0.22, 0.45, 0.72].map((t) => {
    const y = 1 - t * d
    const half = halfNear + (halfFar - halfNear) * t
    const bend = steer * curveGain * t * t
    return {
      a: { x: Math.max(0.02, Math.min(0.98, 0.5 - half + bend)), y },
      b: { x: Math.max(0.02, Math.min(0.98, 0.5 + half + bend)), y },
    }
  })

  return { left: sample(-1), right: sample(1), center: sample(0), bands }
}

function farBend(steeringDeg, curveGain = 0.28) {
  return (Math.max(-45, Math.min(45, steeringDeg)) / 45) * curveGain
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

const straight = compute(0)
assert(straight.left.length === 29, 'segments')
assert(straight.left[0].y > straight.left.at(-1).y, 'bottom to top')
assert(straight.left[0].x < 0.5 && straight.right[0].x > 0.5, 'rails at bumper')
assert(Math.abs(straight.center.at(-1).x - 0.5) < 0.02, 'center far ~0.5')

const leftSteer = compute(30)
const rightSteer = compute(-30)
assert(leftSteer.center.at(-1).x > straight.center.at(-1).x, 'left steer bends +x')
assert(rightSteer.center.at(-1).x < straight.center.at(-1).x, 'right steer bends -x')
assert(farBend(45) > 0.2 && farBend(-45) < -0.2, 'farBend')

const wide = compute(0, 0.56)
assert(wide.right[0].x - wide.left[0].x > straight.right[0].x - straight.left[0].x, 'wider track')

assert(straight.bands.length === 3, '3 distance bands')
assert(straight.bands[0].b.x - straight.bands[0].a.x > straight.bands[2].b.x - straight.bands[2].a.x, 'perspective taper')

console.log(
  'OK reverse-guides · bend@30°',
  leftSteer.center.at(-1).x.toFixed(3),
  '· track',
  (straight.right[0].x - straight.left[0].x).toFixed(3),
)
console.log('OK reverse-guides-smoke')
