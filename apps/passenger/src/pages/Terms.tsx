import { BRAND } from '@ride-app/shared';
import { useI18n, LanguageSwitcher } from '@ride-app/web-shared';

export default function Terms() {
  const { t } = useI18n();
  return (
    <div className="auth-page">
      <LanguageSwitcher className="auth-page__lang" />
      <div className="auth-card" style={{ maxWidth: 640 }}>
        <h1>{t('legal.termsTitle')}</h1>
        <p>{t('legal.termsP1', { brand: BRAND.name })}</p>
        <p>{t('legal.termsP2')}</p>
        <p>{t('legal.termsP3')}</p>
        <p><a href="/login">{t('common.back')}</a></p>
      </div>
    </div>
  );
}
