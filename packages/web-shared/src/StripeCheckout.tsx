import { loadStripe } from '@stripe/stripe-js';
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { useState } from 'react';
import { useI18n } from './I18nProvider';

const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? '');

function CheckoutForm({ onSuccess }: { onSuccess: (paymentIntentId: string) => void }) {
  const { t } = useI18n();
  const stripe = useStripe();
  const elements = useElements();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        if (!stripe || !elements) return;
        setLoading(true);
        setError('');
        const { error: err, paymentIntent } = await stripe.confirmPayment({ elements, redirect: 'if_required' });
        setLoading(false);
        if (err) setError(err.message ?? t('common.paymentError'));
        else if (paymentIntent) onSuccess(paymentIntent.id);
      }}
    >
      <PaymentElement options={{ wallets: { applePay: 'auto', googlePay: 'auto' } }} />
      {error && <p className="error-text">{error}</p>}
      <button className="btn-primary" type="submit" disabled={!stripe || loading}>
        {loading ? t('common.processing') : t('common.pay')}
      </button>
    </form>
  );
}

export function StripeCheckout({ clientSecret, onSuccess }: { clientSecret: string; onSuccess: (paymentIntentId: string) => void }) {
  if (!import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY) return null;

  return (
    <Elements stripe={stripePromise} options={{ clientSecret }}>
      <CheckoutForm onSuccess={onSuccess} />
    </Elements>
  );
}
