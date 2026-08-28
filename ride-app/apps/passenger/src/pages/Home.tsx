import { useEffect, useState } from 'react';
import type { Ride, RideEstimate, VehicleType } from '@ride-app/shared';
import { RIDE_STATUS_LABELS } from '@ride-app/shared';
import {
  api,
  getSocket,
  GoogleMapsProvider,
  HistoryPanel,
  MapView,
  PlaceAutocomplete,
  RatingForm,
  useAuth,
  VehicleTypePicker,
  vehicleTypeLabel,
  type PlaceResult,
} from '@ride-app/web-shared';

type Point = { lat: number; lng: number; address: string };
type Tab = 'ride' | 'history';

export default function Home() {
  const { user, logout } = useAuth();
  const [tab, setTab] = useState<Tab>('ride');
  const [pickup, setPickup] = useState<Point | null>(null);
  const [dropoff, setDropoff] = useState<Point | null>(null);
  const [estimate, setEstimate] = useState<RideEstimate | null>(null);
  const [vehicleType, setVehicleType] = useState<VehicleType>('standard');
  const [ride, setRide] = useState<Ride | null>(null);
  const [driverPos, setDriverPos] = useState<{ lat: number; lng: number } | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [rated, setRated] = useState(false);
  const [locationBias, setLocationBias] = useState<{ lat: number; lng: number } | null>(null);

  useEffect(() => {
    navigator.geolocation.getCurrentPosition((pos) => {
      const coords = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      setLocationBias(coords);
      setPickup({ ...coords, address: 'Mi ubicación' });
    });
    api.getActiveRide().then((r) => r.ride && setRide(r.ride)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!ride) return;
    const socket = getSocket();
    socket.emit('join:ride', ride.id);
    const onUpdate = (updated: Ride) => setRide(updated);
    const onLocation = (data: { lat: number; lng: number }) => setDriverPos({ lat: data.lat, lng: data.lng });
    socket.on('ride:updated', onUpdate);
    socket.on('driver:location', onLocation);
    return () => {
      socket.off('ride:updated', onUpdate);
      socket.off('driver:location', onLocation);
    };
  }, [ride?.id]);

  useEffect(() => {
    if (!pickup || !dropoff || ride) return;
    api.estimateRide({
      pickupAddress: pickup.address,
      pickupLat: pickup.lat,
      pickupLng: pickup.lng,
      dropoffAddress: dropoff.address,
      dropoffLat: dropoff.lat,
      dropoffLng: dropoff.lng,
    }).then((data) => {
      setEstimate(data);
      setVehicleType(data.options[0]?.vehicleType ?? 'standard');
    }).catch(() => setEstimate(null));
  }, [pickup, dropoff, ride]);

  const onPickupSelect = (place: PlaceResult) => {
    setPickup(place);
    setEstimate(null);
  };

  const onDropoffSelect = (place: PlaceResult) => {
    setDropoff(place);
    setEstimate(null);
  };

  const requestRide = async () => {
    if (!pickup || !dropoff) return;
    setLoading(true);
    setError('');
    try {
      const created = await api.createRide({
        pickupAddress: pickup.address,
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        dropoffAddress: dropoff.address,
        dropoffLat: dropoff.lat,
        dropoffLng: dropoff.lng,
        vehicleType,
      });
      setRide(created);
      setRated(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
    } finally {
      setLoading(false);
    }
  };

  const cancelRide = async () => {
    if (!ride) return;
    await api.updateRideStatus(ride.id, 'cancelled');
    setRide(null);
    setDriverPos(null);
  };

  const payRide = async () => {
    if (!ride) return;
    setLoading(true);
    try {
      const result = await api.payRide(ride.id);
      setRide(result.ride);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
    } finally {
      setLoading(false);
    }
  };

  const reset = () => {
    setRide(null);
    setDropoff(null);
    setEstimate(null);
    setDriverPos(null);
    setRated(false);
  };

  const selectedOption = estimate?.options.find((o) => o.vehicleType === vehicleType);
  const readyToBook = pickup && dropoff && !ride && selectedOption;
  const routePolyline = ride?.routePolyline ?? estimate?.polyline ?? null;
  const showRating = ride?.status === 'completed' && ride.paymentStatus === 'paid' && !rated;

  return (
    <GoogleMapsProvider>
      <div className="map-page">
        <MapView
          pickup={pickup}
          dropoff={dropoff}
          driver={driverPos}
          routePolyline={routePolyline}
          follow={driverPos ?? pickup ?? locationBias}
        />
        <div className="top-bar">
          <span className="badge">Hola, {user?.name}</span>
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn-secondary" onClick={() => setTab(tab === 'ride' ? 'history' : 'ride')}>
              {tab === 'ride' ? 'Historial' : 'Viaje'}
            </button>
            <button className="btn-secondary" onClick={logout}>Salir</button>
          </div>
        </div>
        {tab === 'history' ? (
          <div className="bottom-sheet">
            <h2>Historial de viajes</h2>
            <HistoryPanel />
          </div>
        ) : (
          <>
            {!ride && (
              <div className="search-panel">
                <PlaceAutocomplete
                  label="Origen"
                  placeholder="Buscar dirección de origen"
                  defaultValue={pickup?.address}
                  bias={locationBias}
                  onSelect={onPickupSelect}
                />
                <PlaceAutocomplete
                  label="Destino"
                  placeholder="¿A dónde vas?"
                  defaultValue={dropoff?.address}
                  bias={pickup ?? locationBias}
                  onSelect={onDropoffSelect}
                />
              </div>
            )}
            <div className="bottom-sheet">
              {!ride ? (
                <>
                  <h2>{readyToBook ? 'Confirmar viaje' : 'Busca origen y destino'}</h2>
                  {pickup && <div className="meta-row"><span>Origen</span><span>{pickup.address}</span></div>}
                  {dropoff && <div className="meta-row"><span>Destino</span><span>{dropoff.address}</span></div>}
                  {estimate && (
                    <>
                      <div className="meta-row"><span>Distancia</span><span>{estimate.distanceKm} km</span></div>
                      <div className="meta-row"><span>Tiempo est.</span><span>{estimate.durationMin} min</span></div>
                      <VehicleTypePicker
                        options={estimate.options}
                        selected={vehicleType}
                        onSelect={setVehicleType}
                      />
                    </>
                  )}
                  {error && <p className="error-text">{error}</p>}
                  {readyToBook && (
                    <button className="btn-primary" onClick={requestRide} disabled={loading}>
                      {loading ? 'Solicitando...' : `Pedir ${vehicleTypeLabel(vehicleType)} · $${selectedOption?.estimatedPrice}`}
                    </button>
                  )}
                </>
              ) : (
                <>
                  <div className="status-pill">{RIDE_STATUS_LABELS[ride.status]}</div>
                  <div className="meta-row"><span>Vehículo</span><span>{vehicleTypeLabel(ride.vehicleType)}</span></div>
                  <div className="meta-row"><span>Precio</span><span>${ride.finalPrice ?? ride.estimatedPrice}</span></div>
                  <div className="meta-row">
                    <span>Pago</span>
                    <span>{ride.paymentStatus === 'paid' ? 'Pagado' : 'Pendiente'}</span>
                  </div>
                  {ride.status === 'requested' && (
                    <button className="btn-danger" onClick={cancelRide}>Cancelar</button>
                  )}
                  {ride.status === 'completed' && ride.paymentStatus !== 'paid' && (
                    <button className="btn-primary" onClick={payRide} disabled={loading}>
                      {loading ? 'Procesando...' : 'Pagar con tarjeta'}
                    </button>
                  )}
                  {showRating && (
                    <RatingForm
                      onSubmit={async (stars, comment) => {
                        await api.rateRide(ride.id, stars, comment);
                        setRated(true);
                      }}
                    />
                  )}
                  {ride.status === 'completed' && ride.paymentStatus === 'paid' && rated && (
                    <button className="btn-primary" onClick={reset}>Nuevo viaje</button>
                  )}
                </>
              )}
            </div>
          </>
        )}
      </div>
    </GoogleMapsProvider>
  );
}
