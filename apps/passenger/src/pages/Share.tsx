import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import type { RideStatus } from '@ride-app/shared';
import { GoogleMapsProvider, MapView, useI18n, LanguageSwitcher } from '@ride-app/web-shared';

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
  const { t, te, rideStatus } = useI18n();
  const [data, setData] = useState<ShareData | null>(null);
  const [error, setError] = useState('');

  const load = () => {
    if (!token) return;
    fetch(`${API_URL}/share/${token}`)
      .then((r) => r.json())
      .then((d) => {
        if (d.error) setError(te(d.error));
        else setData(d);
      })
      .catch(() => setError(t('common.loadFailed')));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, [token]);

  if (error) {
    return (
      <div className="auth-page">
        <LanguageSwitcher className="auth-page__lang" />
        <p className="error-text">{error}</p>
      </div>
    );
  }
  if (!data) {
    return (
      <div className="auth-page">
        <LanguageSwitcher className="auth-page__lang" />
        {t('share.loading')}
      </div>
    );
  }

  const { ride, driverLocation } = data;
  const active = ['accepted', 'arriving', 'in_progress'].includes(ride.status);

  return (
    <GoogleMapsProvider>
      <div className="map-page" style={{ minHeight: '100vh' }}>
        <div style={{ position: 'absolute', top: 16, right: 16, zIndex: 20 }}>
          <LanguageSwitcher />
        </div>
        <MapView
          pickup={{ lat: ride.pickupLat, lng: ride.pickupLng }}
          dropoff={{ lat: ride.dropoffLat, lng: ride.dropoffLng }}
          routePolyline={ride.routePolyline}
          driver={driverLocation}
        />
        <div className="bottom-sheet">
          <h1>{t('share.title')}</h1>
          <div className="status-pill">{rideStatus(ride.status as RideStatus)}</div>
          <div className="meta-row"><span>{t('common.origin')}</span><span>{ride.pickupAddress}</span></div>
          <div className="meta-row"><span>{t('common.destination')}</span><span>{ride.dropoffAddress}</span></div>
          {ride.etaPickupMin != null && active && (
            <div className="eta-badge">{t('common.arriveIn', { min: ride.etaPickupMin })}</div>
          )}
          {driverLocation && active && (
            <p className="muted-text">{t('share.driverMoving')}</p>
          )}
        </div>
      </div>
    </GoogleMapsProvider>
  );
}
