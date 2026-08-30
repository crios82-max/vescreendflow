import { useI18n, LanguageSwitcher } from '@ride-app/web-shared';

export default function Privacy() {
  const { t } = useI18n();
  return (
    <div className="auth-page">
      <LanguageSwitcher className="auth-page__lang" />
      <div className="auth-card" style={{ maxWidth: 640 }}>
        <h1>{t('legal.privacyTitle')}</h1>
        <p>{t('legal.privacyP1')}</p>
        <p>{t('legal.privacyP2')}</p>
        <p>{t('legal.privacyP3')}</p>
        <p><a href="/login">{t('common.back')}</a></p>
      </div>
    </div>
  );
}
