import { useState } from 'react';
import { api } from './api';
import { useI18n } from './I18nProvider';

interface Props {
  subtotal: number;
  onApplied: (code: string, discount: number) => void;
}

export function PromoInput({ subtotal, onApplied }: Props) {
  const { t } = useI18n();
  const [code, setCode] = useState('');
  const [error, setError] = useState('');

  return (
    <div className="promo-input">
      <input className="place-input" placeholder={t('common.promoPlaceholder')} value={code} onChange={(e) => setCode(e.target.value)} />
      <button
        type="button"
        className="btn-secondary"
        onClick={async () => {
          setError('');
          try {
            const promo = await api.validatePromo(code, subtotal);
            onApplied(promo.code, promo.discount);
          } catch {
            setError(t('common.invalidPromo'));
          }
        }}
      >
        {t('common.apply')}
      </button>
      {error && <p className="error-text">{error}</p>}
    </div>
  );
}
