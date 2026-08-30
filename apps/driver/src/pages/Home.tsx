import { useEffect, useRef, useState } from 'react';
import type { Ride, VehicleType } from '@ride-app/shared';
import { api, getSocket, GoogleMapsProvider, HistoryPanel, MapView, RatingForm, ChatPanel, useAuth, useI18n, useFlash, LanguageSwitcher, DriverDocsForm } from '@ride-app/web-shared';

interface PendingRide {
  id: string;
  pickupAddress: string;
  pickupLat: number;
  pickupLng: number;
  dropoffAddress: string;
  estimatedPrice: number;
  distanceKm: number;
  vehicleType: string;
}

type Tab = 'rides' | 'history';

export default function Home() {
  const { user, logout } = useAuth();
  const { t, rideStatus, vehicle, te } = useI18n();
  const { show: showFlash } = useFlash();
  const [tab, setTab] = useState<Tab>('rides');
  const [online, setOnline] = useState(false);
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const [pending, setPending] = useState<PendingRide[]>([]);
  const [ride, setRide] = useState<Ride | null>(null);
  const [rated, setRated] = useState(false);
  const [earnings, setEarnings] = useState<{ totalEarnings: number; today: { total: number } } | null>(null);
  const [connectStatus, setConnectStatus] = useState<{ onboarded: boolean } | null>(null);
  const [approvalStatus, setApprovalStatus] = useState<string>('approved');
  const [error, setError] = useState('');
  const watchRef = useRef<number | null>(null);

  const loadPending = () => {
    api.getPendingRides().then((r) => setPending(r.rides)).catch(() => {});
  };

  useEffect(() => {
    api.getActiveRide().then((r) => r.ride && setRide(r.ride)).catch(() => {});
    api.getDriverEarnings().then(setEarnings).catch(() => {});
    api.getConnectStatus().then(setConnectStatus).catch(() => {});
    api.getOnboardingStatus().then((r) => setApprovalStatus(r.approvalStatus)).catch(() => {});
    const socket = getSocket();
    socket.emit('join:drivers');
    socket.on('ride:requested', loadPending);
    socket.on('ride:taken', loadPending);
    return () => {
      socket.off('ride:requested', loadPending);
      socket.off('ride:taken', loadPending);
    };
  }, []);

  useEffect(() => {
    if (!ride) return;
    const socket = getSocket();
    socket.emit('join:ride', ride.id);
    const onUpdate = (updated: Ride) => setRide(updated);
    socket.on('ride:updated', onUpdate);
    return () => {
      socket.off('ride:updated', onUpdate);
    };
  }, [ride?.id]);

  useEffect(() => {
    if (!online) {
      if (watchRef.current != null) navigator.geolocation.clearWatch(watchRef.current);
      return;
    }
    watchRef.current = navigator.geolocation.watchPosition((pos) => {
      const next = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      setPosition(next);
      api.sendLocation(next.lat, next.lng).catch(() => {});
    }, undefined, { enableHighAccuracy: true });
    return () => {
      if (watchRef.current != null) navigator.geolocation.clearWatch(watchRef.current);
    };
  }, [online]);

  const toggleOnline = async () => {
    setError('');
    if (approvalStatus !== 'approved') {
      setError(t('driver.docsRequired'));
      return;
    }
    if (online) {
      await api.goOffline();
      setOnline(false);
      setPending([]);
      return;
    }
    navigator.geolocation.getCurrentPosition(async (pos) => {
      const next = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      setPosition(next);
      await api.goOnline(next.lat, next.lng);
      setOnline(true);
      loadPending();
    }, () => setError(t('common.enableLocation')));
  };

  const acceptRide = async (id: string) => {
    try {
      const accepted = await api.acceptRide(id);
      setRide(accepted);
      setRated(false);
      setPending([]);
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
      loadPending();
    }
  };

  const updateStatus = async (status: 'arriving' | 'in_progress' | 'completed') => {
    if (!ride) return;
    try {
      const updated = await api.updateRideStatus(ride.id, status);
      setRide(updated);
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    }
  };

  const showRating = ride?.status === 'completed' && !rated;

  return (
    <GoogleMapsProvider>
      <div className="map-page">
        <MapView
          pickup={ride ? { lat: ride.pickupLat, lng: ride.pickupLng } : null}
          dropoff={ride ? { lat: ride.dropoffLat, lng: ride.dropoffLng } : null}
          routePolyline={ride?.routePolyline}
          follow={position}
        />
        {ride && (
          <a
            className="btn-secondary"
            style={{ position: 'absolute', bottom: '45%', right: 16, zIndex: 10 }}
            href={`https://www.google.com/maps/dir/?api=1&destination=${ride.status === 'in_progress' ? `${ride.dropoffLat},${ride.dropoffLng}` : `${ride.pickupLat},${ride.pickupLng}`}`}
            target="_blank"
            rel="noreferrer"
          >
            {t('common.navigate')}
          </a>
        )}
        <div className="top-bar">
          <span className={`badge${online ? ' badge--accent' : ''}`}>{online ? t('driver.online') : t('driver.offline')} — {user?.name}</span>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <LanguageSwitcher />
            <button className="btn-secondary" onClick={() => setTab(tab === 'rides' ? 'history' : 'rides')}>
              {tab === 'rides' ? t('common.history') : t('driver.rides')}
            </button>
            <button className="btn-secondary" onClick={logout}>{t('common.logout')}</button>
          </div>
        </div>
        <div className="bottom-sheet">
          {tab === 'history' ? (
            <>
              <h2>{t('driver.rideHistory')}</h2>
              <HistoryPanel />
            </>
          ) : !ride ? (
            <>
              <h2>{online ? t('driver.availableRides') : t('driver.goOnlineHint')}</h2>
              <DriverDocsForm onUpdated={() => api.getOnboardingStatus().then((r) => setApprovalStatus(r.approvalStatus))} />
              {earnings && (
                <div className="meta-row"><span>{t('driver.todayEarnings')}</span><span>${earnings.today.total.toFixed(2)}</span></div>
              )}
              {connectStatus && !connectStatus.onboarded && (
                <button className="btn-secondary" onClick={async () => {
                  const r = await api.startConnectOnboarding();
                  if (r.url) window.location.href = r.url;
                  else showFlash(te(r.message ?? t('common.stripeNotConfigured')), 'error');
                }}>
                  {t('driver.setupStripe')}
                </button>
              )}
              <button className="btn-primary" onClick={toggleOnline}>
                {online ? t('driver.goOffline') : t('driver.goOnline')}
              </button>
              {error && <p className="error-text">{error}</p>}
              <div className="ride-list">
                {pending.map((p) => (
                  <div className="ride-card" key={p.id}>
                    <strong>${p.estimatedPrice} — {p.distanceKm} {t('common.km')} · {vehicle(p.vehicleType as VehicleType)}</strong>
                    <div className="meta-row"><span>{t('common.origin')}</span><span>{p.pickupAddress}</span></div>
                    <div className="meta-row"><span>{t('common.destination')}</span><span>{p.dropoffAddress}</span></div>
                    <button className="btn-primary" onClick={() => acceptRide(p.id)}>{t('common.accept')}</button>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <>
              <div className="status-pill">{rideStatus(ride.status)}</div>
              <div className="meta-row"><span>{t('common.type')}</span><span>{vehicle(ride.vehicleType)}</span></div>
              <div className="meta-row"><span>{t('common.earnings')}</span><span>${ride.estimatedPrice}</span></div>
              {ride.status === 'accepted' && (
                <button className="btn-primary" onClick={() => updateStatus('arriving')}>{t('driver.onTheWay')}</button>
              )}
              {ride.status === 'arriving' && (
                <button className="btn-primary" onClick={() => updateStatus('in_progress')}>{t('driver.startRide')}</button>
              )}
              {ride.status === 'in_progress' && (
                <button className="btn-primary" onClick={() => updateStatus('completed')}>{t('driver.completeRide')}</button>
              )}
              {['accepted', 'arriving', 'in_progress'].includes(ride.status) && (
                <>
                  <button className="btn-secondary" type="button" onClick={async () => {
                    const c = await api.initiateMaskedCall(ride.id);
                    if (c.initiated) showFlash(te(c.message ?? t('common.callConnecting')));
                    else if (c.dialUrl) window.location.href = c.dialUrl;
                    else showFlash(te(c.hint ?? t('common.callFailed')), 'error');
                  }}>{t('driver.callPassenger')}</button>
                  <ChatPanel rideId={ride.id} />
                </>
              )}
              {showRating && (
                <RatingForm
                  title={t('common.ratePassenger')}
                  onSubmit={async (stars, comment) => {
                    await api.rateRide(ride.id, stars, comment);
                    setRated(true);
                  }}
                />
              )}
              {ride.status === 'completed' && rated && (
                <button className="btn-primary" onClick={() => { setRide(null); setRated(false); }}>{t('common.done')}</button>
              )}
            </>
          )}
        </div>
      </div>
    </GoogleMapsProvider>
  );
}
