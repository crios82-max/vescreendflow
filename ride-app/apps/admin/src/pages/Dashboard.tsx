import { useEffect, useState } from 'react';
import type { Ride } from '@ride-app/shared';
import { api, useAuth, BrandMark, useI18n, useFlash, LanguageSwitcher } from '@ride-app/web-shared';

type AdminRide = Ride & { passengerName?: string; driverName?: string };

type Tab = 'overview' | 'drivers' | 'users' | 'sos' | 'promos';

export default function Dashboard() {
  const { user, logout } = useAuth();
  const { t, rideStatus, te } = useI18n();
  const { show: showFlash } = useFlash();
  const [tab, setTab] = useState<Tab>('overview');
  const [stats, setStats] = useState<{ users: number; drivers: number; rides: number; revenue: number; pendingDrivers: number; sosLast24h: number } | null>(null);
  const [rides, setRides] = useState<AdminRide[]>([]);
  const [pendingDrivers, setPendingDrivers] = useState<Array<{ userId: string; name: string; email: string; licenseUrl?: string; idUrl?: string; vehiclePhotoUrl?: string }>>([]);
  const [users, setUsers] = useState<Array<{ id: string; name: string; email: string; banned?: boolean }>>([]);
  const [sosEvents, setSosEvents] = useState<Array<{ id: string; name: string; pickup_address: string; created_at: string; acknowledged_at: string | null }>>([]);
  const [promos, setPromos] = useState<Array<{ code: string; discount_type: string; discount_value: number; active?: boolean }>>([]);
  const [promoForm, setPromoForm] = useState({ code: '', discountType: 'percent' as 'percent' | 'fixed', discountValue: 10 });
  const [error, setError] = useState('');
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [ridesQuery, setRidesQuery] = useState('');
  const [usersQuery, setUsersQuery] = useState('');
  const [debouncedRidesQuery, setDebouncedRidesQuery] = useState('');
  const [debouncedUsersQuery, setDebouncedUsersQuery] = useState('');
  const [ridesPage, setRidesPage] = useState(0);
  const [usersPage, setUsersPage] = useState(0);
  const [ridesTotal, setRidesTotal] = useState(0);
  const [usersTotal, setUsersTotal] = useState(0);
  const pageSize = 20;

  const reload = (ridesQ = debouncedRidesQuery, usersQ = debouncedUsersQuery, rPage = ridesPage, uPage = usersPage) => {
    Promise.all([
      api.getAdminStats(),
      api.getAdminRides({ q: ridesQ || undefined, limit: pageSize, offset: rPage * pageSize }),
      api.getPendingDrivers(),
      api.getAdminUsers({ q: usersQ || undefined, limit: pageSize, offset: uPage * pageSize }),
      api.getAdminSos(),
      api.getAdminPromos(),
    ])
      .then(([s, r, d, u, sos, p]) => {
        setStats(s);
        setRides(r.rides);
        setRidesTotal(r.total);
        setPendingDrivers(d.drivers);
        setUsers(u.users);
        setUsersTotal(u.total);
        setSosEvents(sos.events);
        setPromos(p.promos);
      })
      .catch((err) => setError(te(err instanceof Error ? err.message : t('common.noAdminAccess'))));
  };

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedRidesQuery(ridesQuery.trim());
      setRidesPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [ridesQuery]);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedUsersQuery(usersQuery.trim());
      setUsersPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [usersQuery]);

  useEffect(() => {
    reload();
  }, [ridesPage, usersPage, debouncedRidesQuery, debouncedUsersQuery]);

  const runAction = async (key: string, fn: () => Promise<unknown>) => {
    setActionLoading(key);
    try {
      await fn();
      reload();
    } catch (err) {
      showFlash(te(err instanceof Error ? err.message : t('common.error')), 'error');
    } finally {
      setActionLoading(null);
    }
  };

  const paymentLabel = (status: string) => (status === 'paid' ? t('common.paid') : t('common.pending'));

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
          <input
            className="place-input"
            placeholder={t('common.searchPlaceholder')}
            value={ridesQuery}
            onChange={(e) => setRidesQuery(e.target.value)}
            aria-label={t('common.searchPlaceholder')}
            style={{ marginBottom: 12, maxWidth: 320 }}
          />
          <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>{t('common.date')}</th><th>{t('admin.passenger')}</th><th>{t('common.driverColumn')}</th><th>{t('common.status')}</th><th>{t('common.payment')}</th><th>{t('common.price')}</th><th></th></tr></thead>
            <tbody>
              {rides.length === 0 ? (
                <tr><td colSpan={7} className="muted-text">{t('admin.noRides')}</td></tr>
              ) : rides.map((ride) => (
                <tr key={ride.id}>
                  <td>{new Date(ride.createdAt).toLocaleString()}</td>
                  <td>{ride.passengerName}</td>
                  <td>{ride.driverName ?? '—'}</td>
                  <td>{rideStatus(ride.status)}</td>
                  <td>{paymentLabel(ride.paymentStatus)}</td>
                  <td>${ride.finalPrice ?? ride.estimatedPrice}</td>
                  <td>
                    {ride.paymentStatus === 'paid' && (
                      <button
                        className="btn-secondary"
                        disabled={actionLoading === `refund-${ride.id}`}
                        onClick={() => runAction(`refund-${ride.id}`, () => api.refundRide(ride.id))}
                      >
                        {actionLoading === `refund-${ride.id}` ? t('common.processing') : t('common.refund')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 12 }}>
            <button className="btn-secondary" type="button" disabled={ridesPage === 0} onClick={() => setRidesPage((p) => p - 1)} aria-label={t('common.prevPage')}>{t('common.prevPage')}</button>
            <span className="muted-text">{t('common.pageOf', { page: ridesPage + 1 })} · {ridesTotal}</span>
            <button className="btn-secondary" type="button" disabled={(ridesPage + 1) * pageSize >= ridesTotal} onClick={() => setRidesPage((p) => p + 1)} aria-label={t('common.nextPage')}>{t('common.nextPage')}</button>
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
              {d.idUrl && <a href={d.idUrl} target="_blank" rel="noreferrer">{t('common.idPhoto')}</a>}
              {d.vehiclePhotoUrl && <a href={d.vehiclePhotoUrl} target="_blank" rel="noreferrer">{t('common.vehiclePhoto')}</a>}
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  className="btn-primary"
                  disabled={actionLoading === `approve-${d.userId}`}
                  onClick={() => runAction(`approve-${d.userId}`, () => api.approveDriver(d.userId))}
                >
                  {actionLoading === `approve-${d.userId}` ? t('common.processing') : t('common.approve')}
                </button>
                <button
                  className="btn-secondary"
                  disabled={actionLoading === `reject-${d.userId}`}
                  onClick={() => runAction(`reject-${d.userId}`, () => api.rejectDriver(d.userId, t('admin.invalidDocs')))}
                >
                  {actionLoading === `reject-${d.userId}` ? t('common.processing') : t('common.reject')}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === 'users' && (
        <>
        <input
          className="place-input"
          placeholder={t('common.searchPlaceholder')}
          value={usersQuery}
          onChange={(e) => setUsersQuery(e.target.value)}
          aria-label={t('common.searchPlaceholder')}
          style={{ marginBottom: 12, maxWidth: 320 }}
        />
        <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>{t('common.name')}</th><th>{t('common.email')}</th><th>{t('common.status')}</th><th></th></tr></thead>
          <tbody>
            {users.length === 0 ? (
              <tr><td colSpan={4} className="muted-text">{t('admin.noUsers')}</td></tr>
            ) : users.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>{u.banned ? t('common.banned') : t('common.active')}</td>
                <td>
                  {u.banned ? (
                    <button
                      className="btn-secondary"
                      disabled={actionLoading === `unban-${u.id}`}
                      onClick={() => runAction(`unban-${u.id}`, () => api.unbanUser(u.id))}
                    >
                      {actionLoading === `unban-${u.id}` ? t('common.processing') : t('common.unban')}
                    </button>
                  ) : (
                    <button
                      className="btn-secondary"
                      disabled={actionLoading === `ban-${u.id}`}
                      onClick={() => runAction(`ban-${u.id}`, () => api.banUser(u.id))}
                    >
                      {actionLoading === `ban-${u.id}` ? t('common.processing') : t('common.ban')}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 12 }}>
          <button className="btn-secondary" type="button" disabled={usersPage === 0} onClick={() => setUsersPage((p) => p - 1)} aria-label={t('common.prevPage')}>{t('common.prevPage')}</button>
          <span className="muted-text">{t('common.pageOf', { page: usersPage + 1 })} · {usersTotal}</span>
          <button className="btn-secondary" type="button" disabled={(usersPage + 1) * pageSize >= usersTotal} onClick={() => setUsersPage((p) => p + 1)} aria-label={t('common.nextPage')}>{t('common.nextPage')}</button>
        </div>
        </>
      )}

      {tab === 'sos' && (
        <div className="history-list">
          {sosEvents.length === 0 ? <p className="muted-text">{t('common.noSosAlerts')}</p> : sosEvents.map((e) => (
            <div key={e.id} className="history-card">
              <strong>{e.name}</strong>
              <span>{new Date(e.created_at).toLocaleString()}</span>
              <span>{e.pickup_address}</span>
              {e.acknowledged_at ? (
                <span className="muted-text">{t('admin.sosAcked')} · {new Date(e.acknowledged_at).toLocaleString()}</span>
              ) : (
                <button
                  className="btn-primary"
                  disabled={actionLoading === `ack-${e.id}`}
                  onClick={() => runAction(`ack-${e.id}`, () => api.acknowledgeSos(e.id))}
                >
                  {actionLoading === `ack-${e.id}` ? t('common.processing') : t('admin.ackSos')}
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {tab === 'promos' && (
        <>
          <form className="auth-card" style={{ marginBottom: 16 }} onSubmit={(ev) => {
            ev.preventDefault();
            runAction('create-promo', () => api.createPromo(promoForm));
          }}>
            <h3>{t('admin.newPromo')}</h3>
            <input placeholder={t('common.code')} value={promoForm.code} onChange={(e) => setPromoForm({ ...promoForm, code: e.target.value })} required />
            <select value={promoForm.discountType} onChange={(e) => setPromoForm({ ...promoForm, discountType: e.target.value as 'percent' | 'fixed' })}>
              <option value="percent">{t('common.percent')}</option>
              <option value="fixed">{t('common.fixedAmount')}</option>
            </select>
            <input type="number" value={promoForm.discountValue} onChange={(e) => setPromoForm({ ...promoForm, discountValue: Number(e.target.value) })} required />
            <button className="btn-primary" type="submit" disabled={actionLoading === 'create-promo'}>
              {actionLoading === 'create-promo' ? t('common.processing') : t('common.create')}
            </button>
          </form>
          <div className="admin-table-wrap">
          <table className="admin-table">
            <thead><tr><th>{t('common.code')}</th><th>{t('common.type')}</th><th>{t('common.price')}</th><th>{t('common.status')}</th><th></th></tr></thead>
            <tbody>
              {promos.length === 0 ? (
                <tr><td colSpan={5} className="muted-text">{t('admin.noPromos')}</td></tr>
              ) : promos.map((p) => (
                <tr key={p.code}>
                  <td>{p.code}</td>
                  <td>{p.discount_type}</td>
                  <td>{p.discount_value}</td>
                  <td>{p.active === false ? t('admin.promoInactive') : t('admin.promoActive')}</td>
                  <td>
                    {p.active !== false && (
                      <button
                        className="btn-secondary"
                        disabled={actionLoading === `promo-${p.code}`}
                        onClick={() => runAction(`promo-${p.code}`, () => api.deactivatePromo(p.code))}
                      >
                        {actionLoading === `promo-${p.code}` ? t('common.processing') : t('admin.deactivatePromo')}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        </>
      )}
    </div>
  );
}
