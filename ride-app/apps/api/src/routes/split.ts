import { Router } from 'express';
import { z } from 'zod';
import type { Server as SocketServer } from 'socket.io';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide } from '../mappers.js';
import { sendEmail } from '../services/email.js';
import {
  confirmSplitPayment,
  createSplitPaymentIntent,
  finalizeSplitRideIfComplete,
  newInviteToken,
  sendSplitInviteEmail,
} from '../services/splitFare.js';
import { sendError } from '../httpError.js';

export function createSplitRouter(io: SocketServer) {
  const router = Router();

  router.get('/invite/:token', async (req, res) => {
    const result = await pool.query(
      `SELECT p.email, p.amount, p.status, r.pickup_address, r.dropoff_address
       FROM ride_split_participants p JOIN rides r ON r.id = p.ride_id
       WHERE p.invite_token = $1`,
      [req.params.token],
    );
    if (result.rows.length === 0) return sendError(res, 404, 'Invitación no encontrada', 'INVITE_NOT_FOUND');

    const row = result.rows[0];
    res.json({
      email: row.email,
      amount: Number(row.amount),
      status: row.status,
      pickupAddress: row.pickup_address,
      dropoffAddress: row.dropoff_address,
    });
  });

  router.post('/invite/:token/payment-intent', async (req, res) => {
    const result = await createSplitPaymentIntent(req.params.token);
    if (!result) return sendError(res, 404, 'Invitación no encontrada', 'INVITE_NOT_FOUND');
    if ('alreadyPaid' in result && result.alreadyPaid) {
      return res.json({ alreadyPaid: true });
    }
    res.json(result);
  });

  router.post('/invite/:token/pay', async (req, res) => {
    const schema = z.object({ paymentIntentId: z.string() });
    const parsed = schema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

    const outcome = await confirmSplitPayment(req.params.token, parsed.data.paymentIntentId, io);
    if (!outcome.ok) return sendError(res, 400, outcome.error ?? 'Pago no confirmado', 'PAYMENT_NOT_CONFIRMED');
    res.json(outcome);
  });

  router.use(authMiddleware);

  router.get('/ride/:rideId/participants', async (req, res) => {
    const ride = await pool.query('SELECT passenger_id FROM rides WHERE id = $1', [req.params.rideId]);
    if (ride.rows.length === 0) return sendError(res, 404, 'Viaje no encontrado', 'RIDE_NOT_FOUND');
    if (ride.rows[0].passenger_id !== req.auth!.userId) {
      return sendError(res, 403, 'Solo el pasajero principal', 'PRIMARY_PASSENGER_ONLY');
    }

    const result = await pool.query(
      'SELECT id, email, amount, status, invite_token FROM ride_split_participants WHERE ride_id = $1 ORDER BY email',
      [req.params.rideId],
    );
    const baseUrl = process.env.PASSENGER_WEB_URL ?? 'http://localhost:5174';
    res.json({
      participants: result.rows.map((r) => ({
        id: r.id,
        email: r.email,
        amount: Number(r.amount),
        status: r.status,
        payUrl: r.invite_token ? `${baseUrl}/split-pay/${r.invite_token}` : null,
      })),
    });
  });

  router.post('/ride/:rideId', async (req, res) => {
    const schema = z.object({ emails: z.array(z.string().email()).min(1).max(4) });
    const parsed = schema.safeParse(req.body);
    if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

    const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.rideId]);
    if (rideResult.rows.length === 0) return sendError(res, 404, 'Viaje no encontrado', 'RIDE_NOT_FOUND');

    const ride = mapRide(rideResult.rows[0]);
    if (ride.passengerId !== req.auth!.userId) return sendError(res, 403, 'Solo el pasajero principal', 'PRIMARY_PASSENGER_ONLY');
    if (ride.status !== 'completed' || ride.paymentStatus === 'paid') {
      return sendError(res, 400, 'Solo viajes completados sin pagar', 'UNPAID_COMPLETED_ONLY');
    }

    const total = ride.finalPrice ?? ride.estimatedPrice;
    const shares = parsed.data.emails.length + 1;
    const perPerson = Math.round((total / shares) * 100) / 100;

    await pool.query('DELETE FROM ride_split_participants WHERE ride_id = $1', [ride.id]);
    await pool.query(
      `UPDATE rides SET split_mode = TRUE, split_share_amount = $1, organizer_split_paid = FALSE WHERE id = $2`,
      [perPerson, ride.id],
    );

    const baseUrl = process.env.PASSENGER_WEB_URL ?? 'http://localhost:5174';
    const invites: Array<{ email: string; payUrl: string }> = [];

    for (const email of parsed.data.emails) {
      const token = newInviteToken();
      await pool.query(
        `INSERT INTO ride_split_participants (ride_id, email, amount, status, invite_token)
         VALUES ($1, $2, $3, 'pending', $4)`,
        [ride.id, email, perPerson, token],
      );
      const payUrl = `${baseUrl}/split-pay/${token}`;
      invites.push({ email, payUrl });
      await sendSplitInviteEmail(email, token, perPerson, ride.id);
    }

    res.json({ perPerson, participants: parsed.data.emails.length, yourShare: perPerson, invites });
  });

  return router;
}
