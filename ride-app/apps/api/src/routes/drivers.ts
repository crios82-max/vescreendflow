import { Router } from 'express';
import { z } from 'zod';
import { pool } from '../db.js';
import { authMiddleware, requireRole } from '../middleware/auth.js';
import { mapDriverProfile } from '../mappers.js';
import type { Server as SocketServer } from 'socket.io';

const router = Router();

const locationSchema = z.object({
  lat: z.number(),
  lng: z.number(),
});

export function createDriversRouter(io: SocketServer) {
  router.use(authMiddleware, requireRole('driver'));

  router.get('/profile', async (req, res) => {
    const result = await pool.query('SELECT * FROM driver_profiles WHERE user_id = $1', [
      req.auth!.userId,
    ]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Perfil no encontrado' });
    }
    res.json(mapDriverProfile(result.rows[0]));
  });

  router.post('/online', async (req, res) => {
    const parsed = locationSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }

    const profile = await pool.query(
      'SELECT approval_status FROM driver_profiles WHERE user_id = $1',
      [req.auth!.userId],
    );
    if (profile.rows[0]?.approval_status !== 'approved') {
      return res.status(403).json({ error: 'Tu cuenta de conductor está pendiente de aprobación' });
    }

    const { lat, lng } = parsed.data;
    const result = await pool.query(
      `UPDATE driver_profiles
       SET is_online = TRUE, lat = $1, lng = $2, updated_at = NOW()
       WHERE user_id = $3
       RETURNING *`,
      [lat, lng, req.auth!.userId],
    );
    res.json(mapDriverProfile(result.rows[0]));
  });

  router.post('/offline', async (req, res) => {
    const result = await pool.query(
      `UPDATE driver_profiles
       SET is_online = FALSE, updated_at = NOW()
       WHERE user_id = $1
       RETURNING *`,
      [req.auth!.userId],
    );
    res.json(mapDriverProfile(result.rows[0]));
  });

  router.post('/location', async (req, res) => {
    const parsed = locationSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }
    const { lat, lng } = parsed.data;
    await pool.query(
      `UPDATE driver_profiles SET lat = $1, lng = $2, updated_at = NOW() WHERE user_id = $3`,
      [lat, lng, req.auth!.userId],
    );

    const activeRide = await pool.query(
      `SELECT id FROM rides
       WHERE driver_id = $1 AND status IN ('accepted', 'arriving', 'in_progress')
       ORDER BY created_at DESC LIMIT 1`,
      [req.auth!.userId],
    );

    if (activeRide.rows.length > 0) {
      const rideId = activeRide.rows[0].id;
      io.to(`ride:${rideId}`).emit('driver:location', { rideId, lat, lng });
      const { computeRideEta } = await import('../services/eta.js');
      const eta = await computeRideEta(rideId);
      io.to(`ride:${rideId}`).emit('ride:eta', eta);
    }

    res.json({ ok: true });
  });

  router.get('/pending-rides', async (req, res) => {
    const profile = await pool.query(
      'SELECT lat, lng, is_online, vehicle_type FROM driver_profiles WHERE user_id = $1',
      [req.auth!.userId],
    );
    if (profile.rows.length === 0 || !profile.rows[0].is_online) {
      return res.json({ rides: [] });
    }

    const vehicleType = profile.rows[0].vehicle_type;
    const result = await pool.query(
      `SELECT * FROM rides
       WHERE status = 'requested' AND vehicle_type = $1
       ORDER BY created_at DESC LIMIT 20`,
      [vehicleType],
    );
    res.json({ rides: result.rows.map((r) => ({
      id: r.id,
      pickupAddress: r.pickup_address,
      pickupLat: Number(r.pickup_lat),
      pickupLng: Number(r.pickup_lng),
      dropoffAddress: r.dropoff_address,
      vehicleType: r.vehicle_type,
      serviceMode: r.service_mode ?? 'ride',
      deliveryNotes: r.delivery_notes ?? null,
      estimatedPrice: Number(r.estimated_price),
      distanceKm: Number(r.distance_km),
    }))});
  });

  return router;
}
