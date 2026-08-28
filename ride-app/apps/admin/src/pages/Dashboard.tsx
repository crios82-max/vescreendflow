import { useEffect, useState } from 'react';
import type { Ride } from '@ride-app/shared';
import { RIDE_STATUS_LABELS, vehicleTypeLabel } from '@ride-app/shared';
import { api, useAuth } from '@ride-app/web-shared';

type AdminRide = Ride & { passengerName?: string; driverName?: string };

export default function Dashboard() {
  const { user, logout } = useAuth();
  const [stats, setStats] = useState<{ users: number; drivers: number; rides: number; revenue: number } | null>(null);
  const [rides, setRides] = useState<AdminRide[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([api.getAdminStats(), api.getAdminRides()])
      .then(([s, r]) => {
        setStats(s);
        setRides(r.rides);
      })
      .catch((err) => setError(err instanceof Error ? err.message : 'Sin acceso admin'));
  }, []);

  if (error) {
    return (
      <div className="admin-page">
        <h1>Acceso denegado</h1>
        <p className="error-text">{error}</p>
        <p className="muted-text">Marca tu usuario como admin: UPDATE users SET is_admin = true WHERE email = '...';</p>
        <button className="btn-secondary" onClick={logout}>Salir</button>
      </div>
    );
  }

  if (!stats) {
    return <div className="admin-page">Cargando...</div>;
  }

  return (
    <div className="admin-page">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h1>Admin</h1>
          <p className="muted-text">{user?.name} · {user?.email}</p>
        </div>
        <button className="btn-secondary" onClick={logout}>Salir</button>
      </div>

      <div className="admin-grid">
        <div className="stat-card"><span>Usuarios</span><strong>{stats.users}</strong></div>
        <div className="stat-card"><span>Conductores</span><strong>{stats.drivers}</strong></div>
        <div className="stat-card"><span>Viajes</span><strong>{stats.rides}</strong></div>
        <div className="stat-card"><span>Ingresos</span><strong>${stats.revenue.toFixed(2)}</strong></div>
      </div>

      <h2>Últimos viajes</h2>
      <table className="admin-table">
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Pasajero</th>
            <th>Conductor</th>
            <th>Estado</th>
            <th>Tipo</th>
            <th>Precio</th>
          </tr>
        </thead>
        <tbody>
          {rides.map((ride) => (
            <tr key={ride.id}>
              <td>{new Date(ride.createdAt).toLocaleString()}</td>
              <td>{ride.passengerName ?? '—'}</td>
              <td>{ride.driverName ?? '—'}</td>
              <td>{RIDE_STATUS_LABELS[ride.status]}</td>
              <td>{vehicleTypeLabel(ride.vehicleType)}</td>
              <td>${ride.finalPrice ?? ride.estimatedPrice}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
