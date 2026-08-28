import { getStripe } from './stripe.js';
import { pool } from '../db.js';

export async function getOrCreateStripeCustomer(userId: string, email: string): Promise<string | null> {
  const existing = await pool.query('SELECT stripe_customer_id, email FROM users WHERE id = $1', [userId]);
  if (existing.rows.length === 0) return null;

  const currentId = existing.rows[0].stripe_customer_id as string | null;
  const stripe = getStripe();
  if (!stripe) return null;

  if (currentId) return currentId;

  const customer = await stripe.customers.create({
    email: email ?? existing.rows[0].email,
    metadata: { userId },
  });
  await pool.query('UPDATE users SET stripe_customer_id = $1 WHERE id = $2', [customer.id, userId]);
  return customer.id;
}

export async function listPaymentMethods(customerId: string) {
  const stripe = getStripe();
  if (!stripe) return [];

  const methods = await stripe.paymentMethods.list({ customer: customerId, type: 'card' });
  return methods.data.map((m) => ({
    id: m.id,
    brand: m.card?.brand ?? 'card',
    last4: m.card?.last4 ?? '????',
    expMonth: m.card?.exp_month,
    expYear: m.card?.exp_year,
  }));
}

export async function createSetupIntent(customerId: string) {
  const stripe = getStripe();
  if (!stripe) return null;

  const intent = await stripe.setupIntents.create({
    customer: customerId,
    automatic_payment_methods: { enabled: true },
  });
  return { clientSecret: intent.client_secret };
}

export async function detachPaymentMethod(paymentMethodId: string) {
  const stripe = getStripe();
  if (!stripe) return false;
  await stripe.paymentMethods.detach(paymentMethodId);
  return true;
}
