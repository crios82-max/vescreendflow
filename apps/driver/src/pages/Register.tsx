import { FormEvent, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import type { VehicleType } from '@ride-app/shared';
import { VEHICLE_TYPES } from '@ride-app/shared';
import { useAuth, BrandMark, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

export default function Register() {
  const { register, user } = useAuth();
  const { t, te, vehicle } = useI18n();
  const [form, setForm] = useState({
    name: '', email: '', password: '', phone: '',
    vehicleMake: '', vehicleModel: '', vehiclePlate: '',
    vehicleType: 'standard' as VehicleType,
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await register({ ...form, role: 'driver' });
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
        <h1>{t('auth.driverRegister')}</h1>
        <label>{t('common.name')}<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
        <label>{t('common.email')}<input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></label>
        <label>{t('common.phone')}<input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
        <label>{t('auth.vehicleMake')}<input value={form.vehicleMake} onChange={(e) => setForm({ ...form, vehicleMake: e.target.value })} /></label>
        <label>{t('auth.vehicleModel')}<input value={form.vehicleModel} onChange={(e) => setForm({ ...form, vehicleModel: e.target.value })} /></label>
        <label>{t('auth.vehiclePlate')}<input value={form.vehiclePlate} onChange={(e) => setForm({ ...form, vehiclePlate: e.target.value })} /></label>
        <label>
          {t('auth.vehicleType')}
          <select
            value={form.vehicleType}
            onChange={(e) => setForm({ ...form, vehicleType: e.target.value as VehicleType })}
            style={{ padding: '12px 14px', borderRadius: 10, background: '#0a0a0a', color: '#fff', border: '1px solid #333' }}
          >
            {VEHICLE_TYPES.map((type) => (
              <option key={type} value={type}>
                {vehicle(type)}
              </option>
            ))}
          </select>
        </label>
        <label>{t('common.password')}<input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required /></label>
        {error && <p className="error-text">{error}</p>}
        <button className="btn-primary" disabled={loading}>{loading ? t('common.registering') : t('common.register')}</button>
        <Link to="/login" className="link-btn">{t('auth.haveAccount')}</Link>
        <Link to="/terms" className="link-btn">{t('auth.terms')}</Link>
        <Link to="/privacy" className="link-btn">{t('auth.privacy')}</Link>
      </form>
    </div>
  );
}
