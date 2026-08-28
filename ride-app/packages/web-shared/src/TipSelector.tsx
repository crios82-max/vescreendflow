import { useI18n } from './I18nProvider';

interface Props {
  value: number;
  onChange: (tip: number) => void;
}

const TIPS = [0, 1, 2, 5];

export function TipSelector({ value, onChange }: Props) {
  const { t } = useI18n();
  return (
    <div className="tip-selector">
      <span className="muted-text">{t('common.tip')}</span>
      <div className="tab-row">
        {TIPS.map((tip) => (
          <button key={tip} type="button" className={`tab-btn${value === tip ? ' tab-btn--active' : ''}`} onClick={() => onChange(tip)}>
            {tip === 0 ? t('common.noTip') : `$${tip}`}
          </button>
        ))}
      </div>
    </div>
  );
}
