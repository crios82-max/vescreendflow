import { pool } from '../db.js';
import { fetchDirections } from './directions.js';
import { haversineKm } from '@ride-app/shared';

export async function computeRideEta(rideId: string): Promise<{ etaPickupMin: number | null; etaDropoffMin: number | null }> {
  const result = await pool.query(
    `SELECT r.*, d.lat AS driver_lat, d.lng AS driver_lng
     FROM rides r
     LEFT JOIN driver_profiles d ON d.user_id = r.driver_id
     WHERE r.id = $1`,
    [rideId],
  );
  if (result.rows.length === 0) return { etaPickupMin: null, etaDropoffMin: null };

  const ride = result.rows[0];
  const status = ride.status as string;
  const driverLat = ride.driver_lat != null ? Number(ride.driver_lat) : null;
  const driverLng = ride.driver_lng != null ? Number(ride.driver_lng) : null;

  let etaPickupMin: number | null = null;
  let etaDropoffMin: number | null = null;

  if (driverLat != null && driverLng != null) {
    if (['accepted', 'arriving'].includes(status)) {
      etaPickupMin = await etaBetween(driverLat, driverLng, Number(ride.pickup_lat), Number(ride.pickup_lng));
    }
    if (status === 'in_progress') {
      etaDropoffMin = await etaBetween(driverLat, driverLng, Number(ride.dropoff_lat), Number(ride.dropoff_lng));
    }
  }

  if (etaPickupMin != null || etaDropoffMin != null) {
    await pool.query(
      `UPDATE rides SET eta_pickup_min = COALESCE($1, eta_pickup_min), eta_dropoff_min = COALESCE($2, eta_dropoff_min) WHERE id = $3`,
      [etaPickupMin, etaDropoffMin, rideId],
    );
  }

  return { etaPickupMin, etaDropoffMin };
}

async function etaBetween(fromLat: number, fromLng: number, toLat: number, toLng: number): Promise<number> {
  const directions = await fetchDirections(fromLat, fromLng, toLat, toLng);
  if (directions) return directions.durationMin;
  const km = haversineKm(fromLat, fromLng, toLat, toLng);
  return Math.max(1, Math.ceil((km / 30) * 60));
}
