import { Router } from 'express';
import { z } from 'zod';
import { buildRideEstimate, estimateFare, VEHICLE_OPTIONS, type VehicleType } from '@ride-app/shared';
import { pool } from '../db.js';
import { authMiddleware, requireRole } from '../middleware/auth.js';
import { mapRide } from '../mappers.js';
import { resolveTripMetrics } from '../services/directions.js';
import { autoAssignDriver, findNearestDriver } from '../services/matching.js';
import { notifyRideEvent } from '../services/push.js';
import { processRidePayment } from '../services/stripe.js';
import type { Server as SocketServer } from 'socket.io';

const router = Router();

function pricing() {
  return {
    baseFare: Number(process.env.BASE_FARE ?? 2.5),
    pricePerKm: Number(process.env.PRICE_PER_KM ?? 1.2),
    pricePerMin: Number(process.env.PRICE_PER_MIN ?? 0.25),
  };
}

const locationSchema = z.object({
  pickupAddress: z.string().min(3),
  pickupLat: z.number(),
  pickupLng: z.number(),
  dropoffAddress: z.string().min(3),
  dropoffLat: z.number(),
  dropoffLng: z.number(),
});

const createRideSchema = locationSchema.extend({
  vehicleType: z.enum(['standard', 'comfort', 'xl', 'vans']),
});

function tripMetrics(pickupLat: number, pickupLng: number, dropoffLat: number, dropoffLng: number) {
  return resolveTripMetrics(pickupLat, pickupLng, dropoffLat, dropoffLng);
}

function priceForType(
  distanceKm: number,
  durationMin: number,
  vehicleType: VehicleType,
  p: ReturnType<typeof pricing>,
) {
  return estimateFare(
    distanceKm,
    durationMin,
    p.baseFare,
    p.pricePerKm,
    p.pricePerMin,
    VEHICLE_OPTIONS[vehicleType].multiplier,
  );
}

export function createRidesRouter(io: SocketServer) {
  router.use(authMiddleware);

  router.post('/estimate', async (req, res) => {
    const parsed = locationSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }
    const { pickupLat, pickupLng, dropoffLat, dropoffLng } = parsed.data;
    const metrics = await tripMetrics(pickupLat, pickupLng, dropoffLat, dropoffLng);
    const p = pricing();
    res.json(buildRideEstimate(metrics.distanceKm, metrics.durationMin, p.baseFare, p.pricePerKm, p.pricePerMin, metrics.polyline));
  });

  router.get('/history', async (req, res) => {
    const { role, userId } = req.auth!;
    const column = role === 'driver' ? 'driver_id' : 'passenger_id';
    const result = await pool.query(
      `SELECT * FROM rides WHERE ${column} = $1 AND status IN ('completed', 'cancelled')
       ORDER BY created_at DESC LIMIT 50`,
      [userId],
    );
    res.json({ rides: result.rows.map(mapRide) });
  });

  router.post('/', requireRole('passenger'), async (req, res) => {
    const parsed = createRideSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }

    const data = parsed.data;
    const metrics = await tripMetrics(data.pickupLat, data.pickupLng, data.dropoffLat, data.dropoffLng);
    const p = pricing();
    const estimatedPrice = priceForType(metrics.distanceKm, metrics.durationMin, data.vehicleType, p);

    const result = await pool.query(
      `INSERT INTO rides (
        passenger_id, pickup_address, pickup_lat, pickup_lng,
        dropoff_address, dropoff_lat, dropoff_lng,
        vehicle_type, estimated_price, distance_km, duration_min, route_polyline
      ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
      RETURNING *`,
      [
        req.auth!.userId,
        data.pickupAddress,
        data.pickupLat,
        data.pickupLng,
        data.dropoffAddress,
        data.dropoffLat,
        data.dropoffLng,
        data.vehicleType,
        estimatedPrice,
        metrics.distanceKm,
        metrics.durationMin,
        metrics.polyline,
      ],
    );

    let ride = mapRide(result.rows[0]);
    const nearestDriver = await findNearestDriver(data.vehicleType, data.pickupLat, data.pickupLng);
    if (nearestDriver) {
      const assigned = await autoAssignDriver(ride.id, nearestDriver);
      if (assigned) {
        ride = mapRide(assigned);
        await notifyRideEvent(
          { id: ride.id, passengerId: ride.passengerId, driverId: ride.driverId, status: ride.status },
          'Conductor asignado',
          'Un conductor aceptó tu viaje',
        );
        io.to(`ride:${ride.id}`).emit('ride:updated', ride);
        io.to('drivers:online').emit('ride:taken', { rideId: ride.id });
      }
    } else {
      io.to('drivers:online').emit('ride:requested', ride);
    }

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
    const { userId } = req.auth!;
    if (ride.passengerId !== userId && ride.driverId !== userId) {
      return res.status(403).json({ error: 'Acceso denegado' });
    }
    res.json(ride);
  });

  router.post('/:id/accept', requireRole('driver'), async (req, res) => {
    const driverProfile = await pool.query(
      'SELECT vehicle_type FROM driver_profiles WHERE user_id = $1',
      [req.auth!.userId],
    );
    if (driverProfile.rows.length === 0) {
      return res.status(404).json({ error: 'Perfil de conductor no encontrado' });
    }
    const driverVehicleType = driverProfile.rows[0].vehicle_type;

    const result = await pool.query(
      `UPDATE rides
       SET driver_id = $1, status = 'accepted', accepted_at = NOW()
       WHERE id = $2 AND status = 'requested' AND driver_id IS NULL
         AND vehicle_type = $3
       RETURNING *`,
      [req.auth!.userId, req.params.id, driverVehicleType],
    );
    if (result.rows.length === 0) {
      return res.status(409).json({ error: 'Viaje no disponible para tu tipo de vehículo' });
    }
    const ride = mapRide(result.rows[0]);
    io.to(`ride:${ride.id}`).emit('ride:updated', ride);
    io.to('drivers:online').emit('ride:taken', { rideId: ride.id });
    res.json(ride);
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
    const paymentInfo = await processRidePayment(amount, ride.id);

    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const paymentResult = await client.query(
        `INSERT INTO payments (ride_id, amount, method, card_last4, stripe_payment_intent_id, status)
         VALUES ($1, $2, $3, $4, $5, 'paid')
         RETURNING *`,
        [ride.id, amount, paymentInfo.method, paymentInfo.cardLast4, paymentInfo.stripePaymentIntentId],
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

  const statusSchema = z.object({
    status: z.enum(['arriving', 'in_progress', 'completed', 'cancelled']),
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
    const { userId } = req.auth!;
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
    await notifyRideEvent(
      { id: ride.id, passengerId: ride.passengerId, driverId: ride.driverId, status: ride.status },
      'Actualización de viaje',
      `Estado: ${ride.status}`,
    );
    res.json(ride);
  });

  const rateSchema = z.object({
    stars: z.number().int().min(1).max(5),
    comment: z.string().max(500).optional(),
  });

  router.post('/:id/rate', async (req, res) => {
    const parsed = rateSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.flatten() });
    }

    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) {
      return res.status(404).json({ error: 'Viaje no encontrado' });
    }

    const ride = mapRide(rideResult.rows[0]);
    if (ride.status !== 'completed') {
      return res.status(400).json({ error: 'Solo puedes calificar viajes completados' });
    }

    const { userId } = req.auth!;
    let rateeId: string | null = null;
    if (userId === ride.passengerId && ride.driverId) rateeId = ride.driverId;
    if (userId === ride.driverId) rateeId = ride.passengerId;
    if (!rateeId) return res.status(403).json({ error: 'No puedes calificar este viaje' });

    const ratingResult = await pool.query(
      `INSERT INTO ratings (ride_id, rater_id, ratee_id, stars, comment)
       VALUES ($1, $2, $3, $4, $5)
       ON CONFLICT (ride_id, rater_id) DO UPDATE SET stars = EXCLUDED.stars, comment = EXCLUDED.comment
       RETURNING *`,
      [ride.id, userId, rateeId, parsed.data.stars, parsed.data.comment ?? null],
    );

    if (ride.driverId && rateeId === ride.driverId) {
      await pool.query(
        `UPDATE driver_profiles SET rating = (
           SELECT COALESCE(AVG(stars), 5)::numeric(3,2) FROM ratings WHERE ratee_id = $1
         ) WHERE user_id = $1`,
        [ride.driverId],
      );
    }

    const row = ratingResult.rows[0];
    res.json({
      rating: {
        id: row.id,
        rideId: row.ride_id,
        raterId: row.rater_id,
        rateeId: row.ratee_id,
        stars: row.stars,
        comment: row.comment,
        createdAt: row.created_at.toISOString(),
      },
    });
  });

  return router;
}
