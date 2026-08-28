import Stripe from 'stripe';
import { buildPaymentIntentParams } from './stripeConnect.js';

export function getStripe(): Stripe | null {
  const key = process.env.STRIPE_SECRET_KEY;
  if (!key) return null;
  return new Stripe(key);
}

export async function processRidePayment(
  amount: number,
  rideId: string,
  tipAmount = 0,
  driverConnectAccountId: string | null = null,
) {
  const total = Math.round((amount + tipAmount) * 100) / 100;
  const stripe = getStripe();
  if (!stripe) {
    return { method: 'mock_card', cardLast4: '4242', stripePaymentIntentId: null as string | null, total };
  }

  const params = await buildPaymentIntentParams(
    Math.round(total * 100),
    rideId,
    tipAmount,
    driverConnectAccountId,
  );

  const intent = await stripe.paymentIntents.create({
    ...params,
    automatic_payment_methods: { enabled: true, allow_redirects: 'never' },
    confirm: true,
    payment_method: 'pm_card_visa',
  } as Stripe.PaymentIntentCreateParams);

  return {
    method: driverConnectAccountId ? 'stripe_connect' : 'stripe',
    cardLast4: '4242',
    stripePaymentIntentId: intent.id,
    total,
  };
}

export async function createPaymentIntent(
  amount: number,
  rideId: string,
  tipAmount = 0,
  driverConnectAccountId: string | null = null,
) {
  const stripe = getStripe();
  if (!stripe) return null;

  const total = Math.round((amount + tipAmount) * 100) / 100;
  const params = await buildPaymentIntentParams(
    Math.round(total * 100),
    rideId,
    tipAmount,
    driverConnectAccountId,
  );

  const intent = await stripe.paymentIntents.create({
    ...params,
    automatic_payment_methods: { enabled: true },
  } as Stripe.PaymentIntentCreateParams);

  return { clientSecret: intent.client_secret, paymentIntentId: intent.id, amount: total };
}

export async function confirmPaymentIntent(paymentIntentId: string) {
  const stripe = getStripe();
  if (!stripe) return null;
  return stripe.paymentIntents.retrieve(paymentIntentId);
}
