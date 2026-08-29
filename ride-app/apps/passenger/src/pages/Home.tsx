import { useEffect, useState } from 'react';
import type { Ride, RideEstimate, ServiceMode, VehicleType } from '@ride-app/shared';
import { vehiclesForMode } from '@ride-app/shared';
import {
  api,
  getSocket,
  GoogleMapsProvider,
  HistoryPanel,
  MapView,
  PlaceAutocomplete,
  RatingForm,
  useAuth,
  useI18n,
  useFlash,
  LanguageSwitcher,
  VehicleTypePicker,
  FareBreakdownView,
  TipSelector,
  PromoInput,
  SavedPlacesBar,
  ChatPanel,
  PhoneVerifyBanner,
  usePhoneVerified,
  SplitFareForm,
  StripeCheckout,
  SavedCards,
  type PlaceResult,
} from '@ride-app/web-shared';

type Point = { lat: number; lng: number; address: string };
type Tab = 'ride' | 'history';

export default function Home() {
  const { user, logout } = useAuth();
  const { t, rideStatus, vehicle, te } = useI18n();
  const { show: showFlash } = useFlash();
  const [tab, setTab] = useState<Tab>('ride');
  const [serviceMode, setServiceMode] = useState<ServiceMode>('ride');
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
  const [deliveryNotes, setDeliveryNotes] = useState('');
  const [stripeSecret, setStripeSecret] = useState<string | null>(null);
  const { verified: phoneVerified } = usePhoneVerified();

  const modeVehicles = vehiclesForMode(serviceMode);
  const filteredOptions = estimate?.options.filter((o) => modeVehicles.includes(o.vehicleType)) ?? [];
  const selectedOption = filteredOptions.find((o) => o.vehicleType === vehicleType) ?? filteredOptions[0];

  useEffect(() => {
    navigator.geolocation.getCurrentPosition((pos) => {
      const coords = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      setLocationBias(coords);
      setPickup({ ...coords, address: t('common.myLocation') });
    });
    api.getActiveRide().then((r) => r.ride && setRide(r.ride)).catch(() => {});
  }, []);

  useEffect(() => {
    const next = vehiclesForMode(serviceMode)[0] ?? 'standard';
    setVehicleType(next);
    if (serviceMode === 'delivery') setStop(null);
  }, [serviceMode]);

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
      const allowed = vehiclesForMode(serviceMode);
      const first = data.options.find((o) => allowed.includes(o.vehicleType));
      setVehicleType(first?.vehicleType ?? allowed[0] ?? 'standard');
    }).catch(() => setEstimate(null));
  }, [pickup, dropoff, ride, promoCode, serviceMode]);

  const onPickupSelect = (place: PlaceResult) => {
    setPickup(place);
    setEstimate(null);
  };

  const onDropoffSelect = (place: PlaceResult) => {
    setDropoff(place);
    setEstimate(null);
  };

  const requestRide = async () => {
    if (!pickup || !dropoff || !selectedOption) return;
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
        vehicleType: selectedOption.vehicleType,
        serviceMode,
        deliveryNotes: serviceMode === 'delivery' ? (deliveryNotes || undefined) : undefined,
        promoCode: promoCode || undefined,
        scheduledAt: scheduledAt ? new Date(scheduledAt).toISOString() : undefined,
        rideForName: rideForName || undefined,
        rideForPhone: rideForPhone || undefined,
        stops: serviceMode === 'ride' && stop ? [{ address: stop.address, lat: stop.lat, lng: stop.lng }] : undefined,
      });
      setRide(created);
      setRated(false);
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
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
      setError(te(err instanceof Error ? err.message : t('common.error')));
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
    setDeliveryNotes('');
  };

  const readyToBook = Boolean(pickup && dropoff && !ride && selectedOption);
  const routePolyline = ride?.routePolyline ?? estimate?.polyline ?? null;
  const showRating = ride?.status === 'completed' && ride.paymentStatus === 'paid' && !rated;
  const isDelivery = serviceMode === 'delivery' || ride?.serviceMode === 'delivery';

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
          <span className="badge badge--accent">{t('common.greeting', { name: user?.name ?? '' })}</span>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <LanguageSwitcher />
            <button className="btn-secondary" onClick={() => setTab(tab === 'ride' ? 'history' : 'ride')}>
              {tab === 'ride' ? t('common.history') : t('passenger.ride')}
            </button>
            <button className="btn-secondary" onClick={logout}>{t('common.logout')}</button>
          </div>
        </div>
        {tab === 'history' ? (
          <div className="bottom-sheet">
            <h2>{t('passenger.rideHistory')}</h2>
            <HistoryPanel />
          </div>
        ) : (
          <>
            {!ride && (
              <div className="search-panel">
                <div className="tab-row" role="tablist" aria-label={t('service.foodDelivery')}>
                  <button
                    type="button"
                    className={`tab-btn${serviceMode === 'ride' ? ' tab-btn--active' : ''}`}
                    onClick={() => setServiceMode('ride')}
                  >
                    {t('service.ride')}
                  </button>
                  <button
                    type="button"
                    className={`tab-btn${serviceMode === 'delivery' ? ' tab-btn--active' : ''}`}
                    onClick={() => setServiceMode('delivery')}
                  >
                    {t('service.foodDelivery')}
                  </button>
                </div>
                <PlaceAutocomplete
                  label={serviceMode === 'delivery' ? t('service.restaurant') : t('common.origin')}
                  placeholder={serviceMode === 'delivery' ? t('service.restaurant') : t('passenger.searchOrigin')}
                  defaultValue={pickup?.address}
                  bias={locationBias}
                  onSelect={onPickupSelect}
                />
                <PlaceAutocomplete
                  label={serviceMode === 'delivery' ? t('service.customer') : t('common.destination')}
                  placeholder={serviceMode === 'delivery' ? t('service.customer') : t('passenger.whereTo')}
                  defaultValue={dropoff?.address}
                  bias={pickup ?? locationBias}
                  onSelect={onDropoffSelect}
                />
                <SavedPlacesBar currentDropoff={dropoff} onSelect={(p) => setDropoff(p)} />
                {serviceMode === 'ride' && (
                  <PlaceAutocomplete
                    label={t('passenger.optionalStop')}
                    placeholder={t('passenger.addStop')}
                    bias={pickup ?? locationBias}
                    onSelect={(p) => setStop(p)}
                  />
                )}
                {serviceMode === 'delivery' ? (
                  <>
                    <input
                      className="place-input"
                      placeholder={t('service.deliveryNotesPlaceholder')}
                      value={deliveryNotes}
                      onChange={(e) => setDeliveryNotes(e.target.value)}
                      maxLength={500}
                    />
                    <input className="place-input" placeholder={t('passenger.contactPhone')} value={rideForPhone} onChange={(e) => setRideForPhone(e.target.value)} />
                  </>
                ) : (
                  <>
                    <input className="place-input" placeholder={t('passenger.rideForName')} value={rideForName} onChange={(e) => setRideForName(e.target.value)} />
                    <input className="place-input" placeholder={t('passenger.contactPhone')} value={rideForPhone} onChange={(e) => setRideForPhone(e.target.value)} />
                  </>
                )}
              </div>
            )}
            <div className="bottom-sheet">
              <PhoneVerifyBanner />
              {!ride ? (
                <>
                  <h2>
                    {readyToBook
                      ? (serviceMode === 'delivery' ? t('service.confirmDelivery') : t('passenger.confirmRide'))
                      : (serviceMode === 'delivery' ? t('service.searchDelivery') : t('passenger.searchPickupDropoff'))}
                  </h2>
                  {pickup && <div className="meta-row"><span>{serviceMode === 'delivery' ? t('service.restaurant') : t('common.origin')}</span><span>{pickup.address}</span></div>}
                  {dropoff && <div className="meta-row"><span>{serviceMode === 'delivery' ? t('service.customer') : t('common.destination')}</span><span>{dropoff.address}</span></div>}
                  {estimate && filteredOptions.length > 0 && (
                    <>
                      {estimate.surgeMultiplier > 1 && (
                        <div className="eta-badge">{t('common.surgeActive', { multiplier: estimate.surgeMultiplier })}</div>
                      )}
                      <div className="meta-row"><span>{t('common.distance')}</span><span>{estimate.distanceKm} {t('common.km')}</span></div>
                      <div className="meta-row"><span>{t('common.estTime')}</span><span>{estimate.durationMin} {t('common.min')}</span></div>
                      <PromoInput
                        subtotal={selectedOption?.estimatedPrice ?? filteredOptions[0]?.estimatedPrice ?? 0}
                        onApplied={(code, discount) => { setPromoCode(code); setPromoDiscount(discount); }}
                      />
                      <input
                        className="place-input"
                        type="datetime-local"
                        value={scheduledAt}
                        onChange={(e) => setScheduledAt(e.target.value)}
                        placeholder={t('passenger.scheduleRide')}
                      />
                      <VehicleTypePicker
                        options={filteredOptions}
                        selected={selectedOption?.vehicleType ?? vehicleType}
                        onSelect={setVehicleType}
                      />
                    </>
                  )}
                  {error && <p className="error-text">{error}</p>}
                  {readyToBook && selectedOption && (
                    <button className="btn-primary" onClick={requestRide} disabled={loading || phoneVerified === false}>
                      {phoneVerified === false
                        ? t('common.verifyPhone')
                        : loading
                          ? t('common.requesting')
                          : serviceMode === 'delivery'
                            ? t('service.requestDelivery', { vehicle: vehicle(selectedOption.vehicleType), price: selectedOption.estimatedPrice })
                            : t('common.requestVehicle', { vehicle: vehicle(selectedOption.vehicleType), price: selectedOption.estimatedPrice })}
                    </button>
                  )}
                </>
              ) : (
                <>
                  <div className="status-pill">{rideStatus(ride.status)}</div>
                  {isDelivery && (
                    <div className="eta-badge">{t('service.foodDelivery')} · {vehicle(ride.vehicleType)}</div>
                  )}
                  {(etaPickup != null || ride.etaPickupMin != null) && ['accepted', 'arriving'].includes(ride.status) && (
                    <div className="eta-badge">{t('common.arriveIn', { min: etaPickup ?? ride.etaPickupMin ?? 0 })}</div>
                  )}
                  {(etaDropoff != null || ride.etaDropoffMin != null) && ride.status === 'in_progress' && (
                    <div className="eta-badge">{t('common.dropoffIn', { min: etaDropoff ?? ride.etaDropoffMin ?? 0 })}</div>
                  )}
                  <div className="meta-row"><span>{t('common.vehicle')}</span><span>{vehicle(ride.vehicleType)}</span></div>
                  {ride.deliveryNotes && (
                    <div className="meta-row"><span>{t('service.foodOrder')}</span><span>{ride.deliveryNotes}</span></div>
                  )}
                  <FareBreakdownView breakdown={ride.fareBreakdown} surgeMultiplier={ride.surgeMultiplier} />
                  <div className="meta-row"><span>{t('common.price')}</span><span>${ride.finalPrice ?? ride.estimatedPrice}</span></div>
                  <div className="extras-row">
                    <button className="btn-secondary" type="button" onClick={async () => {
                      const s = await api.shareRide(ride.id);
                      const url = `${window.location.origin}/share/${s.shareToken}`;
                      navigator.clipboard.writeText(url).catch(() => {});
                      showFlash(t('common.linkCopiedBanner'));
                    }}>{t('common.share')}</button>
                    <button className="btn-danger" type="button" onClick={() => api.triggerSos(ride.id, pickup?.lat, pickup?.lng)}>{t('common.sos')}</button>
                    {ride.driverId && ['accepted', 'arriving', 'in_progress'].includes(ride.status) && (
                      <button className="btn-secondary" type="button" onClick={async () => {
                      const c = await api.initiateMaskedCall(ride.id);
                      if (c.initiated) showFlash(te(c.message ?? t('common.callConnecting')));
                      else if (c.dialUrl) window.location.href = c.dialUrl;
                      else showFlash(te(c.hint ?? t('common.callFailed')), 'error');
                    }}>{t('passenger.callDriver')}</button>
                    )}
                  </div>
                  {ride.driverId && <ChatPanel rideId={ride.id} />}
                  <div className="meta-row">
                    <span>{t('common.payment')}</span>
                    <span>{ride.paymentStatus === 'paid' ? t('common.paid') : t('common.pending')}</span>
                  </div>
                  {ride.status === 'requested' && (
                    <button className="btn-danger" onClick={cancelRide}>{t('common.cancel')}</button>
                  )}
                  {ride.status === 'completed' && ride.paymentStatus !== 'paid' && (
                    <>
                      <SavedCards />
                      <SplitFareForm rideId={ride.id} />
                      <TipSelector value={tipAmount} onChange={setTipAmount} />
                      <label className="meta-row">
                        <span>{t('common.payWithWallet')}</span>
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
                          {loading ? t('common.processing') : t('common.payAmount', { amount: (ride.finalPrice ?? ride.estimatedPrice) + tipAmount })}
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
                    <button className="btn-primary" onClick={reset}>{t('common.newRide')}</button>
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
