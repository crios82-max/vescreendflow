import { useEffect, useState } from 'react';
import type { Ride } from '@ride-app/shared';
import { api } from './api';
import { useI18n } from './I18nProvider';

export function HistoryPanel() {
  const { t, rideStatus, vehicle } = useI18n();
  const [rides, setRides] = useState<Ride[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.getHistory()
      .then((data) => setRides(data.rides))
      .catch(() => setRides([]))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="muted-text">{t('common.loadingHistory')}</p>;
  if (rides.length === 0) return <p className="muted-text">{t('common.noPastRides')}</p>;

  return (
    <div className="history-list">
      {rides.map((ride) => (
        <div className="history-card" key={ride.id}>
          <div className="meta-row">
            <span>{new Date(ride.createdAt).toLocaleDateString()}</span>
            <span>{rideStatus(ride.status)}</span>
          </div>
          <div className="meta-row"><span>{t('common.type')}</span><span>{vehicle(ride.vehicleType)}{ride.serviceMode === 'delivery' ? ` · ${t('service.foodDelivery')}` : ''}</span></div>
          {ride.deliveryNotes && (
            <div className="meta-row"><span>{t('service.foodOrder')}</span><span>{ride.deliveryNotes}</span></div>
          )}
          <div className="meta-row"><span>{t('common.origin')}</span><span>{ride.pickupAddress}</span></div>
          <div className="meta-row"><span>{t('common.destination')}</span><span>{ride.dropoffAddress}</span></div>
          <div className="meta-row">
            <span>{t('common.price')}</span>
            <span>${ride.finalPrice ?? ride.estimatedPrice}</span>
          </div>
        </div>
      ))}
    </div>
  );
}
