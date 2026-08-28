import { randomBytes } from 'crypto';
import type { Server as SocketServer } from 'socket.io';
import { pool } from '../db.js';
import { mapRide } from '../mappers.js';
import { sendEmail } from './email.js';
import { createPaymentIntent, confirmPaymentIntent } from './stripe.js';

export async function finalizeSplitRideIfComplete(rideId: string, io?: SocketServer) {
  const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [rideId]);
  if (rideResult.rows.length === 0) return false;

  const ride = mapRide(rideResult.rows[0]);
  if (!rideResult.rows[0].split_mode) return false;
  if (ride.paymentStatus === 'paid') return true;

  const organizerPaid = rideResult.rows[0].organizer_split_paid as boolean;
  const parts = await pool.query(
    `SELECT status FROM ride_split_participants WHERE ride_id = $1`,
    [rideId],
  );
  const allInviteesPaid = parts.rows.length > 0 && parts.rows.every((r) => r.status === 'paid');
  if (!organizerPaid || !allInviteesPaid) return false;

  const total = ride.finalPrice ?? ride.estimatedPrice;
  await pool.query(`UPDATE rides SET payment_status = 'paid' WHERE id = $1`, [rideId]);
  await pool.query(
    `INSERT INTO payments (ride_id, amount, method, card_last4, status)
     VALUES ($1, $2, 'split_fare', '0000', 'paid')`,
    [rideId, total],
  );

  if (ride.driverId) {
    const driverShare = Math.round(total * 0.75 * 100) / 100;
    await pool.query(
      `UPDATE driver_profiles SET total_earnings = total_earnings + $1 WHERE user_id = $2`,
      [driverShare, ride.driverId],
    );
  }

  const updated = mapRide({ ...rideResult.rows[0], payment_status: 'paid' });
  io?.to(`ride:${rideId}`).emit('ride:updated', updated);
  return true;
}

export async function sendSplitInviteEmail(email: string, token: string, amount: number, rideId: string) {
  const baseUrl = process.env.PASSENGER_WEB_URL ?? 'http://localhost:5174';
  const link = `${baseUrl}/split-pay/${token}`;
  await sendEmail(
    email,
    'Te invitaron a dividir un viaje — Ride App',
    `<p>Alguien te invitó a pagar tu parte de un viaje: <strong>$${amount.toFixed(2)}</strong></p>
     <p><a href="${link}">Pagar mi parte</a></p>
     <p>Viaje: ${rideId}</p>`,
  );
}

export function newInviteToken() {
  return randomBytes(24).toString('hex');
}

export async function createSplitPaymentIntent(token: string) {
  const part = await pool.query(
    `SELECT p.*, r.id AS ride_id FROM ride_split_participants p
     JOIN rides r ON r.id = p.ride_id WHERE p.invite_token = $1`,
    [token],
  );
  if (part.rows.length === 0) return null;
  if (part.rows[0].status === 'paid') return { alreadyPaid: true as const };

  const amount = Number(part.rows[0].amount);
  const intent = await createPaymentIntent(amount, part.rows[0].ride_id as string, 0, null);
  if (!intent) return { mock: true as const, amount };

  await pool.query(
    `UPDATE ride_split_participants SET stripe_payment_intent_id = $1 WHERE id = $2`,
    [intent.paymentIntentId, part.rows[0].id],
  );
  return { ...intent, alreadyPaid: false as const };
}

export async function confirmSplitPayment(token: string, paymentIntentId: string, io?: SocketServer) {
  const part = await pool.query(
    'SELECT * FROM ride_split_participants WHERE invite_token = $1',
    [token],
  );
  if (part.rows.length === 0) return { ok: false, error: 'Invitación no encontrada' };
  if (part.rows[0].status === 'paid') return { ok: true, alreadyPaid: true };

  const intent = await confirmPaymentIntent(paymentIntentId);
  if (!intent || intent.status !== 'succeeded') {
    return { ok: false, error: 'Pago no confirmado' };
  }

  await pool.query(
    `UPDATE ride_split_participants SET status = 'paid', paid_at = NOW() WHERE id = $1`,
    [part.rows[0].id],
  );
  await finalizeSplitRideIfComplete(part.rows[0].ride_id as string, io);
  return { ok: true };
}
