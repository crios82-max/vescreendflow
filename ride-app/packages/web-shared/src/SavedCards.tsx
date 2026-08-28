import { useEffect, useState } from 'react';
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { loadStripe } from '@stripe/stripe-js';
import { api } from './api';

const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? '');

function AddCardForm({ onAdded }: { onAdded: () => void }) {
  const stripe = useStripe();
  const elements = useElements();
  const [loading, setLoading] = useState(false);

  return (
    <form onSubmit={async (e) => {
      e.preventDefault();
      if (!stripe || !elements) return;
      setLoading(true);
      const { error } = await stripe.confirmSetup({ elements, redirect: 'if_required' });
      setLoading(false);
      if (!error) onAdded();
    }}>
      <PaymentElement options={{ wallets: { applePay: 'auto', googlePay: 'auto' } }} />
      <button className="btn-secondary" type="submit" disabled={loading}>{loading ? 'Guardando...' : 'Guardar tarjeta'}</button>
    </form>
  );
}

export function SavedCards() {
  const [methods, setMethods] = useState<Array<{ id: string; brand: string; last4: string }>>([]);
  const [setupSecret, setSetupSecret] = useState<string | null>(null);

  const load = () => api.getPaymentMethods().then((r) => setMethods(r.methods)).catch(() => {});

  useEffect(() => { load(); }, []);

  return (
    <div className="rating-form">
      <h3>Métodos de pago</h3>
      {methods.map((m) => (
        <div key={m.id} className="meta-row">
          <span>{m.brand} •••• {m.last4}</span>
          <button type="button" className="btn-secondary" onClick={() => api.deletePaymentMethod(m.id).then(load)}>Eliminar</button>
        </div>
      ))}
      {!setupSecret ? (
        <button type="button" className="btn-secondary" onClick={async () => {
          const r = await api.createSetupIntent();
          if (r.clientSecret) setSetupSecret(r.clientSecret);
        }}>Agregar tarjeta</button>
      ) : (
        <Elements stripe={stripePromise} options={{ clientSecret: setupSecret }}>
          <AddCardForm onAdded={() => { setSetupSecret(null); load(); }} />
        </Elements>
      )}
    </div>
  );
}
