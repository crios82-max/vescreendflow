import { useEffect, useState } from 'react';
import { api } from './api';

export function usePhoneVerified() {
  const [verified, setVerified] = useState<boolean | null>(null);

  useEffect(() => {
    api.getPhoneVerifyStatus().then((s) => setVerified(s.verified)).catch(() => setVerified(false));
  }, []);

  return { verified, setVerified };
}

export function PhoneVerifyBanner() {
  const { verified, setVerified } = usePhoneVerified();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [hint, setHint] = useState('');

  if (verified === null) return null;
  if (verified) return null;

  return (
    <div className="rating-form">
      <h3>Verifica tu teléfono (requerido)</h3>
      <input className="place-input" placeholder="+58..." value={phone} onChange={(e) => setPhone(e.target.value)} />
      <input className="place-input" placeholder="Código 6 dígitos" value={code} onChange={(e) => setCode(e.target.value)} />
      {hint && <p className="muted-text">{hint}</p>}
      <div className="extras-row">
        <button type="button" className="btn-secondary" onClick={async () => {
          const r = await api.sendPhoneOtp(phone);
          if (r.devHint) setHint(`Dev: ${r.devHint}`);
        }}>Enviar código</button>
        <button type="button" className="btn-primary" onClick={async () => {
          await api.confirmPhoneOtp(phone, code);
          setVerified(true);
        }}>Verificar</button>
      </div>
    </div>
  );
}
