import { haversineKm } from '@ride-app/shared';

export interface DirectionsResult {
  distanceKm: number;
  durationMin: number;
  polyline: string | null;
}

export interface Waypoint {
  lat: number;
  lng: number;
}

export async function fetchDirections(
  pickupLat: number,
  pickupLng: number,
  dropoffLat: number,
  dropoffLng: number,
  waypoints: Waypoint[] = [],
): Promise<DirectionsResult | null> {
  const key = process.env.GOOGLE_MAPS_API_KEY ?? process.env.VITE_GOOGLE_MAPS_API_KEY;
  if (!key) return null;

  const url = new URL('https://maps.googleapis.com/maps/api/directions/json');
  url.searchParams.set('origin', `${pickupLat},${pickupLng}`);
  url.searchParams.set('destination', `${dropoffLat},${dropoffLng}`);
  if (waypoints.length > 0) {
    url.searchParams.set(
      'waypoints',
      waypoints.map((w) => `${w.lat},${w.lng}`).join('|'),
    );
  }
  url.searchParams.set('key', key);

  try {
    const res = await fetch(url.toString());
    const data = await res.json() as {
      status: string;
      routes?: Array<{
        overview_polyline: { points: string };
        legs: Array<{ distance: { value: number }; duration: { value: number } }>;
      }>;
    };
    if (data.status !== 'OK' || !data.routes?.[0]?.legs?.length) return null;
    const route = data.routes[0];
    const distanceM = route.legs.reduce((sum, leg) => sum + leg.distance.value, 0);
    const durationS = route.legs.reduce((sum, leg) => sum + leg.duration.value, 0);
    return {
      distanceKm: Math.round((distanceM / 1000) * 100) / 100,
      durationMin: Math.max(1, Math.ceil(durationS / 60)),
      polyline: route.overview_polyline.points,
    };
  } catch {
    return null;
  }
}

export function fallbackMetrics(
  pickupLat: number,
  pickupLng: number,
  dropoffLat: number,
  dropoffLng: number,
  waypoints: Waypoint[] = [],
): DirectionsResult {
  const points = [
    { lat: pickupLat, lng: pickupLng },
    ...waypoints,
    { lat: dropoffLat, lng: dropoffLng },
  ];
  let distanceKm = 0;
  for (let i = 0; i < points.length - 1; i++) {
    distanceKm += haversineKm(points[i].lat, points[i].lng, points[i + 1].lat, points[i + 1].lng);
  }
  distanceKm = Math.round(distanceKm * 100) / 100;
  const durationMin = Math.max(5, Math.round((distanceKm / 30) * 60));
  return { distanceKm, durationMin, polyline: null };
}

export async function resolveTripMetrics(
  pickupLat: number,
  pickupLng: number,
  dropoffLat: number,
  dropoffLng: number,
  waypoints: Waypoint[] = [],
): Promise<DirectionsResult> {
  const directions = await fetchDirections(pickupLat, pickupLng, dropoffLat, dropoffLng, waypoints);
  return directions ?? fallbackMetrics(pickupLat, pickupLng, dropoffLat, dropoffLng, waypoints);
}
