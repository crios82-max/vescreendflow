import { useState } from 'react';
import { useI18n } from './I18nProvider';

interface Props {
  onSubmit: (stars: number, comment: string) => Promise<void>;
  title?: string;
}

export function RatingForm({ onSubmit, title }: Props) {
  const { t } = useI18n();
  const [stars, setStars] = useState(5);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);

  return (
    <div className="rating-form">
      <h3>{title ?? t('common.rateDriver')}</h3>
      <div className="stars-row">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" className={`star-btn${n <= stars ? ' star-btn--on' : ''}`} onClick={() => setStars(n)}>
            ★
          </button>
        ))}
      </div>
      <input
        className="place-input"
        placeholder={t('common.commentOptional')}
        value={comment}
        onChange={(e) => setComment(e.target.value)}
      />
      <button
        className="btn-primary"
        disabled={loading}
        onClick={async () => {
          setLoading(true);
          try {
            await onSubmit(stars, comment);
          } finally {
            setLoading(false);
          }
        }}
      >
        {loading ? t('common.sending') : t('common.submitRating')}
      </button>
    </div>
  );
}
