import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { RIDE_STATUS_LABELS } from '@ride-app/shared';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:4001';

export default function Share() {
  const { token } = useParams();
  const [data, setData] = useState<{
    ride: { status: string; pickupAddress: string; dropoffAddress: string; etaPickupMin: number | null };
    driverLocation: { lat: number; lng: number } | null;
  } | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return;
    fetch(`${API_URL}/share/${token}`)
      .then((r) => r.json())
      .then((d) => {
        if (d.error) setError(d.error);
        else setData(d);
      })
      .catch(() => setError('No se pudo cargar'));
  }, [token]);

  if (error) return <div className="auth-page"><p className="error-text">{error}</p></div>;
  if (!data) return <div className="auth-page">Cargando viaje compartido...</div>;

  const { ride, driverLocation } = data;
  return (
    <div className="admin-page">
      <h1>Viaje compartido</h1>
      <div className="status-pill">{RIDE_STATUS_LABELS[ride.status as keyof typeof RIDE_STATUS_LABELS] ?? ride.status}</div>
      <div className="meta-row"><span>Origen</span><span>{ride.pickupAddress}</span></div>
      <div className="meta-row"><span>Destino</span><span>{ride.dropoffAddress}</span></div>
      {ride.etaPickupMin != null && (
        <div className="eta-badge">ETA ~{ride.etaPickupMin} min</div>
      )}
      {driverLocation && (
        <p className="muted-text">Conductor: {driverLocation.lat.toFixed(4)}, {driverLocation.lng.toFixed(4)}</p>
      )}
    </div>
  );
}
