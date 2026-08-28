import Stripe from 'stripe';

export function getStripe(): Stripe | null {
  const key = process.env.STRIPE_SECRET_KEY;
  if (!key) return null;
  return new Stripe(key);
}

export async function processRidePayment(amount: number, rideId: string) {
  const stripe = getStripe();
  if (!stripe) {
    return { method: 'mock_card', cardLast4: '4242', stripePaymentIntentId: null as string | null };
  }

  const intent = await stripe.paymentIntents.create({
    amount: Math.round(amount * 100),
    currency: 'usd',
    automatic_payment_methods: { enabled: true, allow_redirects: 'never' },
    metadata: { rideId },
    confirm: true,
    payment_method: 'pm_card_visa',
  });

  return {
    method: 'stripe',
    cardLast4: '4242',
    stripePaymentIntentId: intent.id,
  };
}
