import { FormEvent, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth, BrandMark, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

export default function Register() {
  const { register, user } = useAuth();
  const { t, te } = useI18n();
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await register({ ...form, role: 'passenger' });
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
        <h1>{t('auth.createAccount')}</h1>
        <label>{t('common.name')}<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
        <label>{t('common.email')}<input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></label>
        <label>{t('common.phone')}<input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
        <label>{t('common.password')}<input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required /></label>
        {error && <p className="error-text">{error}</p>}
        <button className="btn-primary" disabled={loading}>{loading ? t('common.registering') : t('common.register')}</button>
        <Link to="/login" className="link-btn">{t('auth.haveAccount')}</Link>
      </form>
    </div>
  );
}
