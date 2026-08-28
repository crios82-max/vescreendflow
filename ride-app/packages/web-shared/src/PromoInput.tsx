import { useState } from 'react';
import { api } from './api';

interface Props {
  subtotal: number;
  onApplied: (code: string, discount: number) => void;
}

export function PromoInput({ subtotal, onApplied }: Props) {
  const [code, setCode] = useState('');
  const [error, setError] = useState('');

  return (
    <div className="promo-input">
      <input className="place-input" placeholder="Código promo" value={code} onChange={(e) => setCode(e.target.value)} />
      <button
        type="button"
        className="btn-secondary"
        onClick={async () => {
          setError('');
          try {
            const promo = await api.validatePromo(code, subtotal);
            onApplied(promo.code, promo.discount);
          } catch {
            setError('Código inválido');
          }
        }}
      >
        Aplicar
      </button>
      {error && <p className="error-text">{error}</p>}
    </div>
  );
}
