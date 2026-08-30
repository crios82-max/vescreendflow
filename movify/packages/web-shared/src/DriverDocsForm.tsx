import { FormEvent, useEffect, useState } from 'react';
import { api } from './api';
import { useI18n } from './I18nProvider';

export function DriverDocsForm({ onUpdated }: { onUpdated?: () => void }) {
  const { t } = useI18n();
  const [docs, setDocs] = useState({ licenseUrl: '', idUrl: '', vehiclePhotoUrl: '' });
  const [status, setStatus] = useState<{ approvalStatus: string; rejectionReason: string | null } | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const load = () => {
    api.getOnboardingStatus().then((r) => {
      setStatus({ approvalStatus: r.approvalStatus, rejectionReason: r.rejectionReason });
    }).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    try {
      await api.submitDriverDocs({
        licenseUrl: docs.licenseUrl || undefined,
        idUrl: docs.idUrl || undefined,
        vehiclePhotoUrl: docs.vehiclePhotoUrl || undefined,
      });
      setMessage(t('driver.docsSubmitted'));
      load();
      onUpdated?.();
    } catch (err) {
      setMessage(err instanceof Error ? err.message : t('common.error'));
    } finally {
      setLoading(false);
    }
  };

  if (status?.approvalStatus === 'approved') return null;

  return (
    <form className="rating-form" onSubmit={onSubmit}>
      <h3>{t('driver.docsTitle')}</h3>
      <p className="muted-text">{t('driver.docsHint')}</p>
      {status?.approvalStatus === 'pending' && (
        <p className="muted-text">{t('driver.approvalPending')}</p>
      )}
      {status?.approvalStatus === 'rejected' && status.rejectionReason && (
        <p className="error-text">{t('driver.approvalRejected', { reason: status.rejectionReason })}</p>
      )}
      <label>{t('driver.licenseUrl')}<input value={docs.licenseUrl} onChange={(e) => setDocs({ ...docs, licenseUrl: e.target.value })} placeholder="https://..." /></label>
      <label>{t('driver.idUrl')}<input value={docs.idUrl} onChange={(e) => setDocs({ ...docs, idUrl: e.target.value })} placeholder="https://..." /></label>
      <label>{t('driver.vehiclePhotoUrl')}<input value={docs.vehiclePhotoUrl} onChange={(e) => setDocs({ ...docs, vehiclePhotoUrl: e.target.value })} placeholder="https://..." /></label>
      {message && <p className={message.includes(t('driver.docsSubmitted')) ? 'flash-text' : 'error-text'}>{message}</p>}
      <button className="btn-secondary" type="submit" disabled={loading}>
        {loading ? t('common.saving') : t('common.save')}
      </button>
    </form>
  );
}
