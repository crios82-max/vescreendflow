import { useEffect, useState } from 'react';
import type { Ride } from '@ride-app/shared';
import { RIDE_STATUS_LABELS } from '@ride-app/shared';
import { api, useAuth } from '@ride-app/web-shared';

type AdminRide = Ride & { passengerName?: string; driverName?: string };

type Tab = 'overview' | 'drivers' | 'users' | 'sos' | 'promos';

export default function Dashboard() {
  const { user, logout } = useAuth();
  const [tab, setTab] = useState<Tab>('overview');
  const [stats, setStats] = useState<{ users: number; drivers: number; rides: number; revenue: number; pendingDrivers: number; sosLast24h: number } | null>(null);
  const [rides, setRides] = useState<AdminRide[]>([]);
  const [pendingDrivers, setPendingDrivers] = useState<Array<{ userId: string; name: string; email: string; licenseUrl?: string; idUrl?: string; vehiclePhotoUrl?: string }>>([]);
  const [users, setUsers] = useState<Array<{ id: string; name: string; email: string; banned?: boolean }>>([]);
  const [sosEvents, setSosEvents] = useState<Array<{ id: string; name: string; pickup_address: string; created_at: string }>>([]);
  const [promos, setPromos] = useState<Array<{ code: string; discount_type: string; discount_value: number }>>([]);
  const [promoForm, setPromoForm] = useState({ code: '', discountType: 'percent' as 'percent' | 'fixed', discountValue: 10 });
  const [error, setError] = useState('');

  const reload = () => {
    Promise.all([
      api.getAdminStats(),
      api.getAdminRides(),
      api.getPendingDrivers(),
      api.getAdminUsers(),
      api.getAdminSos(),
      api.getAdminPromos(),
    ])
      .then(([s, r, d, u, sos, p]) => {
        setStats(s);
        setRides(r.rides);
        setPendingDrivers(d.drivers);
        setUsers(u.users);
        setSosEvents(sos.events);
        setPromos(p.promos);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Sin acceso admin'));
  };

  useEffect(() => { reload(); }, []);

  if (error) {
    return (
      <div className="admin-page">
        <h1>Acceso denegado</h1>
        <p className="error-text">{error}</p>
        <button className="btn-secondary" onClick={logout}>Salir</button>
      </div>
    );
  }

  if (!stats) return <div className="admin-page">Cargando...</div>;

  return (
    <div className="admin-page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1>Admin</h1>
          <p className="muted-text">{user?.name}</p>
        </div>
        <button className="btn-secondary" onClick={logout}>Salir</button>
      </div>

      <div className="tab-row">
        {(['overview', 'drivers', 'users', 'sos', 'promos'] as Tab[]).map((t) => (
          <button key={t} type="button" className={`tab-btn${tab === t ? ' tab-btn--active' : ''}`} onClick={() => setTab(t)}>
            {t === 'overview' ? 'Resumen' : t === 'drivers' ? `Conductores (${stats.pendingDrivers})` : t === 'users' ? 'Usuarios' : t === 'sos' ? `SOS (${stats.sosLast24h})` : 'Promos'}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <>
          <div className="admin-grid">
            <div className="stat-card"><span>Usuarios</span><strong>{stats.users}</strong></div>
            <div className="stat-card"><span>Conductores</span><strong>{stats.drivers}</strong></div>
            <div className="stat-card"><span>Viajes</span><strong>{stats.rides}</strong></div>
            <div className="stat-card"><span>Ingresos</span><strong>${stats.revenue.toFixed(2)}</strong></div>
            <div className="stat-card"><span>SOS 24h</span><strong>{stats.sosLast24h}</strong></div>
          </div>
          <h2>Últimos viajes</h2>
          <table className="admin-table">
            <thead><tr><th>Fecha</th><th>Pasajero</th><th>Estado</th><th>Pago</th><th>Precio</th><th></th></tr></thead>
            <tbody>
              {rides.slice(0, 20).map((ride) => (
                <tr key={ride.id}>
                  <td>{new Date(ride.createdAt).toLocaleString()}</td>
                  <td>{ride.passengerName}</td>
                  <td>{RIDE_STATUS_LABELS[ride.status]}</td>
                  <td>{ride.paymentStatus}</td>
                  <td>${ride.finalPrice ?? ride.estimatedPrice}</td>
                  <td>
                    {ride.paymentStatus === 'paid' && (
                      <button className="btn-secondary" onClick={() => api.refundRide(ride.id).then(reload)}>Reembolsar</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {tab === 'drivers' && (
        <div className="history-list">
          {pendingDrivers.length === 0 ? <p className="muted-text">Sin conductores pendientes</p> : pendingDrivers.map((d) => (
            <div key={d.userId} className="history-card">
              <strong>{d.name}</strong>
              <span>{d.email}</span>
              {d.licenseUrl && <a href={d.licenseUrl} target="_blank" rel="noreferrer">Licencia</a>}
              {d.idUrl && <a href={d.idUrl} target="_blank" rel="noreferrer">ID</a>}
              {d.vehiclePhotoUrl && <a href={d.vehiclePhotoUrl} target="_blank" rel="noreferrer">Vehículo</a>}
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn-primary" onClick={() => api.approveDriver(d.userId).then(reload)}>Aprobar</button>
                <button className="btn-secondary" onClick={() => api.rejectDriver(d.userId, 'Documentos inválidos').then(reload)}>Rechazar</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'users' && (
        <table className="admin-table">
          <thead><tr><th>Nombre</th><th>Email</th><th>Estado</th><th></th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>{u.banned ? 'Baneado' : 'Activo'}</td>
                <td>
                  {u.banned ? (
                    <button className="btn-secondary" onClick={() => api.unbanUser(u.id).then(reload)}>Unban</button>
                  ) : (
                    <button className="btn-secondary" onClick={() => api.banUser(u.id).then(reload)}>Ban</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {tab === 'sos' && (
        <div className="history-list">
          {sosEvents.length === 0 ? <p className="muted-text">Sin alertas SOS</p> : sosEvents.map((e) => (
            <div key={e.id} className="history-card">
              <strong>{e.name}</strong>
              <span>{new Date(e.created_at).toLocaleString()}</span>
              <span>{e.pickup_address}</span>
            </div>
          ))}
        </div>
      )}

      {tab === 'promos' && (
        <>
          <form className="auth-card" style={{ marginBottom: 16 }} onSubmit={(ev) => {
            ev.preventDefault();
            api.createPromo(promoForm).then(reload);
          }}>
            <h3>Nueva promo</h3>
            <input placeholder="Código" value={promoForm.code} onChange={(e) => setPromoForm({ ...promoForm, code: e.target.value })} required />
            <select value={promoForm.discountType} onChange={(e) => setPromoForm({ ...promoForm, discountType: e.target.value as 'percent' | 'fixed' })}>
              <option value="percent">%</option>
              <option value="fixed">$ fijo</option>
            </select>
            <input type="number" value={promoForm.discountValue} onChange={(e) => setPromoForm({ ...promoForm, discountValue: Number(e.target.value) })} required />
            <button className="btn-primary" type="submit">Crear</button>
          </form>
          <table className="admin-table">
            <thead><tr><th>Código</th><th>Tipo</th><th>Valor</th></tr></thead>
            <tbody>
              {promos.map((p) => (
                <tr key={p.code}><td>{p.code}</td><td>{p.discount_type}</td><td>{p.discount_value}</td></tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
