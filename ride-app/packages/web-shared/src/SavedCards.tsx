import { useEffect, useState } from 'react';
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { api } from './api';
import { useI18n } from './I18nProvider';

const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? '');

function AddCardForm({ onAdded }: { onAdded: () => void }) {
  const { t, te } = useI18n();
  const stripe = useStripe();
  const elements = useElements();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  return (
    <form onSubmit={async (e) => {
      e.preventDefault();
      if (!stripe || !elements) return;
      setLoading(true);
      setError('');
      const { error: setupError } = await stripe.confirmSetup({ elements, redirect: 'if_required' });
      setLoading(false);
      if (setupError) {
        setError(te(setupError.message ?? t('common.error')));
        return;
      }
      onAdded();
    }}>
      <PaymentElement options={{ wallets: { applePay: 'auto', googlePay: 'auto' } }} />
      {error && <p className="error-text">{error}</p>}
      <button className="btn-secondary" type="submit" disabled={loading} aria-label={t('common.saveCard')}>
        {loading ? t('common.saving') : t('common.saveCard')}
      </button>
    </form>
  );
}

export function SavedCards() {
  const { t, te } = useI18n();
  const [methods, setMethods] = useState<Array<{ id: string; brand: string; last4: string }>>([]);
  const [setupSecret, setSetupSecret] = useState<string | null>(null);
  const [error, setError] = useState('');

  const load = () =>
    api.getPaymentMethods()
      .then((r) => {
        setMethods(r.methods);
        setError('');
      })
      .catch((err) => setError(te(err instanceof Error ? err.message : t('common.loadFailed'))));

  useEffect(() => { load(); }, []);

  return (
    <div className="rating-form">
      <h3>{t('common.paymentMethods')}</h3>
      {error && <p className="error-text">{error}</p>}
      {methods.map((m) => (
        <div key={m.id} className="meta-row">
          <span>{m.brand} •••• {m.last4}</span>
          <button type="button" className="btn-secondary" onClick={() => {
            api.deletePaymentMethod(m.id).then(load).catch((err) => {
              setError(te(err instanceof Error ? err.message : t('common.error')));
            });
          }}>{t('common.delete')}</button>
        </div>
      ))}
      {!setupSecret ? (
        <button type="button" className="btn-secondary" aria-label={t('common.addCard')} onClick={async () => {
          setError('');
          try {
            const r = await api.createSetupIntent();
            if (r.clientSecret) setSetupSecret(r.clientSecret);
          } catch (err) {
            setError(te(err instanceof Error ? err.message : t('common.error')));
          }
        }}>{t('common.addCard')}</button>
      ) : (
        <Elements stripe={stripePromise} options={{ clientSecret: setupSecret }}>
          <AddCardForm onAdded={() => { setSetupSecret(null); load(); }} />
        </Elements>
      )}
    </div>
  );
}
