import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { RIDE_STATUS_LABELS } from '@ride-app/shared';
import { GoogleMapsProvider, MapView } from '@ride-app/web-shared';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:4001';

type ShareData = {
  ride: {
    status: string;
    pickupAddress: string;
    dropoffAddress: string;
    pickupLat: number;
    pickupLng: number;
    dropoffLat: number;
    dropoffLng: number;
    routePolyline: string | null;
    etaPickupMin: number | null;
  };
  driverLocation: { lat: number; lng: number } | null;
};

export default function Share() {
  const { token } = useParams();
  const [data, setData] = useState<ShareData | null>(null);
  const [error, setError] = useState('');

  const load = () => {
    if (!token) return;
    fetch(`${API_URL}/share/${token}`)
      .then((r) => r.json())
      .then((d) => {
        if (d.error) setError(d.error);
        else setData(d);
      })
      .catch(() => setError('No se pudo cargar'));
  };

  useEffect(() => {
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, [token]);

  if (error) return <div className="auth-page"><p className="error-text">{error}</p></div>;
  if (!data) return <div className="auth-page">Cargando viaje compartido...</div>;

  const { ride, driverLocation } = data;
  const active = ['accepted', 'arriving', 'in_progress'].includes(ride.status);

  return (
    <GoogleMapsProvider>
      <div className="map-page" style={{ minHeight: '100vh' }}>
        <MapView
          pickup={{ lat: ride.pickupLat, lng: ride.pickupLng }}
          dropoff={{ lat: ride.dropoffLat, lng: ride.dropoffLng }}
          routePolyline={ride.routePolyline}
          driver={driverLocation}
        />
        <div className="bottom-sheet">
          <h1>Viaje compartido</h1>
          <div className="status-pill">{RIDE_STATUS_LABELS[ride.status as keyof typeof RIDE_STATUS_LABELS] ?? ride.status}</div>
          <div className="meta-row"><span>Origen</span><span>{ride.pickupAddress}</span></div>
          <div className="meta-row"><span>Destino</span><span>{ride.dropoffAddress}</span></div>
          {ride.etaPickupMin != null && active && (
            <div className="eta-badge">ETA ~{ride.etaPickupMin} min</div>
          )}
          {driverLocation && active && (
            <p className="muted-text">Conductor en movimiento — actualización cada 5s</p>
          )}
        </div>
      </div>
    </GoogleMapsProvider>
  );
}
