import { useEffect, useRef, useState } from 'react';
import type { Ride, VehicleType } from '@ride-app/shared';
import { RIDE_STATUS_LABELS, vehicleTypeLabel } from '@ride-app/shared';
import { api, getSocket, GoogleMapsProvider, HistoryPanel, MapView, RatingForm, ChatPanel, useAuth } from '@ride-app/web-shared';

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
  const [tab, setTab] = useState<Tab>('rides');
  const [online, setOnline] = useState(false);
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const [pending, setPending] = useState<PendingRide[]>([]);
  const [ride, setRide] = useState<Ride | null>(null);
  const [rated, setRated] = useState(false);
  const [earnings, setEarnings] = useState<{ totalEarnings: number; today: { total: number } } | null>(null);
  const [connectStatus, setConnectStatus] = useState<{ onboarded: boolean } | null>(null);
  const [error, setError] = useState('');
  const watchRef = useRef<number | null>(null);

  const loadPending = () => {
    api.getPendingRides().then((r) => setPending(r.rides)).catch(() => {});
  };

  useEffect(() => {
    api.getActiveRide().then((r) => r.ride && setRide(r.ride)).catch(() => {});
    api.getDriverEarnings().then(setEarnings).catch(() => {});
    api.getConnectStatus().then(setConnectStatus).catch(() => {});
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
    }, () => setError('Activa la ubicación'));
  };

  const acceptRide = async (id: string) => {
    try {
      const accepted = await api.acceptRide(id);
      setRide(accepted);
      setRated(false);
      setPending([]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
      loadPending();
    }
  };

  const updateStatus = async (status: 'arriving' | 'in_progress' | 'completed') => {
    if (!ride) return;
    const updated = await api.updateRideStatus(ride.id, status);
    setRide(updated);
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
            Navegar
          </a>
        )}
        <div className="top-bar">
          <span className="badge">{online ? '🟢 En línea' : '⚫ Offline'} — {user?.name}</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-secondary" onClick={() => setTab(tab === 'rides' ? 'history' : 'rides')}>
              {tab === 'rides' ? 'Historial' : 'Viajes'}
            </button>
            <button className="btn-secondary" onClick={logout}>Salir</button>
          </div>
        </div>
        <div className="bottom-sheet">
          {tab === 'history' ? (
            <>
              <h2>Historial</h2>
              <HistoryPanel />
            </>
          ) : !ride ? (
            <>
              <h2>{online ? 'Viajes disponibles' : 'Ponte en línea para recibir viajes'}</h2>
              {earnings && (
                <div className="meta-row"><span>Ganancias hoy</span><span>${earnings.today.total.toFixed(2)}</span></div>
              )}
              {connectStatus && !connectStatus.onboarded && (
                <button className="btn-secondary" onClick={async () => {
                  const r = await api.startConnectOnboarding();
                  if (r.url) window.location.href = r.url;
                  else alert(r.message ?? 'Stripe Connect no configurado');
                }}>
                  Configurar cobros (Stripe)
                </button>
              )}
              <button className="btn-primary" onClick={toggleOnline}>
                {online ? 'Ir offline' : 'Ir online'}
              </button>
              {error && <p className="error-text">{error}</p>}
              <div className="ride-list">
                {pending.map((p) => (
                  <div className="ride-card" key={p.id}>
                    <strong>${p.estimatedPrice} — {p.distanceKm} km · {vehicleTypeLabel(p.vehicleType as VehicleType)}</strong>
                    <div className="meta-row"><span>Origen</span><span>{p.pickupAddress}</span></div>
                    <div className="meta-row"><span>Destino</span><span>{p.dropoffAddress}</span></div>
                    <button className="btn-primary" onClick={() => acceptRide(p.id)}>Aceptar</button>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <>
              <div className="status-pill">{RIDE_STATUS_LABELS[ride.status]}</div>
              <div className="meta-row"><span>Tipo</span><span>{vehicleTypeLabel(ride.vehicleType)}</span></div>
              <div className="meta-row"><span>Ganancia</span><span>${ride.estimatedPrice}</span></div>
              {ride.status === 'accepted' && (
                <button className="btn-primary" onClick={() => updateStatus('arriving')}>Voy en camino</button>
              )}
              {ride.status === 'arriving' && (
                <button className="btn-primary" onClick={() => updateStatus('in_progress')}>Iniciar viaje</button>
              )}
              {ride.status === 'in_progress' && (
                <button className="btn-primary" onClick={() => updateStatus('completed')}>Completar viaje</button>
              )}
              {['accepted', 'arriving', 'in_progress'].includes(ride.status) && (
                <>
                  <button className="btn-secondary" type="button" onClick={async () => {
                    const c = await api.initiateMaskedCall(ride.id);
                    if (c.initiated) alert(c.message ?? 'Te llamamos para conectar');
                    else if (c.dialUrl) window.location.href = c.dialUrl;
                    else alert(c.hint ?? 'No se pudo llamar');
                  }}>Llamar pasajero</button>
                  <ChatPanel rideId={ride.id} />
                </>
              )}
              {showRating && (
                <RatingForm
                  title="Califica al pasajero"
                  onSubmit={async (stars, comment) => {
                    await api.rateRide(ride.id, stars, comment);
                    setRated(true);
                  }}
                />
              )}
              {ride.status === 'completed' && rated && (
                <button className="btn-primary" onClick={() => { setRide(null); setRated(false); }}>Listo</button>
              )}
            </>
          )}
        </div>
      </div>
    </GoogleMapsProvider>
  );
}
