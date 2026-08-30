import { pool } from '../db.js';
import type { VehicleType } from '@ride-app/shared';

export async function findNearestDriver(
  vehicleType: VehicleType,
  lat: number,
  lng: number,
): Promise<string | null> {
  const result = await pool.query(
    `SELECT dp.user_id,
            dp.lat, dp.lng, dp.rating,
            ((dp.lat - $2) * (dp.lat - $2) + (dp.lng - $3) * (dp.lng - $3)) AS dist_sq
     FROM driver_profiles dp
     JOIN users u ON u.id = dp.user_id
     WHERE dp.is_online = TRUE
       AND dp.approval_status = 'approved'
       AND u.banned = FALSE
       AND dp.vehicle_type = $1
       AND dp.lat IS NOT NULL
       AND dp.lng IS NOT NULL
     ORDER BY dist_sq ASC, dp.rating DESC
     LIMIT 5`,
    [vehicleType, lat, lng],
  );

  if (result.rows.length === 0) return null;
  // Pick best score: closer + higher rating
  let best = result.rows[0];
  let bestScore = scoreDriver(best);
  for (const row of result.rows.slice(1)) {
    const s = scoreDriver(row);
    if (s < bestScore) {
      best = row;
      bestScore = s;
    }
  }
  return best.user_id as string;
}

function scoreDriver(row: Record<string, unknown>) {
  const dist = Math.sqrt(Number(row.dist_sq));
  const rating = Number(row.rating);
  return dist * 0.7 + (5 - rating) * 0.3;
}

export async function autoAssignDriver(rideId: string, driverId: string) {
  const result = await pool.query(
    `UPDATE rides
     SET driver_id = $1, status = 'accepted', accepted_at = NOW()
     WHERE id = $2 AND status IN ('requested', 'scheduled') AND driver_id IS NULL
     RETURNING *`,
    [driverId, rideId],
  );
  return result.rows[0] ?? null;
}

export async function activateScheduledRides() {
  const result = await pool.query(
    `UPDATE rides SET status = 'requested'
     WHERE status = 'scheduled' AND scheduled_at <= NOW()
     RETURNING *`,
  );
  return result.rows;
}
