import { Router, type Request, type Response } from 'express';
import type Stripe from 'stripe';
import { getStripe } from '../services/stripe.js';
import { pool } from '../db.js';

const router = Router();

router.post('/stripe', async (req: Request, res: Response) => {
  const stripe = getStripe();
  const secret = process.env.STRIPE_WEBHOOK_SECRET;
  if (!stripe || !secret) {
    return res.status(503).json({ error: 'Webhooks no configurados' });
  }

  const sig = req.headers['stripe-signature'];
  if (!sig || typeof sig !== 'string') {
    return res.status(400).json({ error: 'Firma ausente' });
  }

  let event: Stripe.Event;
  try {
    event = stripe.webhooks.constructEvent(req.body as Buffer, sig, secret);
  } catch (err) {
    console.warn('Stripe webhook signature failed:', err);
    return res.status(400).json({ error: 'Firma inválida' });
  }

  try {
    switch (event.type) {
      case 'payment_intent.succeeded': {
        const intent = event.data.object as Stripe.PaymentIntent;
        const rideId = intent.metadata?.rideId;
        if (rideId) {
          await pool.query(
            `UPDATE payments SET status = 'paid' WHERE stripe_payment_intent_id = $1`,
            [intent.id],
          );
          await pool.query(
            `UPDATE rides SET payment_status = 'paid' WHERE id = $1`,
            [rideId],
          );
        }
        break;
      }
      case 'payment_intent.payment_failed': {
        const intent = event.data.object as Stripe.PaymentIntent;
        await pool.query(
          `UPDATE payments SET status = 'failed' WHERE stripe_payment_intent_id = $1`,
          [intent.id],
        );
        break;
      }
      case 'charge.refunded': {
        const charge = event.data.object as Stripe.Charge;
        const pi = charge.payment_intent;
        if (typeof pi === 'string') {
          await pool.query(
            `UPDATE payments SET status = 'failed' WHERE stripe_payment_intent_id = $1`,
            [pi],
          );
        }
        break;
      }
      case 'account.updated': {
        const account = event.data.object as Stripe.Account;
        if (account.charges_enabled && account.payouts_enabled) {
          await pool.query(
            `UPDATE driver_profiles SET stripe_connect_onboarded = TRUE
             WHERE stripe_connect_account_id = $1`,
            [account.id],
          );
        }
        break;
      }
      default:
        break;
    }
  } catch (err) {
    console.error('Stripe webhook handler error:', err);
    return res.status(500).json({ error: 'Handler error' });
  }

  res.json({ received: true });
});

export default router;
