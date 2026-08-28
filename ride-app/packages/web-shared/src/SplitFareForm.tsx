import { useState } from 'react';
import { api } from './api';

interface Props {
  rideId: string;
}

export function SplitFareForm({ rideId }: Props) {
  const [emails, setEmails] = useState('');
  const [result, setResult] = useState<string>('');

  return (
    <div className="rating-form">
      <h3>Dividir cuenta</h3>
      <input
        className="place-input"
        placeholder="emails separados por coma"
        value={emails}
        onChange={(e) => setEmails(e.target.value)}
      />
      <button
        type="button"
        className="btn-secondary"
        onClick={async () => {
          const list = emails.split(',').map((e) => e.trim()).filter(Boolean);
          const r = await api.splitFare(rideId, list);
          setResult(`Tu parte: $${r.yourShare} (${list.length} invitados)`);
        }}
      >
        Dividir
      </button>
      {result && <p className="muted-text">{result}</p>}
    </div>
  );
}
