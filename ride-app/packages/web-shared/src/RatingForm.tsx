import { useState } from 'react';
import { useI18n } from './I18nProvider';

interface Props {
  onSubmit: (stars: number, comment: string) => Promise<void>;
  title?: string;
}

export function RatingForm({ onSubmit, title }: Props) {
  const { t, te } = useI18n();
  const [stars, setStars] = useState(5);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  return (
    <div className="rating-form">
      <h3>{title ?? t('common.rateDriver')}</h3>
      <div className="stars-row">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            className={`star-btn${n <= stars ? ' star-btn--on' : ''}`}
            onClick={() => setStars(n)}
            aria-label={t('common.starRating', { n })}
            aria-pressed={n <= stars}
          >
            ★
          </button>
        ))}
      </div>
      <input
        className="place-input"
        placeholder={t('common.commentOptional')}
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        aria-label={t('common.commentOptional')}
      />
      {error && <p className="error-text">{error}</p>}
      <button
        className="btn-primary"
        disabled={loading}
        aria-label={t('common.submitRating')}
        onClick={async () => {
          setLoading(true);
          setError('');
          try {
            await onSubmit(stars, comment);
          } catch (err) {
            setError(te(err instanceof Error ? err.message : t('common.error')));
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
