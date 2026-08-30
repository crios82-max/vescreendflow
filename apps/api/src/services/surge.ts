import { pool } from '../db.js';

export async function computeSurgeMultiplier(pickupLat: number, pickupLng: number): Promise<number> {
  const radius = 0.05; // ~5km box
  const [pending, online] = await Promise.all([
    pool.query(
      `SELECT COUNT(*)::int AS c FROM rides
       WHERE status IN ('requested', 'scheduled')
         AND ABS(pickup_lat - $1) < $3 AND ABS(pickup_lng - $2) < $3`,
      [pickupLat, pickupLng, radius],
    ),
    pool.query(
      `SELECT COUNT(*)::int AS c FROM driver_profiles
       WHERE is_online = TRUE AND approval_status = 'approved'
         AND lat IS NOT NULL AND ABS(lat - $1) < $3 AND ABS(lng - $2) < $3`,
      [pickupLat, pickupLng, radius],
    ),
  ]);

  const demand = pending.rows[0].c as number;
  const supply = Math.max(online.rows[0].c as number, 1);
  const ratio = demand / supply;

  if (ratio >= 3) return 2.0;
  if (ratio >= 2) return 1.5;
  if (ratio >= 1.5) return 1.25;
  return 1.0;
}
