import { useState } from 'react';
import { api } from './api';
import { useI18n } from './I18nProvider';

interface Props {
  rideId: string;
}

export function SplitFareForm({ rideId }: Props) {
  const { t, te } = useI18n();
  const [emails, setEmails] = useState('');
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [invites, setInvites] = useState<Array<{ email: string; payUrl: string }>>([]);

  return (
    <div className="rating-form">
      <h3>{t('common.splitBill')}</h3>
      <input
        className="place-input"
        placeholder={t('common.emailsComma')}
        value={emails}
        onChange={(e) => setEmails(e.target.value)}
        aria-label={t('common.emailsComma')}
      />
      <button
        type="button"
        className="btn-secondary"
        disabled={loading}
        aria-label={t('common.splitAndInvite')}
        onClick={async () => {
          setError('');
          setResult('');
          setLoading(true);
          try {
            const list = emails.split(',').map((e) => e.trim()).filter(Boolean);
            const r = await api.splitFare(rideId, list);
            setResult(t('common.splitShare', { amount: r.yourShare }));
            setInvites(r.invites ?? []);
          } catch (err) {
            setError(te(err instanceof Error ? err.message : t('common.error')));
          } finally {
            setLoading(false);
          }
        }}
      >
        {loading ? t('common.processing') : t('common.splitAndInvite')}
      </button>
      {error && <p className="error-text">{error}</p>}
      {result && <p className="muted-text">{result}</p>}
      {invites.map((i) => (
        <div key={i.email} className="meta-row">
          <span>{i.email}</span>
          <button type="button" className="link-btn" onClick={() => navigator.clipboard.writeText(i.payUrl)}>{t('common.copyLink')}</button>
        </div>
      ))}
    </div>
  );
}
