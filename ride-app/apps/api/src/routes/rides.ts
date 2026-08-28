import { Router } from 'express';
import { z } from 'zod';
import { estimateFare, haversineKm } from '@ride-app/shared';
import { pool } from '../db.js';
import { authMiddleware, requireRole } from '../middleware/auth.js';
import { mapRide } from '../mappers.js';
import type { Server as SocketServer } from 'socket.io';

const router = Router();

function pricing() {
  return {
    baseFare: Number(process.env.BASE_FARE ?? 2.5),
    pricePerKm: Number(process.env.PRICE_PER_KM ?? 1.2),
    pricePerMin: Number(process.env.PRICE_PER_MIN ?? 0.25),
  };
}

const createRideSchema = z.object({
  pickupAddress: z.string().min(3),
  pickupLat: z.number(),
  pickupLng: z.number(),
  dropoffAddress: z.string().min(3),
  dropoffLat: z.number(),
  dropoffLng: z.number(),
});

export function createRidesRouter(io: SocketServer) {
  router.use(authMiddleware);

  router.post('/estimate', (req, res) => {
    const parsed = createRideSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }
    const { pickupLat, pickupLng, dropoffLat, dropoffLng } = parsed.data;
    const distanceKm = haversineKm(pickupLat, pickupLng, dropoffLat, dropoffLng);
    const durationMin = Math.max(5, Math.round((distanceKm / 30) * 60));
    const p = pricing();
    const estimatedPrice = estimateFare(distanceKm, durationMin, p.baseFare, p.pricePerKm, p.pricePerMin);
    res.json({ distanceKm: Math.round(distanceKm * 100) / 100, durationMin, estimatedPrice });
  });

  router.post('/', requireRole('passenger'), async (req, res) => {
    const parsed = createRideSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }

    const data = parsed.data;
    const distanceKm = haversineKm(data.pickupLat, data.pickupLng, data.dropoffLat, data.dropoffLng);
    const durationMin = Math.max(5, Math.round((distanceKm / 30) * 60));
    const p = pricing();
    const estimatedPrice = estimateFare(distanceKm, durationMin, p.baseFare, p.pricePerKm, p.pricePerMin);

    const result = await pool.query(
      `INSERT INTO rides (
        passenger_id, pickup_address, pickup_lat, pickup_lng,
        dropoff_address, dropoff_lat, dropoff_lng,
        estimated_price, distance_km, duration_min
      ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
      RETURNING *`,
      [
        req.auth!.userId,
        data.pickupAddress,
        data.pickupLat,
        data.pickupLng,
        data.dropoffAddress,
        data.dropoffLat,
        data.dropoffLng,
        estimatedPrice,
        Math.round(distanceKm * 100) / 100,
        durationMin,
      ],
    );

    const ride = mapRide(result.rows[0]);
    io.to('drivers:online').emit('ride:requested', ride);
    res.status(201).json(ride);
  });

  router.get('/active', async (req, res) => {
    const { role, userId } = req.auth!;
    const column = role === 'driver' ? 'driver_id' : 'passenger_id';
    const result = await pool.query(
      `SELECT * FROM rides
       WHERE ${column} = $1 AND status NOT IN ('completed', 'cancelled')
       ORDER BY created_at DESC LIMIT 1`,
      [userId],
    );
    if (result.rows.length === 0) {
      return res.json({ ride: null });
    }
    res.json({ ride: mapRide(result.rows[0]) });
  });

  router.get('/:id', async (req, res) => {
    const result = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Viaje no encontrado' });
    }
    const ride = mapRide(result.rows[0]);
    const { userId, role } = req.auth!;
    if (ride.passengerId !== userId && ride.driverId !== userId) {
      return res.status(403).json({ error: 'Acceso denegado' });
    }
    res.json(ride);
  });

  router.post('/:id/accept', requireRole('driver'), async (req, res) => {
    const result = await pool.query(
      `UPDATE rides
       SET driver_id = $1, status = 'accepted', accepted_at = NOW()
       WHERE id = $2 AND status = 'requested' AND driver_id IS NULL
       RETURNING *`,
      [req.auth!.userId, req.params.id],
    );
    if (result.rows.length === 0) {
      return res.status(409).json({ error: 'Viaje no disponible' });
    }
    const ride = mapRide(result.rows[0]);
    io.to(`ride:${ride.id}`).emit('ride:updated', ride);
    io.to('drivers:online').emit('ride:taken', { rideId: ride.id });
    res.json(ride);
  });

  const statusSchema = z.object({
    status: z.enum(['arriving', 'in_progress', 'completed', 'cancelled']),
  });

  router.post('/:id/pay', requireRole('passenger'), async (req, res) => {
    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) {
      return res.status(404).json({ error: 'Viaje no encontrado' });
    }

    const ride = mapRide(rideResult.rows[0]);
    if (ride.passengerId !== req.auth!.userId) {
      return res.status(403).json({ error: 'Acceso denegado' });
    }
    if (ride.status !== 'completed') {
      return res.status(400).json({ error: 'El viaje debe estar completado para pagar' });
    }
    if (ride.paymentStatus === 'paid') {
      return res.status(400).json({ error: 'Viaje ya pagado' });
    }

    const amount = ride.finalPrice ?? ride.estimatedPrice;
    const cardLast4 = '4242';

    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const paymentResult = await client.query(
        `INSERT INTO payments (ride_id, amount, method, card_last4, status)
         VALUES ($1, $2, 'mock_card', $3, 'paid')
         RETURNING *`,
        [ride.id, amount, cardLast4],
      );
      await client.query(`UPDATE rides SET payment_status = 'paid' WHERE id = $1`, [ride.id]);
      await client.query('COMMIT');

      const updated = mapRide({ ...rideResult.rows[0], payment_status: 'paid' });
      io.to(`ride:${ride.id}`).emit('ride:updated', updated);

      res.json({
        payment: {
          id: paymentResult.rows[0].id,
          rideId: ride.id,
          amount: Number(paymentResult.rows[0].amount),
          method: paymentResult.rows[0].method,
          cardLast4: paymentResult.rows[0].card_last4,
          status: paymentResult.rows[0].status,
          createdAt: paymentResult.rows[0].created_at.toISOString(),
        },
        ride: updated,
      });
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  });

  router.patch('/:id/status', async (req, res) => {
    const parsed = statusSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }

    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) {
      return res.status(404).json({ error: 'Viaje no encontrado' });
    }

    const current = mapRide(rideResult.rows[0]);
    const { userId, role } = req.auth!;
    const isPassenger = current.passengerId === userId;
    const isDriver = current.driverId === userId;

    if (!isPassenger && !isDriver) {
      return res.status(403).json({ error: 'Acceso denegado' });
    }

    const next = parsed.data.status;
    if (next === 'cancelled' && !isPassenger && current.status === 'requested') {
      return res.status(403).json({ error: 'Solo el pasajero puede cancelar' });
    }
    if (['arriving', 'in_progress', 'completed'].includes(next) && !isDriver) {
      return res.status(403).json({ error: 'Solo el conductor puede actualizar el viaje' });
    }

    const finalPrice = next === 'completed' ? current.estimatedPrice : null;
    const result = await pool.query(
      `UPDATE rides
       SET status = $1,
           final_price = COALESCE($2, final_price),
           completed_at = CASE WHEN $1 = 'completed' THEN NOW() ELSE completed_at END
       WHERE id = $3
       RETURNING *`,
      [next, finalPrice, req.params.id],
    );

    const ride = mapRide(result.rows[0]);
    io.to(`ride:${ride.id}`).emit('ride:updated', ride);
    res.json(ride);
  });

  return router;
}
