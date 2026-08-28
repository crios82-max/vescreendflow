import { useEffect, useState } from 'react';
import type { Ride } from '@ride-app/shared';
import { RIDE_STATUS_LABELS, vehicleTypeLabel } from '@ride-app/shared';
import { api, useAuth } from '@ride-app/web-shared';

type AdminRide = Ride & { passengerName?: string; driverName?: string };

type Tab = 'overview' | 'drivers' | 'users' | 'sos';

export default function Dashboard() {
  const { user, logout } = useAuth();
  const [tab, setTab] = useState<Tab>('overview');
  const [stats, setStats] = useState<{ users: number; drivers: number; rides: number; revenue: number; pendingDrivers: number; sosLast24h: number } | null>(null);
  const [rides, setRides] = useState<AdminRide[]>([]);
  const [pendingDrivers, setPendingDrivers] = useState<Array<{ userId: string; name: string; email: string }>>([]);
  const [users, setUsers] = useState<Array<{ id: string; name: string; email: string; banned?: boolean }>>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.getAdminStats(), api.getAdminRides(), api.getPendingDrivers(), api.getAdminUsers()])
      .then(([s, r, d, u]) => {
        setStats(s);
        setRides(r.rides);
        setPendingDrivers(d.drivers);
        setUsers(u.users);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Sin acceso admin'));
  }, []);

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
        {(['overview', 'drivers', 'users', 'sos'] as Tab[]).map((t) => (
          <button key={t} type="button" className={`tab-btn${tab === t ? ' tab-btn--active' : ''}`} onClick={() => setTab(t)}>
            {t === 'overview' ? 'Resumen' : t === 'drivers' ? `Conductores (${stats.pendingDrivers})` : t === 'users' ? 'Usuarios' : 'SOS'}
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
            <thead><tr><th>Fecha</th><th>Pasajero</th><th>Estado</th><th>Precio</th></tr></thead>
            <tbody>
              {rides.slice(0, 20).map((ride) => (
                <tr key={ride.id}>
                  <td>{new Date(ride.createdAt).toLocaleString()}</td>
                  <td>{ride.passengerName}</td>
                  <td>{RIDE_STATUS_LABELS[ride.status]}</td>
                  <td>${ride.finalPrice ?? ride.estimatedPrice}</td>
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
              <button className="btn-primary" onClick={() => api.approveDriver(d.userId).then(() => setPendingDrivers((p) => p.filter((x) => x.userId !== d.userId)))}>
                Aprobar
              </button>
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
                  {!u.banned && (
                    <button className="btn-secondary" onClick={() => api.banUser(u.id)}>Ban</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {tab === 'sos' && (
        <p className="muted-text">Alertas SOS últimas 24h: {stats.sosLast24h}</p>
      )}
    </div>
  );
}
