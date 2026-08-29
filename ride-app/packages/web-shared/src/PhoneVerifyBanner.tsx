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
  const { t } = useI18n();
  const { verified, setVerified } = usePhoneVerified();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [hint, setHint] = useState('');

  if (verified === null) return null;
  if (verified) return null;

  return (
    <div className="rating-form">
      <h3>{t('common.phoneRequired')}</h3>
      <input className="place-input" placeholder="+58..." value={phone} onChange={(e) => setPhone(e.target.value)} />
      <input className="place-input" placeholder={t('common.otpPlaceholder')} value={code} onChange={(e) => setCode(e.target.value)} />
      {hint && <p className="muted-text">{hint}</p>}
      <div className="extras-row">
        <button type="button" className="btn-secondary" onClick={async () => {
          const r = await api.sendPhoneOtp(phone);
          if (r.devHint) setHint(t('common.devOtp', { code: r.devHint }));
        }}>{t('common.sendCode')}</button>
        <button type="button" className="btn-primary" onClick={async () => {
          await api.confirmPhoneOtp(phone, code);
          setVerified(true);
        }}>{t('common.verify')}</button>
      </div>
    </div>
  );
}
