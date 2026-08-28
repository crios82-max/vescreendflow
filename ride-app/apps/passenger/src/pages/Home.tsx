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
  FareBreakdownView,
  TipSelector,
  PromoInput,
  SavedPlacesBar,
  ChatPanel,
  PhoneVerifyBanner,
  SplitFareForm,
  StripeCheckout,
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
  const [promoCode, setPromoCode] = useState('');
  const [promoDiscount, setPromoDiscount] = useState(0);
  const [tipAmount, setTipAmount] = useState(0);
  const [scheduledAt, setScheduledAt] = useState('');
  const [etaPickup, setEtaPickup] = useState<number | null>(null);
  const [etaDropoff, setEtaDropoff] = useState<number | null>(null);
  const [useWallet, setUseWallet] = useState(false);
  const [stop, setStop] = useState<Point | null>(null);
  const [rideForName, setRideForName] = useState('');
  const [rideForPhone, setRideForPhone] = useState('');
  const [stripeSecret, setStripeSecret] = useState<string | null>(null);

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
    const onEta = (data: { etaPickupMin: number | null; etaDropoffMin: number | null }) => {
      setEtaPickup(data.etaPickupMin);
      setEtaDropoff(data.etaDropoffMin);
    };
    socket.on('ride:updated', onUpdate);
    socket.on('driver:location', onLocation);
    socket.on('ride:eta', onEta);
    api.getRideEta(ride.id).then(onEta).catch(() => {});
    const poll = setInterval(() => api.getRideEta(ride.id).then(onEta).catch(() => {}), 15000);
    return () => {
      socket.off('ride:updated', onUpdate);
      socket.off('driver:location', onLocation);
      socket.off('ride:eta', onEta);
      clearInterval(poll);
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
      promoCode: promoCode || undefined,
    }).then((data) => {
      setEstimate(data);
      setVehicleType(data.options[0]?.vehicleType ?? 'standard');
    }).catch(() => setEstimate(null));
  }, [pickup, dropoff, ride, promoCode]);

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
        promoCode: promoCode || undefined,
        scheduledAt: scheduledAt ? new Date(scheduledAt).toISOString() : undefined,
        rideForName: rideForName || undefined,
        rideForPhone: rideForPhone || undefined,
        stops: stop ? [{ address: stop.address, lat: stop.lat, lng: stop.lng }] : undefined,
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
      const intent = await api.createPaymentIntent(ride.id, tipAmount);
      if (intent.clientSecret) {
        setStripeSecret(intent.clientSecret);
        return;
      }
      const result = await api.payRide(ride.id, { tipAmount, useWallet });
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
                <SavedPlacesBar currentDropoff={dropoff} onSelect={(p) => setDropoff(p)} />
                <PlaceAutocomplete
                  label="Parada intermedia (opcional)"
                  placeholder="Agregar parada"
                  bias={pickup ?? locationBias}
                  onSelect={(p) => setStop(p)}
                />
                <input className="place-input" placeholder="Viaje para: nombre" value={rideForName} onChange={(e) => setRideForName(e.target.value)} />
                <input className="place-input" placeholder="Teléfono contacto" value={rideForPhone} onChange={(e) => setRideForPhone(e.target.value)} />
              </div>
            )}
            <div className="bottom-sheet">
              <PhoneVerifyBanner />
              {!ride ? (
                <>
                  <h2>{readyToBook ? 'Confirmar viaje' : 'Busca origen y destino'}</h2>
                  {pickup && <div className="meta-row"><span>Origen</span><span>{pickup.address}</span></div>}
                  {dropoff && <div className="meta-row"><span>Destino</span><span>{dropoff.address}</span></div>}
                  {estimate && (
                    <>
                      {estimate.surgeMultiplier > 1 && (
                        <div className="eta-badge">⚡ Surge {estimate.surgeMultiplier}x activo</div>
                      )}
                      <div className="meta-row"><span>Distancia</span><span>{estimate.distanceKm} km</span></div>
                      <div className="meta-row"><span>Tiempo est.</span><span>{estimate.durationMin} min</span></div>
                      <PromoInput
                        subtotal={selectedOption?.estimatedPrice ?? estimate.options[0]?.estimatedPrice ?? 0}
                        onApplied={(code, discount) => { setPromoCode(code); setPromoDiscount(discount); }}
                      />
                      <input
                        className="place-input"
                        type="datetime-local"
                        value={scheduledAt}
                        onChange={(e) => setScheduledAt(e.target.value)}
                        placeholder="Programar viaje"
                      />
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
                  {(etaPickup != null || ride.etaPickupMin != null) && ['accepted', 'arriving'].includes(ride.status) && (
                    <div className="eta-badge">Llega en ~{etaPickup ?? ride.etaPickupMin} min</div>
                  )}
                  {(etaDropoff != null || ride.etaDropoffMin != null) && ride.status === 'in_progress' && (
                    <div className="eta-badge">Destino en ~{etaDropoff ?? ride.etaDropoffMin} min</div>
                  )}
                  <div className="meta-row"><span>Vehículo</span><span>{vehicleTypeLabel(ride.vehicleType)}</span></div>
                  <FareBreakdownView breakdown={ride.fareBreakdown} surgeMultiplier={ride.surgeMultiplier} />
                  <div className="meta-row"><span>Precio</span><span>${ride.finalPrice ?? ride.estimatedPrice}</span></div>
                  <div className="extras-row">
                    <button className="btn-secondary" type="button" onClick={async () => {
                      const s = await api.shareRide(ride.id);
                      const url = `${window.location.origin}/share/${s.shareToken}`;
                      navigator.clipboard.writeText(url).catch(() => {});
                      alert('Link copiado');
                    }}>Compartir</button>
                    <button className="btn-danger" type="button" onClick={() => api.triggerSos(ride.id, pickup?.lat, pickup?.lng)}>SOS</button>
                  </div>
                  {ride.driverId && <ChatPanel rideId={ride.id} />}
                  <div className="meta-row">
                    <span>Pago</span>
                    <span>{ride.paymentStatus === 'paid' ? 'Pagado' : 'Pendiente'}</span>
                  </div>
                  {ride.status === 'requested' && (
                    <button className="btn-danger" onClick={cancelRide}>Cancelar</button>
                  )}
                  {ride.status === 'completed' && ride.paymentStatus !== 'paid' && (
                    <>
                      <SplitFareForm rideId={ride.id} />
                      <TipSelector value={tipAmount} onChange={setTipAmount} />
                      <label className="meta-row">
                        <span>Pagar con wallet</span>
                        <input type="checkbox" checked={useWallet} onChange={(e) => setUseWallet(e.target.checked)} />
                      </label>
                      {stripeSecret ? (
                        <StripeCheckout
                          clientSecret={stripeSecret}
                          onSuccess={async (paymentIntentId) => {
                            const result = await api.payRide(ride.id, { tipAmount, paymentIntentId });
                            setRide(result.ride);
                            setStripeSecret(null);
                          }}
                        />
                      ) : (
                        <button className="btn-primary" onClick={payRide} disabled={loading}>
                          {loading ? 'Procesando...' : `Pagar $${(ride.finalPrice ?? ride.estimatedPrice) + tipAmount}`}
                        </button>
                      )}
                    </>
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
