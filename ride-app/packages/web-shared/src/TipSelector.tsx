interface Props {
  value: number;
  onChange: (tip: number) => void;
}

const TIPS = [0, 1, 2, 5];

export function TipSelector({ value, onChange }: Props) {
  return (
    <div className="tip-selector">
      <span className="muted-text">Propina</span>
      <div className="tab-row">
        {TIPS.map((t) => (
          <button key={t} type="button" className={`tab-btn${value === t ? ' tab-btn--active' : ''}`} onClick={() => onChange(t)}>
            {t === 0 ? 'Sin propina' : `$${t}`}
          </button>
        ))}
      </div>
    </div>
  );
}
