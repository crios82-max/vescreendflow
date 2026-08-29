import { FormEvent, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { api, useAuth, BrandMark, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

export default function Login() {
  const { login, user } = useAuth();
  const { t, te } = useI18n();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotMsg, setForgotMsg] = useState('');

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await login(email, password);
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <LanguageSwitcher className="auth-page__lang" />
      <form className="auth-card" onSubmit={onSubmit}>
        <BrandMark size="lg" showTagline />
        <label>
          {t('common.email')}
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          {t('common.password')}
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error-text">{error}</p>}
        {forgotMsg && <p className="muted-text">{forgotMsg}</p>}
        <button className="btn-primary" disabled={loading}>{loading ? t('common.loggingIn') : t('common.login')}</button>
        <button type="button" className="link-btn" disabled={forgotLoading} onClick={async () => {
          if (!email) { setError(t('common.enterEmail')); return; }
          setForgotLoading(true);
          setError('');
          try {
            const r = await api.forgotPassword(email);
            setForgotMsg(r.devResetUrl ? t('common.devReset', { url: r.devResetUrl }) : t('common.checkEmail'));
          } catch (err) {
            setError(te(err instanceof Error ? err.message : t('common.error')));
          } finally {
            setForgotLoading(false);
          }
        }}>{forgotLoading ? t('common.processing') : t('auth.forgotPassword')}</button>
        <Link to="/register" className="link-btn">{t('auth.createAccount')}</Link>
        <p className="muted-text"><Link to="/terms">{t('auth.terms')}</Link> · <Link to="/privacy">{t('auth.privacy')}</Link></p>
      </form>
    </div>
  );
}
