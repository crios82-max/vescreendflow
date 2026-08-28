import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { StripeCheckout } from '@ride-app/web-shared';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:4001';

export default function SplitPay() {
  const { token } = useParams();
  const [info, setInfo] = useState<{ email: string; amount: number; status: string; pickupAddress: string; dropoffAddress: string } | null>(null);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return;
    fetch(`${API_URL}/split/invite/${token}`)
      .then((r) => r.json())
      .then((d) => {
        if (d.error) setError(d.error);
        else if (d.status === 'paid') setDone(true);
        else setInfo(d);
      })
      .catch(() => setError('No se pudo cargar'));
  }, [token]);

  const startPay = async () => {
    if (!token) return;
    const r = await fetch(`${API_URL}/split/invite/${token}/payment-intent`, { method: 'POST' });
    const d = await r.json();
    if (d.clientSecret) setClientSecret(d.clientSecret);
    else if (d.mock) alert(`Modo demo: paga $${d.amount} al organizador`);
    else setError(d.error ?? 'Error');
  };

  if (error) return <div className="auth-page"><p className="error-text">{error}</p></div>;
  if (done) return <div className="auth-page"><div className="auth-card"><h1>¡Gracias!</h1><p>Tu parte ya fue pagada.</p></div></div>;
  if (!info) return <div className="auth-page">Cargando...</div>;

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Pagar tu parte</h1>
        <p className="muted-text">{info.email}</p>
        <div className="meta-row"><span>Monto</span><strong>${info.amount}</strong></div>
        <div className="meta-row"><span>Origen</span><span>{info.pickupAddress}</span></div>
        <div className="meta-row"><span>Destino</span><span>{info.dropoffAddress}</span></div>
        {!clientSecret ? (
          <button className="btn-primary" type="button" onClick={startPay}>Continuar al pago</button>
        ) : (
          <StripeCheckout
            clientSecret={clientSecret}
            onSuccess={async (paymentIntentId) => {
              await fetch(`${API_URL}/split/invite/${token}/pay`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ paymentIntentId }),
              });
              setDone(true);
            }}
          />
        )}
      </div>
    </div>
  );
}
