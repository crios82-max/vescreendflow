import { useEffect, useState } from 'react';
import { api } from './api';
import { useI18n } from './I18nProvider';

export function usePhoneVerified() {
  const [verified, setVerified] = useState<boolean | null>(null);

  useEffect(() => {
    api.getPhoneVerifyStatus().then((s) => setVerified(s.verified)).catch(() => setVerified(false));
  }, []);

  return { verified, setVerified };
}

export function PhoneVerifyBanner() {
  const { t, te } = useI18n();
  const { verified, setVerified } = usePhoneVerified();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [hint, setHint] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (verified === null) return null;
  if (verified) return null;

  return (
    <div className="rating-form">
      <h3>{t('common.phoneRequired')}</h3>
      <input className="place-input" placeholder="+58..." value={phone} onChange={(e) => setPhone(e.target.value)} aria-label={t('common.phoneRequired')} />
      <input className="place-input" placeholder={t('common.otpPlaceholder')} value={code} onChange={(e) => setCode(e.target.value)} aria-label={t('common.otpPlaceholder')} />
      {hint && <p className="muted-text">{hint}</p>}
      {error && <p className="error-text">{error}</p>}
      <div className="extras-row">
        <button type="button" className="btn-secondary" disabled={loading} aria-label={t('common.sendCode')} onClick={async () => {
          setError('');
          setLoading(true);
          try {
            const r = await api.sendPhoneOtp(phone);
            if (r.devHint) setHint(t('common.devOtp', { code: r.devHint }));
          } catch (err) {
            setError(te(err instanceof Error ? err.message : t('common.error')));
          } finally {
            setLoading(false);
          }
        }}>{t('common.sendCode')}</button>
        <button type="button" className="btn-primary" disabled={loading} aria-label={t('common.verify')} onClick={async () => {
          setError('');
          setLoading(true);
          try {
            await api.confirmPhoneOtp(phone, code);
            setVerified(true);
          } catch (err) {
            setError(te(err instanceof Error ? err.message : t('common.error')));
          } finally {
            setLoading(false);
          }
        }}>{t('common.verify')}</button>
      </div>
    </div>
  );
}
