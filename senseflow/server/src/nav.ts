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
  /** Intermediate stops: "lat,lng;lat,lng" (max 5). */
  via: z.string().max(400).optional(),
  /** Labels matching via count: "Chacao;Bellas Artes" */
  via_names: z.string().max(400).optional(),
})

export type NavPoint = { name: string; lat: number; lng: number; role: 'via' | 'dest' }

export function parseViaParam(
  viaRaw: string | undefined,
  viaNamesRaw: string | undefined,
): Array<{ name: string; lat: number; lng: number }> {
  if (!viaRaw || !viaRaw.trim()) return []
  const names = (viaNamesRaw || '')
    .split(';')
    .map((s) => s.trim())
    .filter(Boolean)
  const parts = viaRaw.split(';').map((s) => s.trim()).filter(Boolean)
  const out: Array<{ name: string; lat: number; lng: number }> = []
  for (let i = 0; i < parts.length && out.length < 5; i++) {
    const [latS, lngS] = parts[i].split(',').map((x) => x.trim())
    const lat = Number(latS)
    const lng = Number(lngS)
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) continue
    out.push({
      name: names[i] || `Parada ${i + 1}`,
      lat,
      lng,
    })
  }
  return out
}

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

function buildWaypoints(
  vias: Array<{ name: string; lat: number; lng: number }>,
  destName: string,
  toLat: number,
  toLng: number,
): NavPoint[] {
  return [
    ...vias.map((v) => ({ ...v, role: 'via' as const })),
    { name: destName, lat: toLat, lng: toLng, role: 'dest' },
  ]
}

function fallbackRoute(
  fromLat: number,
  fromLng: number,
  toLat: number,
  toLng: number,
  destName: string,
  vias: Array<{ name: string; lat: number; lng: number }> = [],
) {
  const points = [{ lat: fromLat, lng: fromLng }, ...vias, { lat: toLat, lng: toLng }]
  let distance_m = 0
  const legs: Array<{ distance_m: number; duration_s: number; to_name: string }> = []
  const steps: Array<{
    instruction: string
    distance_m: number
    name: string
    type: string
    modifier: string
  }> = []
  const names = [...vias.map((v) => v.name), destName]
  for (let i = 0; i < points.length - 1; i++) {
    const a = points[i]
    const b = points[i + 1]
    const d = haversineM(a.lat, a.lng, b.lat, b.lng)
    const dur = d / 8.3
    distance_m += d
    const toName = names[i] || destName
    legs.push({
      distance_m: Math.round(d),
      duration_s: Math.round(dur),
      to_name: toName,
    })
    steps.push({
      instruction: i === 0 ? `Dirigite a ${toName}` : `Continuar hacia ${toName}`,
      distance_m: Math.round(d),
      name: toName,
      type: i === 0 ? 'depart' : 'continue',
      modifier: '',
    })
    steps.push({
      instruction: i === points.length - 2 ? 'Llegaste al destino' : `Pasá por ${toName}`,
      distance_m: 0,
      name: toName,
      type: 'arrive',
      modifier: '',
    })
  }
  return {
    ok: true,
    source: 'fallback',
    dest_name: destName,
    distance_m: Math.round(distance_m),
    duration_s: Math.round(distance_m / 8.3),
    geometry: {
      type: 'LineString',
      coordinates: points.map((p) => [p.lng, p.lat]),
    },
    steps,
    waypoints: buildWaypoints(vias, destName, toLat, toLng),
    legs,
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
  const vias = parseViaParam(parsed.data.via, parsed.data.via_names)
  const waypoints = buildWaypoints(vias, destName, to_lat, to_lng)

  const coordParts = [
    `${from_lng},${from_lat}`,
    ...vias.map((v) => `${v.lng},${v.lat}`),
    `${to_lng},${to_lat}`,
  ]
  const osrmUrl =
    `${OSRM_BASE}/route/v1/driving/` +
    coordParts.join(';') +
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
          distance: number
          duration: number
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

    const legs = route.legs.map((leg, i) => ({
      distance_m: Math.round(leg.distance),
      duration_s: Math.round(leg.duration),
      to_name: waypoints[i]?.name || destName,
    }))

    res.json({
      ok: true,
      source: 'osrm',
      dest_name: destName,
      distance_m: Math.round(route.distance),
      duration_s: Math.round(route.duration),
      geometry: route.geometry,
      steps,
      waypoints,
      legs,
    })
  } catch (e) {
    console.warn('nav OSRM fail → fallback', e)
    res.json(fallbackRoute(from_lat, from_lng, to_lat, to_lng, destName, vias))
  }
})
