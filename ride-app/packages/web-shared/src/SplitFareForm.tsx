import { useState } from 'react';
import { api } from './api';

interface Props {
  rideId: string;
}

export function SplitFareForm({ rideId }: Props) {
  const [emails, setEmails] = useState('');
  const [result, setResult] = useState<string>('');
  const [invites, setInvites] = useState<Array<{ email: string; payUrl: string }>>([]);

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
          setResult(`Tu parte: $${r.yourShare} — espera que paguen los invitados`);
          setInvites(r.invites ?? []);
        }}
      >
        Dividir y enviar invitaciones
      </button>
      {result && <p className="muted-text">{result}</p>}
      {invites.map((i) => (
        <div key={i.email} className="meta-row">
          <span>{i.email}</span>
          <button type="button" className="link-btn" onClick={() => navigator.clipboard.writeText(i.payUrl)}>Copiar link</button>
        </div>
      ))}
    </div>
  );
}
