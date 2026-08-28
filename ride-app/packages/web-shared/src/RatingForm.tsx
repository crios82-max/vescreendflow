import { useState } from 'react';

interface Props {
  onSubmit: (stars: number, comment: string) => Promise<void>;
  title?: string;
}

export function RatingForm({ onSubmit, title = 'Califica tu viaje' }: Props) {
  const [stars, setStars] = useState(5);
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(false);

  return (
    <div className="rating-form">
      <h3>{title}</h3>
      <div className="stars-row">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" className={`star-btn${n <= stars ? ' star-btn--on' : ''}`} onClick={() => setStars(n)}>
            ★
          </button>
        ))}
      </div>
      <input
        className="place-input"
        placeholder="Comentario opcional"
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
        {loading ? 'Enviando...' : 'Enviar calificación'}
      </button>
    </div>
  );
}
