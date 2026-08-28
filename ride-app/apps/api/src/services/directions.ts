import { haversineKm } from '@ride-app/shared';

export interface DirectionsResult {
  distanceKm: number;
  durationMin: number;
  polyline: string | null;
}

export async function fetchDirections(
  pickupLat: number,
  pickupLng: number,
  dropoffLat: number,
  dropoffLng: number,
): Promise<DirectionsResult | null> {
  const key = process.env.GOOGLE_MAPS_API_KEY ?? process.env.VITE_GOOGLE_MAPS_API_KEY;
  if (!key) return null;

  const url = new URL('https://maps.googleapis.com/maps/api/directions/json');
  url.searchParams.set('origin', `${pickupLat},${pickupLng}`);
  url.searchParams.set('destination', `${dropoffLat},${dropoffLng}`);
  url.searchParams.set('key', key);

  try {
    const res = await fetch(url.toString());
    const data = await res.json() as {
      status: string;
      routes?: Array<{ overview_polyline: { points: string }; legs: Array<{ distance: { value: number }; duration: { value: number } }> }>;
    };
    if (data.status !== 'OK' || !data.routes?.[0]?.legs?.[0]) return null;
    const leg = data.routes[0].legs[0];
    return {
      distanceKm: Math.round((leg.distance.value / 1000) * 100) / 100,
      durationMin: Math.max(1, Math.ceil(leg.duration.value / 60)),
      polyline: data.routes[0].overview_polyline.points,
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
): DirectionsResult {
  const distanceKm = Math.round(haversineKm(pickupLat, pickupLng, dropoffLat, dropoffLng) * 100) / 100;
  const durationMin = Math.max(5, Math.round((distanceKm / 30) * 60));
  return { distanceKm, durationMin, polyline: null };
}

export async function resolveTripMetrics(
  pickupLat: number,
  pickupLng: number,
  dropoffLat: number,
  dropoffLng: number,
): Promise<DirectionsResult> {
  const directions = await fetchDirections(pickupLat, pickupLng, dropoffLat, dropoffLng);
  return directions ?? fallbackMetrics(pickupLat, pickupLng, dropoffLat, dropoffLng);
}
