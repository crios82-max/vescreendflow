import { Router } from 'express'
import { z } from 'zod'

export const navRouter = Router()

const OSRM_BASE = process.env.OSRM_URL || 'https://router.project-osrm.org'

/** Demo destinations around Caracas for one-tap navigation. */
export const DEMO_DESTINATIONS = [
  { id: 'altamira', name: 'Altamira', lat: 10.4965, lng: -66.8492 },
  { id: 'chacao', name: 'Chacao', lat: 10.4958, lng: -66.8756 },
  { id: 'bellas-artes', name: 'Bellas Artes', lat: 10.4989, lng: -66.8986 },
  { id: 'la-candelaria', name: 'La Candelaria', lat: 10.5025, lng: -66.9142 },
  { id: 'airport', name: 'Aeropuerto Maiquetía', lat: 10.6013, lng: -66.9912 },
]

navRouter.get('/destinations', (_req, res) => {
  res.json({ destinations: DEMO_DESTINATIONS })
})

const routeQuery = z.object({
  from_lat: z.coerce.number().min(-90).max(90),
  from_lng: z.coerce.number().min(-180).max(180),
  to_lat: z.coerce.number().min(-90).max(90),
  to_lng: z.coerce.number().min(-180).max(180),
  dest_name: z.string().max(80).optional(),
})

function maneuverInstruction(step: {
  manoeuvre?: { type?: string; modifier?: string }
  name?: string
  distance?: number
}): string {
  const type = step.manoeuvre?.type || 'turn'
  const mod = step.manoeuvre?.modifier || ''
  const road = step.name ? ` por ${step.name}` : ''
  const map: Record<string, string> = {
    'turn-left': 'Girá a la izquierda',
    'turn-right': 'Girá a la derecha',
    'turn-slight left': 'Leve a la izquierda',
    'turn-slight right': 'Leve a la derecha',
    'turn-sharp left': 'Cerrada a la izquierda',
    'turn-sharp right': 'Cerrada a la derecha',
    'turn-straight': 'Seguí derecho',
    'new name-straight': 'Continuar',
    'depart-': 'Salí',
    'arrive-': 'Llegaste',
    'merge-': 'Incorporate',
    'on ramp-': 'Entrá a la rampa',
    'off ramp-': 'Salí por la rampa',
    'fork-left': 'Tomá la horquilla izquierda',
    'fork-right': 'Tomá la horquilla derecha',
    'roundabout-': 'Rotonda',
    'rotary-': 'Rotonda',
    'continue-straight': 'Continuar derecho',
  }
  const key = `${type}-${mod}`.trim()
  const base =
    map[key] ||
    map[`${type}-`] ||
    (type === 'depart' ? 'Salí' : type === 'arrive' ? 'Llegaste al destino' : 'Continuar')
  return `${base}${road}`
}

function haversineM(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const R = 6371000
  const toR = (d: number) => (d * Math.PI) / 180
  const dLat = toR(bLat - aLat)
  const dLng = toR(bLng - aLng)
  const x =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toR(aLat)) * Math.cos(toR(bLat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(x))
}

function fallbackRoute(
  fromLat: number,
  fromLng: number,
  toLat: number,
  toLng: number,
  destName: string,
) {
  const distance_m = haversineM(fromLat, fromLng, toLat, toLng)
  const duration_s = distance_m / 8.3 // ~30 km/h urban
  return {
    ok: true,
    source: 'fallback',
    dest_name: destName,
    distance_m: Math.round(distance_m),
    duration_s: Math.round(duration_s),
    geometry: {
      type: 'LineString',
      coordinates: [
        [fromLng, fromLat],
        [toLng, toLat],
      ],
    },
    steps: [
      {
        instruction: `Dirigite a ${destName}`,
        distance_m: Math.round(distance_m),
        name: destName,
        type: 'depart',
        modifier: '',
      },
      {
        instruction: 'Llegaste al destino',
        distance_m: 0,
        name: destName,
        type: 'arrive',
        modifier: '',
      },
    ],
  }
}

navRouter.get('/route', async (req, res) => {
  const parsed = routeQuery.safeParse(req.query)
  if (!parsed.success) {
    res.status(400).json({ error: 'query inválida', details: parsed.error.flatten() })
    return
  }
  const { from_lat, from_lng, to_lat, to_lng } = parsed.data
  const destName = parsed.data.dest_name || 'Destino'

  const osrmUrl =
    `${OSRM_BASE}/route/v1/driving/` +
    `${from_lng},${from_lat};${to_lng},${to_lat}` +
    `?overview=full&geometries=geojson&steps=true&annotations=false`

  try {
    const ctrl = AbortSignal.timeout(10_000)
    const r = await fetch(osrmUrl, { signal: ctrl })
    if (!r.ok) throw new Error(`OSRM HTTP ${r.status}`)
    const data = (await r.json()) as {
      code?: string
      routes?: Array<{
        distance: number
        duration: number
        geometry: { type: string; coordinates: number[][] }
        legs: Array<{
          steps: Array<{
            distance: number
            name: string
            manoeuvre?: { type?: string; modifier?: string; instruction?: string }
          }>
        }>
      }>
    }
    if (data.code !== 'Ok' || !data.routes?.[0]) throw new Error(`OSRM ${data.code}`)
    const route = data.routes[0]
    const rawSteps = route.legs.flatMap((l) => l.steps)
    const steps = rawSteps
      .filter((s) => (s.manoeuvre?.type || '') !== 'notification')
      .map((s) => ({
        instruction: s.manoeuvre?.instruction || maneuverInstruction(s),
        distance_m: Math.round(s.distance),
        name: s.name || '',
        type: s.manoeuvre?.type || '',
        modifier: s.manoeuvre?.modifier || '',
      }))
      .filter((s, i, arr) => !(s.type === 'arrive' && i < arr.length - 1))

    res.json({
      ok: true,
      source: 'osrm',
      dest_name: destName,
      distance_m: Math.round(route.distance),
      duration_s: Math.round(route.duration),
      geometry: route.geometry,
      steps,
    })
  } catch (e) {
    console.warn('nav OSRM fail → fallback', e)
    res.json(fallbackRoute(from_lat, from_lng, to_lat, to_lng, destName))
  }
})
