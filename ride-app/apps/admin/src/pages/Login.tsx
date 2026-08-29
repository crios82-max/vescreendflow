import { FormEvent, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth, BrandMark, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

export default function Login() {
  const { login, user } = useAuth();
  const { t } = useI18n();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(email, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('common.error'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <LanguageSwitcher className="auth-page__lang" />
      <form className="auth-card" onSubmit={onSubmit}>
        <BrandMark size="lg" />
        <p>{t('auth.adminPanel')}</p>
        <label>
          {t('common.email')}
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          {t('common.password')}
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error-text">{error}</p>}
        <button className="btn-primary" disabled={loading}>{loading ? t('common.loggingIn') : t('common.login')}</button>
      </form>
    </div>
  );
}
