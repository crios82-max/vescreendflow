import { FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

export default function ResetPassword() {
  const { t, te } = useI18n();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== confirm) {
      setError(t('auth.passwordMismatch'));
      return;
    }
    try {
      await api.resetPassword(token, password);
      setDone(true);
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    }
  };

  if (!token) {
    return (
      <div className="auth-page">
        <LanguageSwitcher className="auth-page__lang" />
        <p className="error-text">{t('common.invalidToken')}</p>
        <Link to="/login">{t('common.back')}</Link>
      </div>
    );
  }

  if (done) {
    return (
      <div className="auth-page">
        <LanguageSwitcher className="auth-page__lang" />
        <div className="auth-card">
          <h1>{t('auth.passwordUpdated')}</h1>
          <Link to="/login">{t('common.login')}</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <LanguageSwitcher className="auth-page__lang" />
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>{t('auth.newPassword')}</h1>
        <label>
          {t('common.password')}
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} />
        </label>
        <label>
          {t('auth.confirmPassword')}
          <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required minLength={6} />
        </label>
        {error && <p className="error-text">{error}</p>}
        <button className="btn-primary" type="submit">{t('common.save')}</button>
      </form>
    </div>
  );
}
