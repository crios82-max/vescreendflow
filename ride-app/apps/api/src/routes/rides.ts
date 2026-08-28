import { randomBytes } from 'crypto';
import { Router } from 'express';
import { z } from 'zod';
import { buildRideEstimate, VEHICLE_OPTIONS, type VehicleType } from '@ride-app/shared';
import { pool } from '../db.js';
import { authMiddleware, requireRole } from '../middleware/auth.js';
import { mapRide } from '../mappers.js';
import { resolveTripMetrics } from '../services/directions.js';
import { autoAssignDriver, findNearestDriver, activateScheduledRides } from '../services/matching.js';
import { notifyRideEvent } from '../services/push.js';
import { processRidePayment, createPaymentIntent } from '../services/stripe.js';
import { computeSurgeMultiplier } from '../services/surge.js';
import { buildFareBreakdown } from '../services/fare.js';
import { validatePromo, redeemPromo } from '../services/promo.js';
import { computeRideEta } from '../services/eta.js';
import { debitWallet } from '../services/wallet.js';
import { sendReceiptEmail } from '../services/receipts.js';
import type { Server as SocketServer } from 'socket.io';

const router = Router();

function pricing() {
  return {
    baseFare: Number(process.env.BASE_FARE ?? 2.5),
    pricePerKm: Number(process.env.PRICE_PER_KM ?? 1.2),
    pricePerMin: Number(process.env.PRICE_PER_MIN ?? 0.25),
  };
}

const stopSchema = z.object({
  address: z.string().min(3),
  lat: z.number(),
  lng: z.number(),
});

const locationSchema = z.object({
  pickupAddress: z.string().min(3),
  pickupLat: z.number(),
  pickupLng: z.number(),
  dropoffAddress: z.string().min(3),
  dropoffLat: z.number(),
  dropoffLng: z.number(),
  promoCode: z.string().optional(),
});

const createRideSchema = locationSchema.extend({
  vehicleType: z.enum(['standard', 'comfort', 'xl', 'vans']),
  scheduledAt: z.string().datetime().optional(),
  rideForName: z.string().optional(),
  rideForPhone: z.string().optional(),
  stops: z.array(stopSchema).max(3).optional(),
});

function tripMetrics(pickupLat: number, pickupLng: number, dropoffLat: number, dropoffLng: number) {
  return resolveTripMetrics(pickupLat, pickupLng, dropoffLat, dropoffLng);
}

export function createRidesRouter(io: SocketServer) {
  router.use(authMiddleware);

  router.post('/estimate', async (req, res) => {
    const parsed = locationSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

    const { pickupLat, pickupLng, dropoffLat, dropoffLng, promoCode } = parsed.data;
    const metrics = await tripMetrics(pickupLat, pickupLng, dropoffLat, dropoffLng);
    const p = pricing();
    const surge = await computeSurgeMultiplier(pickupLat, pickupLng);
    const estimate = buildRideEstimate(
      metrics.distanceKm, metrics.durationMin, p.baseFare, p.pricePerKm, p.pricePerMin, metrics.polyline, surge,
    );

    if (promoCode && estimate.options[0]) {
      const promo = await validatePromo(promoCode, estimate.options[0].estimatedPrice);
      if (promo) {
        estimate.options = estimate.options.map((o) => ({
          ...o,
          estimatedPrice: Math.round((o.estimatedPrice - promo.discount) * 100) / 100,
        }));
      }
    }
    res.json(estimate);
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

  router.post('/activate-scheduled', async (_req, res) => {
    const activated = await activateScheduledRides();
    for (const row of activated) {
      const ride = mapRide(row);
      const nearest = await findNearestDriver(ride.vehicleType, ride.pickupLat, ride.pickupLng);
      if (nearest) {
        const assigned = await autoAssignDriver(ride.id, nearest);
        if (assigned) {
          const updated = mapRide(assigned);
          io.to(`ride:${updated.id}`).emit('ride:updated', updated);
          await notifyRideEvent(updated);
        }
      } else {
        io.to('drivers:online').emit('ride:requested', ride);
      }
    }
    res.json({ activated: activated.length });
  });

  router.post('/', requireRole('passenger'), async (req, res) => {
    const parsed = createRideSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

    const data = parsed.data;
    const metrics = await tripMetrics(data.pickupLat, data.pickupLng, data.dropoffLat, data.dropoffLng);
    const p = pricing();
    const surge = await computeSurgeMultiplier(data.pickupLat, data.pickupLng);

    let promoDiscount = 0;
    let promoCode: string | null = null;
    const breakdown = buildFareBreakdown(
      metrics.distanceKm, metrics.durationMin, data.vehicleType, surge, 0,
      p.baseFare, p.pricePerKm, p.pricePerMin,
    );
    if (data.promoCode) {
      const promo = await validatePromo(data.promoCode, breakdown.total);
      if (promo) {
        promoDiscount = promo.discount;
        promoCode = promo.code;
        breakdown.promoDiscount = promoDiscount;
        breakdown.total = Math.round((breakdown.total - promoDiscount) * 100) / 100;
      }
    }
    const estimatedPrice = breakdown.total;
    const isScheduled = data.scheduledAt && new Date(data.scheduledAt) > new Date();
    const status = isScheduled ? 'scheduled' : 'requested';
    const shareToken = randomBytes(16).toString('hex');

    const result = await pool.query(
      `INSERT INTO rides (
        passenger_id, pickup_address, pickup_lat, pickup_lng,
        dropoff_address, dropoff_lat, dropoff_lng,
        vehicle_type, estimated_price, distance_km, duration_min, route_polyline,
        scheduled_at, status, surge_multiplier, fare_breakdown, promo_code, promo_discount,
        share_token, ride_for_name, ride_for_phone
      ) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,$21)
      RETURNING *`,
      [
        req.auth!.userId, data.pickupAddress, data.pickupLat, data.pickupLng,
        data.dropoffAddress, data.dropoffLat, data.dropoffLng,
        data.vehicleType, estimatedPrice, metrics.distanceKm, metrics.durationMin, metrics.polyline,
        isScheduled ? data.scheduledAt : null, status, surge, JSON.stringify(breakdown),
        promoCode, promoDiscount, shareToken, data.rideForName ?? null, data.rideForPhone ?? null,
      ],
    );

    if (data.stops?.length) {
      for (let i = 0; i < data.stops.length; i++) {
        const s = data.stops[i];
        await pool.query(
          `INSERT INTO ride_stops (ride_id, stop_order, address, lat, lng) VALUES ($1,$2,$3,$4,$5)`,
          [result.rows[0].id, i + 1, s.address, s.lat, s.lng],
        );
      }
    }
    if (promoCode) await redeemPromo(promoCode);

    let ride = mapRide(result.rows[0]);
    if (!isScheduled) {
      const nearestDriver = await findNearestDriver(data.vehicleType, data.pickupLat, data.pickupLng);
      if (nearestDriver) {
        const assigned = await autoAssignDriver(ride.id, nearestDriver);
        if (assigned) {
          ride = mapRide(assigned);
          await notifyRideEvent(ride);
          io.to(`ride:${ride.id}`).emit('ride:updated', ride);
          io.to('drivers:online').emit('ride:taken', { rideId: ride.id });
        }
      } else {
        io.to('drivers:online').emit('ride:requested', ride);
      }
    }

    res.status(201).json(ride);
  });

  router.get('/active', async (req, res) => {
    const { role, userId } = req.auth!;
    const column = role === 'driver' ? 'driver_id' : 'passenger_id';
    const result = await pool.query(
      `SELECT * FROM rides WHERE ${column} = $1 AND status NOT IN ('completed', 'cancelled')
       ORDER BY created_at DESC LIMIT 1`,
      [userId],
    );
    res.json({ ride: result.rows.length ? mapRide(result.rows[0]) : null });
  });

  router.get('/:id/eta', async (req, res) => {
    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
    const ride = mapRide(rideResult.rows[0]);
    const { userId } = req.auth!;
    if (ride.passengerId !== userId && ride.driverId !== userId) {
      return res.status(403).json({ error: 'Acceso denegado' });
    }
    const eta = await computeRideEta(ride.id);
    res.json(eta);
  });

  router.get('/:id/receipt', async (req, res) => {
    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
    const ride = mapRide(rideResult.rows[0]);
    if (ride.passengerId !== req.auth!.userId) return res.status(403).json({ error: 'Acceso denegado' });
    if (ride.paymentStatus !== 'paid') return res.status(400).json({ error: 'Viaje no pagado' });

    const user = await pool.query('SELECT email FROM users WHERE id = $1', [ride.passengerId]);
    await sendReceiptEmail(ride, user.rows[0].email as string);
    res.json({ ok: true, ride });
  });

  router.post('/:id/share', async (req, res) => {
    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
    const ride = mapRide(rideResult.rows[0]);
    if (ride.passengerId !== req.auth!.userId) return res.status(403).json({ error: 'Acceso denegado' });

    let token = ride.shareToken;
    if (!token) {
      token = randomBytes(16).toString('hex');
      await pool.query('UPDATE rides SET share_token = $1 WHERE id = $2', [token, ride.id]);
    }
    res.json({ shareUrl: `/share/${token}`, shareToken: token });
  });

  router.get('/:id', async (req, res) => {
    const result = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (result.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
    const ride = mapRide(result.rows[0]);
    const { userId } = req.auth!;
    if (ride.passengerId !== userId && ride.driverId !== userId) {
      return res.status(403).json({ error: 'Acceso denegado' });
    }
    res.json(ride);
  });

  router.post('/:id/accept', requireRole('driver'), async (req, res) => {
    const driverProfile = await pool.query(
      'SELECT vehicle_type, approval_status FROM driver_profiles WHERE user_id = $1',
      [req.auth!.userId],
    );
    if (driverProfile.rows.length === 0) return res.status(404).json({ error: 'Perfil no encontrado' });
    if (driverProfile.rows[0].approval_status !== 'approved') {
      return res.status(403).json({ error: 'Cuenta de conductor pendiente de aprobación' });
    }

    const result = await pool.query(
      `UPDATE rides SET driver_id = $1, status = 'accepted', accepted_at = NOW()
       WHERE id = $2 AND status IN ('requested', 'scheduled') AND driver_id IS NULL
         AND vehicle_type = $3 RETURNING *`,
      [req.auth!.userId, req.params.id, driverProfile.rows[0].vehicle_type],
    );
    if (result.rows.length === 0) return res.status(409).json({ error: 'Viaje no disponible' });

    const ride = mapRide(result.rows[0]);
    io.to(`ride:${ride.id}`).emit('ride:updated', ride);
    io.to('drivers:online').emit('ride:taken', { rideId: ride.id });
    await notifyRideEvent(ride);
    res.json(ride);
  });

  router.post('/:id/payment-intent', requireRole('passenger'), async (req, res) => {
    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
    const ride = mapRide(rideResult.rows[0]);
    if (ride.passengerId !== req.auth!.userId) return res.status(403).json({ error: 'Acceso denegado' });
    if (ride.status !== 'completed') return res.status(400).json({ error: 'Viaje no completado' });

    const tipAmount = Number((req.body as { tipAmount?: number }).tipAmount ?? 0);
    const amount = ride.finalPrice ?? ride.estimatedPrice;
    const intent = await createPaymentIntent(amount, ride.id, tipAmount);
    if (!intent) return res.json({ mock: true, amount: amount + tipAmount });
    res.json(intent);
  });

  router.post('/:id/pay', requireRole('passenger'), async (req, res) => {
    const paySchema = z.object({
      tipAmount: z.number().min(0).max(100).optional(),
      useWallet: z.boolean().optional(),
      paymentIntentId: z.string().optional(),
    });
    const parsed = paySchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

    const ride = mapRide(rideResult.rows[0]);
    if (ride.passengerId !== req.auth!.userId) return res.status(403).json({ error: 'Acceso denegado' });
    if (ride.status !== 'completed') return res.status(400).json({ error: 'Viaje debe estar completado' });
    if (ride.paymentStatus === 'paid') return res.status(400).json({ error: 'Ya pagado' });

    const tipAmount = parsed.data.tipAmount ?? 0;
    const amount = ride.finalPrice ?? ride.estimatedPrice;
    let paymentInfo: { method: string; cardLast4: string; stripePaymentIntentId: string | null; total: number };

    if (parsed.data.useWallet) {
      const ok = await debitWallet(req.auth!.userId, amount + tipAmount, 'Pago de viaje', ride.id);
      if (!ok) return res.status(400).json({ error: 'Saldo insuficiente en wallet' });
      paymentInfo = { method: 'wallet', cardLast4: '0000', stripePaymentIntentId: null, total: amount + tipAmount };
    } else {
      paymentInfo = await processRidePayment(amount, ride.id, tipAmount);
    }

    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const paymentResult = await client.query(
        `INSERT INTO payments (ride_id, amount, method, card_last4, stripe_payment_intent_id, tip_amount, status)
         VALUES ($1, $2, $3, $4, $5, $6, 'paid') RETURNING *`,
        [ride.id, paymentInfo.total, paymentInfo.method, paymentInfo.cardLast4, paymentInfo.stripePaymentIntentId, tipAmount],
      );
      await client.query(
        `UPDATE rides SET payment_status = 'paid', tip_amount = $1 WHERE id = $2`,
        [tipAmount, ride.id],
      );
      if (ride.driverId) {
        const driverShare = Math.round(paymentInfo.total * 0.75 * 100) / 100;
        await client.query(
          `UPDATE driver_profiles SET total_earnings = total_earnings + $1 WHERE user_id = $2`,
          [driverShare, ride.driverId],
        );
      }
      await client.query('COMMIT');

      const updated = mapRide({ ...rideResult.rows[0], payment_status: 'paid', tip_amount: tipAmount });
      io.to(`ride:${ride.id}`).emit('ride:updated', updated);

      const user = await pool.query('SELECT email FROM users WHERE id = $1', [ride.passengerId]);
      await sendReceiptEmail(updated, user.rows[0].email as string);

      res.json({
        payment: {
          id: paymentResult.rows[0].id,
          rideId: ride.id,
          amount: Number(paymentResult.rows[0].amount),
          tipAmount,
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
    if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

    const current = mapRide(rideResult.rows[0]);
    const { userId } = req.auth!;
    const isPassenger = current.passengerId === userId;
    const isDriver = current.driverId === userId;
    if (!isPassenger && !isDriver) return res.status(403).json({ error: 'Acceso denegado' });

    const next = parsed.data.status;
    if (next === 'cancelled') {
      const canCancel = isPassenger || (isDriver && ['accepted', 'arriving'].includes(current.status));
      if (!canCancel) return res.status(403).json({ error: 'No puedes cancelar en este estado' });
    }
    if (['arriving', 'in_progress', 'completed'].includes(next) && !isDriver) {
      return res.status(403).json({ error: 'Solo el conductor puede actualizar' });
    }

    let cancellationFee: number | null = null;
    if (next === 'cancelled' && ['accepted', 'arriving'].includes(current.status) && isPassenger) {
      cancellationFee = Math.round((current.estimatedPrice * 0.25) * 100) / 100;
    }

    const finalPrice = next === 'completed' ? current.estimatedPrice : null;
    const result = await pool.query(
      `UPDATE rides SET status = $1, final_price = COALESCE($2, final_price),
         completed_at = CASE WHEN $1 = 'completed' THEN NOW() ELSE completed_at END,
         cancelled_by = CASE WHEN $1 = 'cancelled' THEN $3 ELSE cancelled_by END,
         cancelled_at = CASE WHEN $1 = 'cancelled' THEN NOW() ELSE cancelled_at END,
         cancellation_fee = COALESCE($4, cancellation_fee)
       WHERE id = $5 RETURNING *`,
      [next, finalPrice, userId, cancellationFee, req.params.id],
    );

    const ride = mapRide(result.rows[0]);
    if (next === 'cancelled' && isDriver && current.driverId) {
      await pool.query(
        `UPDATE rides SET driver_id = NULL, status = 'requested', accepted_at = NULL WHERE id = $1`,
        [current.id],
      );
      const nearest = await findNearestDriver(current.vehicleType, current.pickupLat, current.pickupLng);
      if (nearest) {
        const reassigned = await autoAssignDriver(current.id, nearest);
        if (reassigned) {
          const updated = mapRide(reassigned);
          io.to(`ride:${updated.id}`).emit('ride:updated', updated);
          await notifyRideEvent(updated);
          return res.json(updated);
        }
      }
      const reset = mapRide({ ...result.rows[0], status: 'requested', driver_id: null });
      io.to(`ride:${reset.id}`).emit('ride:updated', reset);
      io.to('drivers:online').emit('ride:requested', reset);
      return res.json(reset);
    }

    io.to(`ride:${ride.id}`).emit('ride:updated', ride);
    await notifyRideEvent(ride);
    if (['accepted', 'arriving', 'in_progress'].includes(ride.status)) {
      const eta = await computeRideEta(ride.id);
      io.to(`ride:${ride.id}`).emit('ride:eta', eta);
    }
    res.json(ride);
  });

  const rateSchema = z.object({
    stars: z.number().int().min(1).max(5),
    comment: z.string().max(500).optional(),
  });

  router.post('/:id/rate', async (req, res) => {
    const parsed = rateSchema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
    if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

    const ride = mapRide(rideResult.rows[0]);
    if (ride.status !== 'completed') return res.status(400).json({ error: 'Solo viajes completados' });

    const { userId } = req.auth!;
    let rateeId: string | null = null;
    if (userId === ride.passengerId && ride.driverId) rateeId = ride.driverId;
    if (userId === ride.driverId) rateeId = ride.passengerId;
    if (!rateeId) return res.status(403).json({ error: 'No puedes calificar' });

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
        id: row.id, rideId: row.ride_id, raterId: row.rater_id, rateeId: row.ratee_id,
        stars: row.stars, comment: row.comment, createdAt: row.created_at.toISOString(),
      },
    });
  });

  return router;
}
