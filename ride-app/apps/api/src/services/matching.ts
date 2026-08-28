import { pool } from '../db.js';
import type { VehicleType } from '@ride-app/shared';

export async function findNearestDriver(
  vehicleType: VehicleType,
  lat: number,
  lng: number,
): Promise<string | null> {
  const result = await pool.query(
    `SELECT user_id
     FROM driver_profiles
     WHERE is_online = TRUE
       AND vehicle_type = $1
       AND lat IS NOT NULL
       AND lng IS NOT NULL
     ORDER BY ((lat - $2) * (lat - $2) + (lng - $3) * (lng - $3)) ASC
     LIMIT 1`,
    [vehicleType, lat, lng],
  );
  return (result.rows[0]?.user_id as string) ?? null;
}

export async function autoAssignDriver(rideId: string, driverId: string) {
  const result = await pool.query(
    `UPDATE rides
     SET driver_id = $1, status = 'accepted', accepted_at = NOW()
     WHERE id = $2 AND status = 'requested' AND driver_id IS NULL
     RETURNING *`,
    [driverId, rideId],
  );
  return result.rows[0] ?? null;
}
