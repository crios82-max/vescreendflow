import Stripe from 'stripe';

export function getStripe(): Stripe | null {
  const key = process.env.STRIPE_SECRET_KEY;
  if (!key) return null;
  return new Stripe(key);
}

export async function processRidePayment(amount: number, rideId: string, tipAmount = 0) {
  const total = Math.round((amount + tipAmount) * 100) / 100;
  const stripe = getStripe();
  if (!stripe) {
    return { method: 'mock_card', cardLast4: '4242', stripePaymentIntentId: null as string | null, total };
  }

  const intent = await stripe.paymentIntents.create({
    amount: Math.round(total * 100),
    currency: 'usd',
    automatic_payment_methods: { enabled: true, allow_redirects: 'never' },
    metadata: { rideId, tipAmount: String(tipAmount) },
    confirm: true,
    payment_method: 'pm_card_visa',
  });

  return {
    method: 'stripe',
    cardLast4: '4242',
    stripePaymentIntentId: intent.id,
    total,
  };
}

export async function createPaymentIntent(amount: number, rideId: string, tipAmount = 0) {
  const stripe = getStripe();
  if (!stripe) return null;

  const total = Math.round((amount + tipAmount) * 100) / 100;
  const intent = await stripe.paymentIntents.create({
    amount: Math.round(total * 100),
    currency: 'usd',
    automatic_payment_methods: { enabled: true },
    metadata: { rideId, tipAmount: String(tipAmount) },
  });

  return { clientSecret: intent.client_secret, paymentIntentId: intent.id, amount: total };
}

export async function confirmPaymentIntent(paymentIntentId: string) {
  const stripe = getStripe();
  if (!stripe) return null;
  return stripe.paymentIntents.retrieve(paymentIntentId);
}
