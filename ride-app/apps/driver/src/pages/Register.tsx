import { FormEvent, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import type { VehicleType } from '@ride-app/shared';
import { VEHICLE_OPTIONS, VEHICLE_TYPES } from '@ride-app/shared';
import { useAuth } from '@ride-app/web-shared';

export default function Register() {
  const { register, user } = useAuth();
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
      setError(err instanceof Error ? err.message : 'Error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>Registro conductor</h1>
        <label>Nombre<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></label>
        <label>Email<input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></label>
        <label>Teléfono<input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
        <label>Marca<input value={form.vehicleMake} onChange={(e) => setForm({ ...form, vehicleMake: e.target.value })} /></label>
        <label>Modelo<input value={form.vehicleModel} onChange={(e) => setForm({ ...form, vehicleModel: e.target.value })} /></label>
        <label>Placa<input value={form.vehiclePlate} onChange={(e) => setForm({ ...form, vehiclePlate: e.target.value })} /></label>
        <label>
          Tipo de vehículo
          <select
            value={form.vehicleType}
            onChange={(e) => setForm({ ...form, vehicleType: e.target.value as VehicleType })}
            style={{ padding: '12px 14px', borderRadius: 10, background: '#0a0a0a', color: '#fff', border: '1px solid #333' }}
          >
            {VEHICLE_TYPES.map((type) => (
              <option key={type} value={type}>
                {VEHICLE_OPTIONS[type].icon} {VEHICLE_OPTIONS[type].label}
              </option>
            ))}
          </select>
        </label>
        <label>Contraseña<input type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required /></label>
        {error && <p className="error-text">{error}</p>}
        <button className="btn-primary" disabled={loading}>{loading ? 'Creando...' : 'Registrarme'}</button>
        <Link to="/login" className="link-btn">Ya tengo cuenta</Link>
      </form>
    </div>
  );
}
