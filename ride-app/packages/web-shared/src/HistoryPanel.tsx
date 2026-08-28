import { useEffect, useState } from 'react';
import type { Ride } from '@ride-app/shared';
import { RIDE_STATUS_LABELS, vehicleTypeLabel } from '@ride-app/shared';
import { api } from './api';

export function HistoryPanel() {
  const [rides, setRides] = useState<Ride[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getHistory()
      .then((data) => setRides(data.rides))
      .catch(() => setRides([]))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="muted-text">Cargando historial...</p>;
  if (rides.length === 0) return <p className="muted-text">Sin viajes anteriores</p>;

  return (
    <div className="history-list">
      {rides.map((ride) => (
        <div className="history-card" key={ride.id}>
          <div className="meta-row">
            <span>{new Date(ride.createdAt).toLocaleDateString()}</span>
            <span>{RIDE_STATUS_LABELS[ride.status]}</span>
          </div>
          <div className="meta-row"><span>Tipo</span><span>{vehicleTypeLabel(ride.vehicleType)}</span></div>
          <div className="meta-row"><span>Origen</span><span>{ride.pickupAddress}</span></div>
          <div className="meta-row"><span>Destino</span><span>{ride.dropoffAddress}</span></div>
          <div className="meta-row">
            <span>Precio</span>
            <span>${ride.finalPrice ?? ride.estimatedPrice}</span>
          </div>
        </div>
      ))}
    </div>
  );

}
