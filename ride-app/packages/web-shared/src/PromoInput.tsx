import { useState } from 'react';
import { api } from './api';
import { useI18n } from './I18nProvider';

interface Props {
  subtotal: number;
  onApplied: (code: string, discount: number) => void;
}

export function PromoInput({ subtotal, onApplied }: Props) {
  const { t, te } = useI18n();
  const [code, setCode] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [applied, setApplied] = useState<{ code: string; discount: number } | null>(null);

  return (
    <div className="promo-input">
      <input
        className="place-input"
        placeholder={t('common.promoPlaceholder')}
        value={code}
        onChange={(e) => { setCode(e.target.value); setApplied(null); }}
        aria-label={t('common.promoPlaceholder')}
        disabled={loading}
      />
      <button
        type="button"
        className="btn-secondary"
        disabled={loading || !code.trim()}
        aria-label={t('common.apply')}
        onClick={async () => {
          setError('');
          setLoading(true);
          try {
            const promo = await api.validatePromo(code, subtotal);
            onApplied(promo.code, promo.discount);
            setApplied({ code: promo.code, discount: promo.discount });
          } catch (err) {
            setApplied(null);
            setError(te(err instanceof Error ? err.message : t('common.invalidPromo')));
          } finally {
            setLoading(false);
          }
        }}
      >
        {loading ? t('common.processing') : t('common.apply')}
      </button>
      {applied && <p className="muted-text">{applied.code} −${applied.discount}</p>}
      {error && <p className="error-text">{error}</p>}
    </div>
  );
}
