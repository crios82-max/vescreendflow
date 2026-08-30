import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { StripeCheckout, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:4001';

export default function SplitPay() {
  const { token } = useParams();
  const { t, te } = useI18n();
  const [info, setInfo] = useState<{ email: string; amount: number; status: string; pickupAddress: string; dropoffAddress: string } | null>(null);
  const [clientSecret, setClientSecret] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return;
    fetch(`${API_URL}/split/invite/${token}`)
      .then((r) => r.json())
      .then((d) => {
        if (d.error) setError(te(d.error));
        else if (d.status === 'paid') setDone(true);
        else setInfo(d);
      })
      .catch(() => setError(t('common.loadFailed')));
  }, [token, t, te]);

  const startPay = async () => {
    if (!token) return;
    const r = await fetch(`${API_URL}/split/invite/${token}/payment-intent`, { method: 'POST' });
    const d = await r.json();
    if (d.clientSecret) setClientSecret(d.clientSecret);
    else if (d.mock) setError(t('split.demoPay', { amount: d.amount }));
    else setError(te(d.error ?? t('common.error')));
  };

  if (error) {
    return (
      <div className="auth-page">
        <LanguageSwitcher className="auth-page__lang" />
        <p className="error-text">{error}</p>
      </div>
    );
  }
  if (done) {
    return (
      <div className="auth-page">
        <LanguageSwitcher className="auth-page__lang" />
        <div className="auth-card">
          <h1>{t('split.thanksTitle')}</h1>
          <p>{t('split.thanksBody')}</p>
        </div>
      </div>
    );
  }
  if (!info) {
    return (
      <div className="auth-page">
        <LanguageSwitcher className="auth-page__lang" />
        {t('common.loading')}
      </div>
    );
  }

  return (
    <div className="auth-page">
      <LanguageSwitcher className="auth-page__lang" />
      <div className="auth-card">
        <h1>{t('split.payYourShare')}</h1>
        <p className="muted-text">{info.email}</p>
        <div className="meta-row"><span>{t('common.amount')}</span><strong>${info.amount}</strong></div>
        <div className="meta-row"><span>{t('common.origin')}</span><span>{info.pickupAddress}</span></div>
        <div className="meta-row"><span>{t('common.destination')}</span><span>{info.dropoffAddress}</span></div>
        {!clientSecret ? (
          <button className="btn-primary" type="button" onClick={startPay}>{t('common.continueToPay')}</button>
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
