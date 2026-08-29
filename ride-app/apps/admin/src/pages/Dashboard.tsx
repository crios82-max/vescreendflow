import { useEffect, useState } from 'react';
import type { Ride } from '@ride-app/shared';
import { api, useAuth, BrandMark, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

type AdminRide = Ride & { passengerName?: string; driverName?: string };

type Tab = 'overview' | 'drivers' | 'users' | 'sos' | 'promos';

export default function Dashboard() {
  const { user, logout } = useAuth();
  const { t, rideStatus } = useI18n();
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
      .catch((err) => setError(err instanceof Error ? err.message : t('common.noAdminAccess')));
  };

  useEffect(() => { reload(); }, []);

  if (error) {
    return (
      <div className="admin-page">
        <h1>{t('common.accessDenied')}</h1>
        <p className="error-text">{error}</p>
        <button className="btn-secondary" onClick={logout}>{t('common.logout')}</button>
      </div>
    );
  }

  if (!stats) return <div className="admin-page">{t('common.loading')}</div>;

  const tabLabel = (key: Tab) => {
    if (key === 'overview') return t('admin.overview');
    if (key === 'drivers') return t('admin.driversPending', { count: stats.pendingDrivers });
    if (key === 'users') return t('admin.users');
    if (key === 'sos') return t('admin.sosTab', { count: stats.sosLast24h });
    return t('admin.promos');
  };

  return (
    <div className="admin-page">
      <header className="admin-header">
        <BrandMark size="md" />
        <div className="admin-header__meta">
          <p className="admin-header__title">{t('admin.title')}</p>
          <p className="admin-header__user">{user?.name}</p>
        </div>
        <LanguageSwitcher />
        <button className="btn-secondary" type="button" onClick={logout}>{t('common.logout')}</button>
      </header>

      <div className="tab-row">
        {(['overview', 'drivers', 'users', 'sos', 'promos'] as Tab[]).map((key) => (
          <button key={key} type="button" className={`tab-btn${tab === key ? ' tab-btn--active' : ''}`} onClick={() => setTab(key)}>
            {tabLabel(key)}
          </button>
        ))}
      </div>

      {tab === 'overview' && (
        <>
          <div className="admin-grid">
            <div className="stat-card"><span>{t('admin.usersCount')}</span><strong>{stats.users}</strong></div>
            <div className="stat-card"><span>{t('admin.driversCount')}</span><strong>{stats.drivers}</strong></div>
            <div className="stat-card"><span>{t('admin.ridesCount')}</span><strong>{stats.rides}</strong></div>
            <div className="stat-card"><span>{t('admin.revenue')}</span><strong>${stats.revenue.toFixed(2)}</strong></div>
            <div className="stat-card"><span>{t('admin.sos24h')}</span><strong>{stats.sosLast24h}</strong></div>
          </div>
          <h2 className="admin-section-title">{t('admin.recentRides')}</h2>
          <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>{t('common.date')}</th><th>{t('admin.passenger')}</th><th>{t('common.status')}</th><th>{t('common.payment')}</th><th>{t('common.price')}</th><th></th></tr></thead>
            <tbody>
              {rides.slice(0, 20).map((ride) => (
                <tr key={ride.id}>
                  <td>{new Date(ride.createdAt).toLocaleString()}</td>
                  <td>{ride.passengerName}</td>
                  <td>{rideStatus(ride.status)}</td>
                  <td>{ride.paymentStatus}</td>
                  <td>${ride.finalPrice ?? ride.estimatedPrice}</td>
                  <td>
                    {ride.paymentStatus === 'paid' && (
                      <button className="btn-secondary" onClick={() => api.refundRide(ride.id).then(reload)}>{t('common.refund')}</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </>
      )}

      {tab === 'drivers' && (
        <div className="history-list">
          {pendingDrivers.length === 0 ? <p className="muted-text">{t('common.noPendingDrivers')}</p> : pendingDrivers.map((d) => (
            <div key={d.userId} className="history-card">
              <strong>{d.name}</strong>
              <span>{d.email}</span>
              {d.licenseUrl && <a href={d.licenseUrl} target="_blank" rel="noreferrer">{t('common.license')}</a>}
              {d.idUrl && <a href={d.idUrl} target="_blank" rel="noreferrer">ID</a>}
              {d.vehiclePhotoUrl && <a href={d.vehiclePhotoUrl} target="_blank" rel="noreferrer">{t('common.vehiclePhoto')}</a>}
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn-primary" onClick={() => api.approveDriver(d.userId).then(reload)}>{t('common.approve')}</button>
                <button className="btn-secondary" onClick={() => api.rejectDriver(d.userId, t('admin.invalidDocs')).then(reload)}>{t('common.reject')}</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'users' && (
        <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>{t('common.name')}</th><th>{t('common.email')}</th><th>{t('common.status')}</th><th></th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>{u.banned ? t('common.banned') : t('common.active')}</td>
                <td>
                  {u.banned ? (
                    <button className="btn-secondary" onClick={() => api.unbanUser(u.id).then(reload)}>{t('common.unban')}</button>
                  ) : (
                    <button className="btn-secondary" onClick={() => api.banUser(u.id).then(reload)}>{t('common.ban')}</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}

      {tab === 'sos' && (
        <div className="history-list">
          {sosEvents.length === 0 ? <p className="muted-text">{t('common.noSosAlerts')}</p> : sosEvents.map((e) => (
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
            <h3>{t('admin.newPromo')}</h3>
            <input placeholder={t('common.code')} value={promoForm.code} onChange={(e) => setPromoForm({ ...promoForm, code: e.target.value })} required />
            <select value={promoForm.discountType} onChange={(e) => setPromoForm({ ...promoForm, discountType: e.target.value as 'percent' | 'fixed' })}>
              <option value="percent">{t('common.percent')}</option>
              <option value="fixed">{t('common.fixedAmount')}</option>
            </select>
            <input type="number" value={promoForm.discountValue} onChange={(e) => setPromoForm({ ...promoForm, discountValue: Number(e.target.value) })} required />
            <button className="btn-primary" type="submit">{t('common.create')}</button>
          </form>
          <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>{t('common.code')}</th><th>{t('common.type')}</th><th>{t('common.price')}</th></tr></thead>
            <tbody>
              {promos.map((p) => (
                <tr key={p.code}><td>{p.code}</td><td>{p.discount_type}</td><td>{p.discount_value}</td></tr>
              ))}
            </tbody>
          </table>
          </div>
        </>
      )}
    </div>
  );
}
