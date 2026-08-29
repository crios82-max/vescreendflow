import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  Share,
  Text,
  TextInput,
  View,
} from 'react-native';
import * as Location from 'expo-location';
import MapView, { Marker, Polyline, PROVIDER_GOOGLE } from 'react-native-maps';
import { StatusBar } from 'expo-status-bar';
import type { Ride, RideEstimate, ServiceMode, User, VehicleType } from '@ride-app/shared';
import { BRAND, VEHICLE_TYPES, vehiclesForMode } from '@ride-app/shared';
import { mobileApi } from './src/api';
import { getMobileSocket, reconnectSocket } from './src/socket';
import { PlaceSearch } from './src/PlaceSearch';
import { VehicleTypePicker } from './src/VehicleTypePicker';
import { SavedPlacesBar } from './src/SavedPlacesBar';
import { FareBreakdownView } from './src/FareBreakdownView';
import { defaultApiUrl, getApiUrl } from './src/storage';
import { registerForPushNotifications } from './src/push';
import { decodePolyline } from './src/polyline';
import { openTurnByTurnNavigation } from './src/navigation';
import { appStyles as styles, colors, placeholderColor } from './src/theme';
import { LOCALES, useMobileI18n } from './src/i18n';

type Screen = 'auth' | 'home';
type Tab = 'ride' | 'history';

export default function App() {
  const { t, te, tagline, locale, setLocale, vehicle, rideStatus } = useMobileI18n();
  const [ready, setReady] = useState(false);
  const [screen, setScreen] = useState<Screen>('auth');
  const [user, setUser] = useState<User | null>(null);
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [role, setRole] = useState<'passenger' | 'driver'>('passenger');
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '', vehicleType: 'standard' as VehicleType });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [apiUrlInput, setApiUrlInput] = useState(defaultApiUrl());
  const [apiConnected, setApiConnected] = useState<boolean | null>(null);
  const [showSettings, setShowSettings] = useState(false);

  const [position, setPosition] = useState<{ latitude: number; longitude: number } | null>(null);
  const [pickup, setPickup] = useState<{ latitude: number; longitude: number; address: string } | null>(null);
  const [dropoff, setDropoff] = useState<{ latitude: number; longitude: number; address: string } | null>(null);
  const [ride, setRide] = useState<Ride | null>(null);
  const [driverPos, setDriverPos] = useState<{ latitude: number; longitude: number } | null>(null);
  const [estimate, setEstimate] = useState<RideEstimate | null>(null);
  const [vehicleType, setVehicleType] = useState<VehicleType>('standard');
  const [serviceMode, setServiceMode] = useState<ServiceMode>('ride');
  const [deliveryNotes, setDeliveryNotes] = useState('');
  const [online, setOnline] = useState(false);
  const [pending, setPending] = useState<Array<{ id: string; pickupAddress: string; estimatedPrice: number; vehicleType: string; serviceMode?: string; deliveryNotes?: string | null }>>([]);
  const [tab, setTab] = useState<Tab>('ride');
  const [history, setHistory] = useState<Ride[]>([]);
  const [rated, setRated] = useState(false);
  const [tipAmount, setTipAmount] = useState(2);
  const [etaPickup, setEtaPickup] = useState<number | null>(null);
  const [rideForName, setRideForName] = useState('');
  const [stop, setStop] = useState<{ latitude: number; longitude: number; address: string } | null>(null);
  const [phoneVerified, setPhoneVerified] = useState(false);
  const [phoneInput, setPhoneInput] = useState('');
  const [otpInput, setOtpInput] = useState('');
  const [promoCode, setPromoCode] = useState('');
  const [promoDiscount, setPromoDiscount] = useState(0);
  const [chatText, setChatText] = useState('');
  const [chatMessages, setChatMessages] = useState<Array<{ id: string; senderName?: string; message: string }>>([]);
  const [banner, setBanner] = useState<{ message: string; error?: boolean } | null>(null);
  const [forgotMsg, setForgotMsg] = useState('');
  const [historyLoading, setHistoryLoading] = useState(false);
  const [useWallet, setUseWallet] = useState(false);
  const [walletBalance, setWalletBalance] = useState<number | null>(null);
  const [splitEmails, setSplitEmails] = useState('');
  const [splitResult, setSplitResult] = useState('');
  const [scheduledHours, setScheduledHours] = useState(0);
  const [rateComment, setRateComment] = useState('');
  const [connectStatus, setConnectStatus] = useState<{ onboarded: boolean } | null>(null);
  const [docs, setDocs] = useState({ licenseUrl: '', idUrl: '', vehiclePhotoUrl: '' });
  const [approvalStatus, setApprovalStatus] = useState('approved');
  const [earnings, setEarnings] = useState<{ today: { total: number } } | null>(null);

  const showBanner = (message: string, error = false) => {
    setBanner({ message, error });
    setTimeout(() => setBanner(null), 4000);
  };

  useEffect(() => {
    (async () => {
      await mobileApi.init();
      const url = await getApiUrl();
      setApiUrlInput(url);
      await checkApi();
      setReady(true);
    })();
  }, []);

  const checkApi = async () => {
    try {
      await mobileApi.health();
      setApiConnected(true);
    } catch {
      setApiConnected(false);
    }
  };

  const saveApiUrl = async () => {
    await mobileApi.setApiUrl(apiUrlInput);
    await reconnectSocket();
    await checkApi();
    setShowSettings(false);
  };

  useEffect(() => {
    if (!ready) return;
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return;
      const loc = await Location.getCurrentPositionAsync({});
      const coords = { latitude: loc.coords.latitude, longitude: loc.coords.longitude };
      setPosition(coords);
      setPickup({ ...coords, address: t('common.myLocation') });
    })();
  }, [ready]);

  useEffect(() => {
    if (!ride?.id) return;
    const loadChat = () => mobileApi.getChatMessages(ride.id).then((r) => setChatMessages(r.messages)).catch(() => {});
    loadChat();
    const chatInterval = setInterval(loadChat, 5000);

    let cleanup = () => {};
    (async () => {
      const socket = await getMobileSocket();
      socket.emit('join:ride', ride.id);
      const onUpdate = (updated: Ride) => setRide(updated);
      const onLocation = (data: { lat: number; lng: number }) =>
        setDriverPos({ latitude: data.lat, longitude: data.lng });
      socket.on('ride:updated', onUpdate);
      socket.on('driver:location', onLocation);
      socket.on('ride:eta', (data: { etaPickupMin: number | null }) => setEtaPickup(data.etaPickupMin));
      mobileApi.getRideEta(ride.id).then((e) => setEtaPickup(e.etaPickupMin)).catch(() => {});
      cleanup = () => {
        socket.off('ride:updated', onUpdate);
        socket.off('driver:location', onLocation);
      };
    })();
    return () => {
      cleanup();
      clearInterval(chatInterval);
    };
  }, [ride?.id]);

  useEffect(() => {
    if (user?.role !== 'driver' || !online) return;
    let sub: Location.LocationSubscription | null = null;
    let socketCleanup = () => {};
    (async () => {
      sub = await Location.watchPositionAsync(
        { accuracy: Location.Accuracy.High, distanceInterval: 10 },
        (loc) => {
          const coords = { latitude: loc.coords.latitude, longitude: loc.coords.longitude };
          setPosition(coords);
          mobileApi.sendLocation(coords.latitude, coords.longitude).catch(() => {});
        },
      );
      const socket = await getMobileSocket();
      socket.emit('join:drivers');
      const loadPending = () => mobileApi.getPendingRides().then((r) => setPending(r.rides)).catch(() => {});
      socket.on('ride:requested', loadPending);
      socket.on('ride:taken', loadPending);
      loadPending();
      socketCleanup = () => {
        socket.off('ride:requested', loadPending);
        socket.off('ride:taken', loadPending);
      };
    })();
    return () => {
      sub?.remove();
      socketCleanup();
    };
  }, [user?.role, online]);

  const submitAuth = async () => {
    setLoading(true);
    setError('');
    try {
      const data = mode === 'login'
        ? await mobileApi.login(form.email, form.password)
        : await mobileApi.register({ ...form, role });
      mobileApi.setToken(data.token);
      setUser(data.user);
      setScreen('home');
      registerForPushNotifications().catch(() => {});
      if (data.user.role === 'passenger') {
        const active = await mobileApi.getActiveRide();
        if (active.ride) setRide(active.ride);
      }
      mobileApi.getPhoneVerifyStatus().then((s) => setPhoneVerified(s.verified)).catch(() => {});
      void mobileApi.setPreferredLocale(locale);
      mobileApi.getWalletBalance().then((w) => setWalletBalance(w.balance)).catch(() => {});
      if (data.user.role === 'driver') {
        mobileApi.getConnectStatus().then(setConnectStatus).catch(() => {});
        mobileApi.getOnboardingStatus().then((r) => setApprovalStatus(r.approvalStatus)).catch(() => {});
        mobileApi.getDriverEarnings().then(setEarnings).catch(() => {});
      }
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    } finally {
      setLoading(false);
    }
  };

  const requestRide = async () => {
    const origin = pickup ?? position;
    if (!origin || !dropoff) return;
    setLoading(true);
    try {
      const created = await mobileApi.createRide({
        pickupAddress: pickup?.address ?? t('common.myLocation'),
        pickupLat: origin.latitude,
        pickupLng: origin.longitude,
        dropoffAddress: dropoff.address,
        dropoffLat: dropoff.latitude,
        dropoffLng: dropoff.longitude,
        vehicleType,
        serviceMode,
        deliveryNotes: serviceMode === 'delivery' ? (deliveryNotes || undefined) : undefined,
        rideForName: rideForName || undefined,
        promoCode: promoCode || undefined,
        scheduledAt: scheduledHours > 0 ? new Date(Date.now() + scheduledHours * 3600_000).toISOString() : undefined,
        stops: serviceMode === 'ride' && stop ? [{ address: stop.address, lat: stop.latitude, lng: stop.longitude }] : undefined,
      });
      setRide(created);
      setScheduledHours(0);
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    } finally {
      setLoading(false);
    }
  };

  const payRide = async () => {
    if (!ride) return;
    setLoading(true);
    setError('');
    try {
      if (useWallet) {
        const r = await mobileApi.payRideOptions(ride.id, { tipAmount, useWallet: true });
        setRide(r.ride);
        const w = await mobileApi.getWalletBalance();
        setWalletBalance(w.balance);
        return;
      }
      const intent = await mobileApi.createPaymentIntent(ride.id, tipAmount);
      if (intent.mock) {
        const r = await mobileApi.payRideOptions(ride.id, { tipAmount });
        setRide(r.ride);
        showBanner(t('mobile.paidMock'));
        return;
      }
      // Stripe configured: server confirms test Visa (same as API processRidePayment)
      const r = await mobileApi.payRideOptions(ride.id, { tipAmount });
      setRide(r.ride);
      showBanner(t('mobile.paidCard'));
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const origin = pickup ?? position;
    if (!origin || !dropoff || user?.role !== 'passenger') return;
    mobileApi.estimateRide({
      pickupAddress: pickup?.address ?? t('common.myLocation'),
      pickupLat: origin.latitude,
      pickupLng: origin.longitude,
      dropoffAddress: dropoff.address,
      dropoffLat: dropoff.latitude,
      dropoffLng: dropoff.longitude,
    }).then((data) => {
      setEstimate(data);
      const allowed = vehiclesForMode(serviceMode);
      const first = data.options.find((o) => allowed.includes(o.vehicleType));
      setVehicleType(first?.vehicleType ?? allowed[0] ?? 'standard');
    }).catch(() => setEstimate(null));
  }, [pickup, position, dropoff, user?.role, serviceMode]);

  const loadHistory = async () => {
    setHistoryLoading(true);
    try {
      const data = await mobileApi.getHistory();
      setHistory(data.rides);
    } catch {
      setHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    if (tab === 'history' && user) loadHistory();
  }, [tab, user?.id]);

  const toggleOnline = async () => {
    if (!position) return;
    if (online) {
      await mobileApi.goOffline();
      setOnline(false);
      setPending([]);
      return;
    }
    await mobileApi.goOnline(position.latitude, position.longitude);
    setOnline(true);
    const list = await mobileApi.getPendingRides();
    setPending(list.rides);
  };

  if (!ready) {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={colors.primary} size="large" />
      </View>
    );
  }

  const connectionBadge = apiConnected === null ? '…' : apiConnected ? t('mobile.connectedApi') : t('mobile.disconnectedApi');

  if (screen === 'auth') {
    return (
      <SafeAreaView style={styles.container}>
        <StatusBar style="light" />
        {banner && (
          <View style={[styles.banner, banner.error && styles.bannerError]}>
            <Text style={styles.bannerText}>{banner.message}</Text>
          </View>
        )}
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.flex}>
          <ScrollView contentContainerStyle={styles.authScroll} keyboardShouldPersistTaps="handled">
            <View style={styles.row}>
              {LOCALES.map((code) => (
                <Pressable key={code} style={[styles.chip, locale === code && styles.chipActive]} onPress={() => setLocale(code)}>
                  <Text style={locale === code ? styles.chipTextActive : styles.chipText}>{code.toUpperCase()}</Text>
                </Pressable>
              ))}
            </View>
            <Image source={require('./assets/icon.png')} style={styles.authLogo} accessibilityLabel={BRAND.name} />
            <Text style={styles.brandName}>{BRAND.name}</Text>
            <Text style={styles.tagline}>{tagline}</Text>
            <Text style={apiConnected ? styles.connectionOk : apiConnected === false ? styles.connectionBad : styles.muted}>
              {connectionBadge}
            </Text>

            <Pressable onPress={() => setShowSettings(!showSettings)}>
              <Text style={styles.link}>{t('mobile.configureApi')}</Text>
            </Pressable>
            {showSettings && (
              <View style={styles.settingsBox}>
                <TextInput
                  style={styles.input}
                  value={apiUrlInput}
                  onChangeText={setApiUrlInput}
                  autoCapitalize="none"
                  autoCorrect={false}
                  placeholder="http://192.168.x.x:4001"
                  placeholderTextColor={placeholderColor}
                />
                <Pressable style={styles.btnSmall} onPress={saveApiUrl}>
                  <Text style={styles.btnText}>{t('mobile.saveAndTest')}</Text>
                </Pressable>
              </View>
            )}

            <View style={styles.row}>
              <Pressable style={[styles.chip, mode === 'login' && styles.chipActive]} onPress={() => setMode('login')}>
                <Text style={mode === 'login' ? styles.chipTextActive : styles.chipText}>{t('mobile.loginTab')}</Text>
              </Pressable>
              <Pressable style={[styles.chip, mode === 'register' && styles.chipActive]} onPress={() => setMode('register')}>
                <Text style={mode === 'register' ? styles.chipTextActive : styles.chipText}>{t('mobile.registerTab')}</Text>
              </Pressable>
            </View>
            {mode === 'register' && (
              <View style={styles.row}>
                <Pressable style={[styles.chip, role === 'passenger' && styles.chipActive]} onPress={() => setRole('passenger')}>
                  <Text style={role === 'passenger' ? styles.chipTextActive : styles.chipText}>{t('mobile.passenger')}</Text>
                </Pressable>
                <Pressable style={[styles.chip, role === 'driver' && styles.chipActive]} onPress={() => setRole('driver')}>
                  <Text style={role === 'driver' ? styles.chipTextActive : styles.chipText}>{t('mobile.driver')}</Text>
                </Pressable>
              </View>
            )}
            {mode === 'register' && (
              <TextInput style={styles.input} placeholder={t('common.name')} placeholderTextColor={placeholderColor} value={form.name} onChangeText={(name) => setForm({ ...form, name })} />
            )}
            {mode === 'register' && role === 'driver' && (
              <View style={styles.row}>
                {VEHICLE_TYPES.map((type) => (
                  <Pressable
                    key={type}
                    style={[styles.chip, form.vehicleType === type && styles.chipActive]}
                    onPress={() => setForm({ ...form, vehicleType: type })}
                  >
                    <Text style={form.vehicleType === type ? styles.chipTextActive : styles.chipText}>
                      {vehicle(type)}
                    </Text>
                  </Pressable>
                ))}
              </View>
            )}
            <TextInput style={styles.input} placeholder={t('common.email')} placeholderTextColor={placeholderColor} autoCapitalize="none" keyboardType="email-address" value={form.email} onChangeText={(email) => setForm({ ...form, email })} />
            <TextInput style={styles.input} placeholder={t('common.password')} placeholderTextColor={placeholderColor} secureTextEntry value={form.password} onChangeText={(password) => setForm({ ...form, password })} />
            {error ? <Text style={styles.error}>{error}</Text> : null}
            <Pressable style={[styles.btn, apiConnected === false && styles.btnDisabled]} onPress={submitAuth} disabled={loading || apiConnected === false}>
              {loading ? <ActivityIndicator color={colors.primaryOnDark} /> : <Text style={styles.btnText}>{mode === 'login' ? t('common.login') : t('auth.createAccount')}</Text>}
            </Pressable>
            {mode === 'login' && (
              <Pressable onPress={async () => {
                if (!form.email) { setError(t('common.enterEmail')); return; }
                const r = await mobileApi.forgotPassword(form.email);
                setForgotMsg(r.devResetUrl ? t('common.devReset', { url: r.devResetUrl }) : t('common.checkEmail'));
              }}>
                <Text style={styles.link}>{t('auth.forgotPassword')}</Text>
              </Pressable>
            )}
            {forgotMsg ? <Text style={styles.muted}>{forgotMsg}</Text> : null}
            {apiConnected === false && (
              <Text style={styles.muted}>{t('mobile.offlineHint')}</Text>
            )}
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    );
  }

  const mapCenter = driverPos ?? dropoff ?? pickup ?? position;
  const filteredOptions = estimate?.options.filter((o) => vehiclesForMode(serviceMode).includes(o.vehicleType)) ?? [];
  const selectedOption = filteredOptions.find((o) => o.vehicleType === vehicleType) ?? filteredOptions[0];
  const routeCoords = ride?.routePolyline ? decodePolyline(ride.routePolyline) : estimate?.polyline ? decodePolyline(estimate.polyline) : [];

  return (
    <View style={styles.flex}>
      <StatusBar style="light" />
      {banner && (
        <SafeAreaView style={[styles.banner, banner.error && styles.bannerError]}>
          <Text style={styles.bannerText}>{banner.message}</Text>
        </SafeAreaView>
      )}
      {mapCenter && (
        <MapView
          style={styles.map}
          provider={PROVIDER_GOOGLE}
          region={{
            latitude: mapCenter.latitude,
            longitude: mapCenter.longitude,
            latitudeDelta: 0.04,
            longitudeDelta: 0.04,
          }}
          showsUserLocation
          showsMyLocationButton
        >
          {(pickup ?? position) && <Marker coordinate={(pickup ?? position)!} title={t('common.origin')} pinColor={colors.mapPickup} />}
          {dropoff && <Marker coordinate={dropoff} title={t('common.destination')} pinColor={colors.primary} />}
          {driverPos && <Marker coordinate={driverPos} title={t('mobile.driver')} pinColor={colors.mapDriver} />}
          {routeCoords.length > 0 && <Polyline coordinates={routeCoords} strokeColor={colors.mapRoute} strokeWidth={4} />}
        </MapView>
      )}
      {user?.role === 'passenger' && !ride && (
        <SafeAreaView style={styles.searchOverlay}>
          <View style={styles.row}>
            <Pressable style={[styles.chip, serviceMode === 'ride' && styles.chipActive]} onPress={() => { setServiceMode('ride'); setVehicleType('standard'); }}>
              <Text style={serviceMode === 'ride' ? styles.chipTextActive : styles.chipText}>{t('service.ride')}</Text>
            </Pressable>
            <Pressable style={[styles.chip, serviceMode === 'delivery' && styles.chipActive]} onPress={() => { setServiceMode('delivery'); setVehicleType('moto'); setStop(null); }}>
              <Text style={serviceMode === 'delivery' ? styles.chipTextActive : styles.chipText}>{t('service.foodDelivery')}</Text>
            </Pressable>
          </View>
          <PlaceSearch placeholder={serviceMode === 'delivery' ? t('service.restaurant') : t('common.origin')} bias={position} language={locale} onSelect={(p) => setPickup(p)} />
          <PlaceSearch placeholder={serviceMode === 'delivery' ? t('service.customer') : t('passenger.whereTo')} bias={pickup ?? position} language={locale} onSelect={(p) => setDropoff(p)} />
          {serviceMode === 'ride' && (
            <>
              <TextInput style={styles.input} placeholder={t('passenger.optionalStop')} placeholderTextColor={placeholderColor} value={stop?.address ?? ''} editable={false} />
              <PlaceSearch placeholder={t('passenger.addStop')} bias={pickup ?? position} language={locale} onSelect={(p) => setStop(p)} />
            </>
          )}
          <SavedPlacesBar currentDropoff={dropoff} onSelect={(p) => setDropoff(p)} />
          {serviceMode === 'delivery' ? (
            <TextInput style={styles.input} placeholder={t('service.deliveryNotesPlaceholder')} placeholderTextColor={placeholderColor} value={deliveryNotes} onChangeText={setDeliveryNotes} />
          ) : (
            <TextInput style={styles.input} placeholder={t('passenger.rideForName')} placeholderTextColor={placeholderColor} value={rideForName} onChangeText={setRideForName} />
          )}
        </SafeAreaView>
      )}
      <SafeAreaView style={styles.sheet}>
        {user?.role === 'passenger' && !phoneVerified && (
          <View style={styles.settingsBox}>
            <Text style={styles.muted}>{t('common.verifyPhone')}</Text>
            <TextInput style={styles.input} placeholder="+58..." value={phoneInput} onChangeText={setPhoneInput} placeholderTextColor={placeholderColor} />
            <TextInput style={styles.input} placeholder={t('common.otpPlaceholder')} value={otpInput} onChangeText={setOtpInput} placeholderTextColor={placeholderColor} keyboardType="number-pad" />
            <View style={styles.row}>
              <Pressable style={styles.btnSmall} onPress={async () => {
                const r = await mobileApi.sendPhoneOtp(phoneInput);
                if (r.devHint) showBanner(t('common.devOtp', { code: r.devHint }));
              }}><Text style={styles.btnText}>{t('common.sendCode')}</Text></Pressable>
              <Pressable style={styles.btnSmall} onPress={async () => {
                await mobileApi.confirmPhoneOtp(phoneInput, otpInput);
                setPhoneVerified(true);
              }}><Text style={styles.btnText}>{t('common.verify')}</Text></Pressable>
            </View>
          </View>
        )}
        <View style={styles.sheetHeader}>
          <View style={styles.row}>
            {LOCALES.map((code) => (
              <Pressable key={code} style={[styles.chip, locale === code && styles.chipActive]} onPress={() => setLocale(code)}>
                <Text style={locale === code ? styles.chipTextActive : styles.chipText}>{code.toUpperCase()}</Text>
              </Pressable>
            ))}
          </View>
          <Text style={styles.sheetTitle}>{user?.name}</Text>
          <Text style={styles.roleBadge}>{user?.role === 'passenger' ? t('mobile.passenger') : t('mobile.driver')} · {connectionBadge}</Text>
          <View style={styles.row}>
            <Pressable style={[styles.chip, tab === 'ride' && styles.chipActive]} onPress={() => setTab('ride')}>
              <Text style={tab === 'ride' ? styles.chipTextActive : styles.chipText}>{t('passenger.ride')}</Text>
            </Pressable>
            <Pressable style={[styles.chip, tab === 'history' && styles.chipActive]} onPress={() => setTab('history')}>
              <Text style={tab === 'history' ? styles.chipTextActive : styles.chipText}>{t('common.history')}</Text>
            </Pressable>
          </View>
        </View>
        {tab === 'history' ? (
          <ScrollView>
            {historyLoading ? (
              <ActivityIndicator color={colors.primary} style={{ marginVertical: 16 }} />
            ) : history.length === 0 ? (
              <Text style={styles.muted}>{t('common.noPastRides')}</Text>
            ) : history.map((h) => (
              <View key={h.id} style={styles.card}>
                <Text style={styles.cardTitle}>{rideStatus(h.status)}</Text>
                <Text style={styles.muted} numberOfLines={1}>{h.pickupAddress}</Text>
                <Text style={styles.muted} numberOfLines={1}>{h.dropoffAddress}</Text>
                <Text style={styles.price}>${h.finalPrice ?? h.estimatedPrice}</Text>
              </View>
            ))}
          </ScrollView>
        ) : user?.role === 'passenger' ? (
          <ScrollView keyboardShouldPersistTaps="handled">
            {!ride ? (
              <>
                {estimate && estimate.surgeMultiplier > 1 && (
                  <Text style={styles.etaText}>{t('common.surgeActive', { multiplier: estimate.surgeMultiplier })}</Text>
                )}
                {estimate && (
                  <Text style={styles.muted}>{estimate.distanceKm} km · ~{Math.round(estimate.durationMin)} min</Text>
                )}
                {filteredOptions.length > 0 && (
                  <VehicleTypePicker
                    options={filteredOptions}
                    selected={selectedOption?.vehicleType ?? vehicleType}
                    onSelect={setVehicleType}
                  />
                )}
                <View style={styles.row}>
                  {[0, 1, 2].map((h) => (
                    <Pressable key={h} style={[styles.chip, scheduledHours === h && styles.chipActive]} onPress={() => setScheduledHours(h)}>
                      <Text style={scheduledHours === h ? styles.chipTextActive : styles.chipText}>
                        {h === 0 ? t('mobile.now') : `+${h}h`}
                      </Text>
                    </Pressable>
                  ))}
                </View>
                {scheduledHours > 0 && (
                  <Text style={styles.muted}>
                    {t('passenger.scheduleRide')}: {new Date(Date.now() + scheduledHours * 3600_000).toLocaleString()}
                  </Text>
                )}
                <TextInput style={styles.input} placeholder={t('common.promoPlaceholder')} placeholderTextColor={placeholderColor} value={promoCode} onChangeText={setPromoCode} autoCapitalize="characters" />
                {promoCode ? (
                  <Pressable style={styles.btnSmall} onPress={async () => {
                    const sub = selectedOption?.estimatedPrice ?? 0;
                    const r = await mobileApi.validatePromo(promoCode, sub);
                    setPromoDiscount(r.valid ? r.discount : 0);
                  }}>
                    <Text style={styles.btnText}>{promoDiscount > 0 ? `-$${promoDiscount}` : t('common.apply')}</Text>
                  </Pressable>
                ) : null}
                {error ? <Text style={styles.error}>{error}</Text> : null}
                <Pressable style={[styles.btn, (!dropoff || loading || !phoneVerified) && styles.btnDisabled]} onPress={requestRide} disabled={!dropoff || loading || !phoneVerified}>
                  <Text style={styles.btnText}>
                    {!phoneVerified ? t('common.verifyPhone') : loading ? t('common.requesting') : t('common.requestVehicle', { vehicle: selectedOption ? vehicle(selectedOption.vehicleType) : 'Ride', price: selectedOption?.estimatedPrice ?? '' })}
                  </Text>
                </Pressable>
              </>
            ) : (
              <>
                <View style={styles.statusPill}>
                  <Text style={styles.statusPillText}>{rideStatus(ride.status)}</Text>
                </View>
                {etaPickup != null && ['accepted', 'arriving'].includes(ride.status) && (
                  <Text style={styles.etaText}>{t('common.arriveIn', { min: etaPickup })}</Text>
                )}
                <Text style={styles.muted}>{vehicle(ride.vehicleType)}{ride.serviceMode === 'delivery' ? ` · ${t('service.foodDelivery')}` : ''}</Text>
                {ride.deliveryNotes ? <Text style={styles.muted}>{t('service.foodOrder')}: {ride.deliveryNotes}</Text> : null}
                <FareBreakdownView breakdown={ride.fareBreakdown} surgeMultiplier={ride.surgeMultiplier} />
                <Text style={styles.price}>${ride.finalPrice ?? ride.estimatedPrice}</Text>
                {ride.status === 'completed' && ride.paymentStatus !== 'paid' && (
                  <>
                    <View style={styles.row}>
                      {[0, 1, 2, 5].map((tipVal) => (
                        <Pressable key={tipVal} style={[styles.chip, tipAmount === tipVal && styles.chipActive]} onPress={() => setTipAmount(tipVal)}>
                          <Text style={tipAmount === tipVal ? styles.chipTextActive : styles.chipText}>{tipVal === 0 ? t('common.noTip') : `$${tipVal}`}</Text>
                        </Pressable>
                      ))}
                    </View>
                    <Pressable style={[styles.chip, useWallet && styles.chipActive]} onPress={() => setUseWallet(!useWallet)}>
                      <Text style={useWallet ? styles.chipTextActive : styles.chipText}>
                        {t('common.payWithWallet')}{walletBalance != null ? ` ($${walletBalance.toFixed(2)})` : ''}
                      </Text>
                    </Pressable>
                    <TextInput
                      style={styles.input}
                      placeholder={t('common.emailsComma')}
                      placeholderTextColor={placeholderColor}
                      value={splitEmails}
                      onChangeText={setSplitEmails}
                      autoCapitalize="none"
                    />
                    <Pressable style={styles.btnSecondary} onPress={async () => {
                      const emails = splitEmails.split(',').map((e) => e.trim()).filter(Boolean);
                      if (emails.length === 0) return;
                      try {
                        const r = await mobileApi.splitFare(ride.id, emails);
                        setSplitResult(t('common.splitShare', { amount: r.yourShare }));
                        showBanner(t('common.splitAndInvite'));
                      } catch (err) {
                        showBanner(te(err instanceof Error ? err.message : t('common.error')), true);
                      }
                    }}>
                      <Text style={styles.btnSecondaryText}>{t('common.splitFare')}</Text>
                    </Pressable>
                    {splitResult ? <Text style={styles.muted}>{splitResult}</Text> : null}
                    {error ? <Text style={styles.error}>{error}</Text> : null}
                    <Pressable style={[styles.btn, loading && styles.btnDisabled]} onPress={payRide} disabled={loading}>
                      <Text style={styles.btnText}>
                        {loading ? t('common.processing') : t('common.payAmount', { amount: (ride.finalPrice ?? ride.estimatedPrice) + tipAmount })}
                      </Text>
                    </Pressable>
                  </>
                )}
                <View style={styles.row}>
                  <Pressable style={styles.btnSecondary} onPress={async () => {
                    const s = await mobileApi.shareRide(ride.id);
                    let shareBase = 'http://localhost:5174';
                    try {
                      const u = new URL(apiUrlInput);
                      u.port = '5174';
                      u.pathname = '';
                      shareBase = u.origin;
                    } catch { /* keep default */ }
                    const url = `${shareBase}/share/${s.shareToken}`;
                    await Share.share({ message: url, url });
                  }}>
                    <Text style={styles.btnSecondaryText}>{t('common.share')}</Text>
                  </Pressable>
                  <Pressable style={styles.btnSecondary} onPress={() => mobileApi.triggerSos(ride.id, position?.latitude, position?.longitude)}>
                    <Text style={styles.btnSecondaryText}>{t('common.sos')}</Text>
                  </Pressable>
                  {['accepted', 'arriving', 'in_progress'].includes(ride.status) && (
                    <Pressable style={styles.btnSecondary} onPress={async () => {
                      const c = await mobileApi.initiateMaskedCall(ride.id);
                      if (c.initiated) showBanner(te(c.message ?? t('common.callConnecting')));
                      else if (c.dialUrl) Linking.openURL(c.dialUrl);
                      else showBanner(te(c.hint ?? t('common.callFailed')), true);
                    }}>
                      <Text style={styles.btnSecondaryText}>{t('common.call')}</Text>
                    </Pressable>
                  )}
                </View>
                {chatMessages.length > 0 && (
                  <View style={styles.settingsBox}>
                    {chatMessages.slice(-4).map((m) => (
                      <Text key={m.id} style={styles.muted}>{m.senderName}: {m.message}</Text>
                    ))}
                  </View>
                )}
                {ride.driverId && ['accepted', 'arriving', 'in_progress'].includes(ride.status) && (
                  <View style={styles.row}>
                    <TextInput style={[styles.input, { flex: 1 }]} placeholder={t('common.message')} value={chatText} onChangeText={setChatText} placeholderTextColor={placeholderColor} />
                    <Pressable style={styles.btnSmall} onPress={async () => {
                      if (!chatText.trim()) return;
                      await mobileApi.sendChatMessage(ride.id, chatText.trim());
                      setChatText('');
                    }}><Text style={styles.btnText}>→</Text></Pressable>
                  </View>
                )}
                {ride.status === 'completed' && ride.paymentStatus === 'paid' && !rated && (
                  <View style={styles.ratingBox}>
                    <Text style={styles.statusPillText}>{t('common.rateDriver')}</Text>
                    {[1, 2, 3, 4, 5].map((stars) => (
                      <Pressable
                        key={stars}
                        style={styles.btnSecondary}
                        onPress={async () => {
                          await mobileApi.rateRide(ride.id, stars, rateComment || undefined);
                          setRated(true);
                          setRateComment('');
                        }}
                      >
                        <Text style={styles.btnSecondaryText}>{'★'.repeat(stars)}</Text>
                      </Pressable>
                    ))}
                    <TextInput
                      style={styles.input}
                      placeholder={t('common.commentOptional')}
                      placeholderTextColor={placeholderColor}
                      value={rateComment}
                      onChangeText={setRateComment}
                    />
                  </View>
                )}
                {ride.status === 'completed' && ride.paymentStatus === 'paid' && rated && (
                  <Pressable style={styles.btn} onPress={() => { setRide(null); setRated(false); }}>
                    <Text style={styles.btnText}>{t('common.newRide')}</Text>
                  </Pressable>
                )}
                {ride.status === 'requested' && (
                  <Pressable style={styles.btnSecondary} onPress={async () => {
                    await mobileApi.updateRideStatus(ride.id, 'cancelled');
                    setRide(null);
                  }}>
                    <Text style={styles.btnSecondaryText}>{t('common.cancel')}</Text>
                  </Pressable>
                )}
              </>
            )}
          </ScrollView>
        ) : (
          <ScrollView>
            {!ride ? (
              <>
                {earnings && (
                  <Text style={styles.muted}>{t('driver.todayEarnings')}: ${earnings.today.total.toFixed(2)}</Text>
                )}
                {approvalStatus !== 'approved' && (
                  <View style={styles.settingsBox}>
                    <Text style={styles.muted}>{t('driver.docsTitle')}</Text>
                    <TextInput style={styles.input} placeholder={t('driver.licenseUrl')} placeholderTextColor={placeholderColor} value={docs.licenseUrl} onChangeText={(licenseUrl) => setDocs({ ...docs, licenseUrl })} autoCapitalize="none" />
                    <TextInput style={styles.input} placeholder={t('driver.idUrl')} placeholderTextColor={placeholderColor} value={docs.idUrl} onChangeText={(idUrl) => setDocs({ ...docs, idUrl })} autoCapitalize="none" />
                    <TextInput style={styles.input} placeholder={t('driver.vehiclePhotoUrl')} placeholderTextColor={placeholderColor} value={docs.vehiclePhotoUrl} onChangeText={(vehiclePhotoUrl) => setDocs({ ...docs, vehiclePhotoUrl })} autoCapitalize="none" />
                    <Pressable style={styles.btnSecondary} onPress={async () => {
                      await mobileApi.submitDriverDocs({
                        licenseUrl: docs.licenseUrl || undefined,
                        idUrl: docs.idUrl || undefined,
                        vehiclePhotoUrl: docs.vehiclePhotoUrl || undefined,
                      });
                      const s = await mobileApi.getOnboardingStatus();
                      setApprovalStatus(s.approvalStatus);
                      showBanner(t('driver.docsSubmitted'));
                    }}>
                      <Text style={styles.btnSecondaryText}>{t('common.save')}</Text>
                    </Pressable>
                  </View>
                )}
                {connectStatus && !connectStatus.onboarded && (
                  <Pressable style={styles.btnSecondary} onPress={async () => {
                    const r = await mobileApi.startConnectOnboarding();
                    if (r.url) Linking.openURL(r.url);
                    else showBanner(te(r.message ?? t('common.stripeNotConfigured')), true);
                  }}>
                    <Text style={styles.btnSecondaryText}>{t('driver.setupStripe')}</Text>
                  </Pressable>
                )}
                <Pressable style={styles.btn} onPress={async () => {
                  if (approvalStatus !== 'approved') {
                    showBanner(t('driver.docsRequired'), true);
                    return;
                  }
                  await toggleOnline();
                }}>
                  <Text style={styles.btnText}>{online ? t('driver.goOffline') : t('driver.goOnline')}</Text>
                </Pressable>
                {pending.map((p) => (
                  <View key={p.id} style={styles.card}>
                    <Text style={styles.cardTitle}>${p.estimatedPrice} · {vehicle(p.vehicleType as VehicleType)}</Text>
                    <Text style={styles.muted} numberOfLines={2}>{p.pickupAddress}</Text>
                    <Pressable style={styles.btn} onPress={async () => setRide(await mobileApi.acceptRide(p.id))}>
                      <Text style={styles.btnText}>{t('common.accept')}</Text>
                    </Pressable>
                  </View>
                ))}
              </>
            ) : (
              <>
                <View style={styles.statusPill}>
                  <Text style={styles.statusPillText}>{rideStatus(ride.status)}</Text>
                </View>
                {ride.status === 'accepted' && (
                  <Pressable style={styles.btn} onPress={async () => {
                    openTurnByTurnNavigation(ride.pickupLat, ride.pickupLng, ride.pickupAddress, false);
                    setRide(await mobileApi.updateRideStatus(ride.id, 'arriving'));
                  }}>
                    <Text style={styles.btnText}>{t('driver.onTheWay')}</Text>
                  </Pressable>
                )}
                {ride.status === 'arriving' && (
                  <Pressable style={styles.btn} onPress={async () => setRide(await mobileApi.updateRideStatus(ride.id, 'in_progress'))}>
                    <Text style={styles.btnText}>{t('driver.startRide')}</Text>
                  </Pressable>
                )}
                {ride.status === 'in_progress' && (
                  <>
                    <Pressable style={styles.btnSecondary} onPress={() => openTurnByTurnNavigation(ride.dropoffLat, ride.dropoffLng, ride.dropoffAddress, true)}>
                      <Text style={styles.btnSecondaryText}>{t('common.navigate')}</Text>
                    </Pressable>
                    <Pressable style={styles.btn} onPress={async () => {
                      const updated = await mobileApi.updateRideStatus(ride.id, 'completed');
                      setRide(updated);
                      setRated(false);
                    }}>
                      <Text style={styles.btnText}>{t('driver.completeRide')}</Text>
                    </Pressable>
                  </>
                )}
                {['accepted', 'arriving', 'in_progress'].includes(ride.status) && (
                  <View style={styles.row}>
                    <TextInput style={[styles.input, { flex: 1 }]} placeholder={t('common.message')} value={chatText} onChangeText={setChatText} placeholderTextColor={placeholderColor} />
                    <Pressable style={styles.btnSmall} onPress={async () => {
                      if (!chatText.trim()) return;
                      await mobileApi.sendChatMessage(ride.id, chatText.trim());
                      setChatText('');
                    }}><Text style={styles.btnText}>→</Text></Pressable>
                  </View>
                )}
                {ride.status === 'completed' && !rated && (
                  <View style={styles.ratingBox}>
                    <Text style={styles.statusPillText}>{t('common.ratePassenger')}</Text>
                    {[5, 4, 3].map((stars) => (
                      <Pressable
                        key={stars}
                        style={styles.btnSecondary}
                        onPress={async () => {
                          await mobileApi.rateRide(ride.id, stars, rateComment || undefined);
                          setRated(true);
                          setRateComment('');
                        }}
                      >
                        <Text style={styles.btnSecondaryText}>{'★'.repeat(stars)}</Text>
                      </Pressable>
                    ))}
                    <TextInput
                      style={styles.input}
                      placeholder={t('common.commentOptional')}
                      placeholderTextColor={placeholderColor}
                      value={rateComment}
                      onChangeText={setRateComment}
                    />
                  </View>
                )}
                {ride.status === 'completed' && rated && (
                  <Pressable style={styles.btn} onPress={() => { setRide(null); setRated(false); }}>
                    <Text style={styles.btnText}>{t('common.done')}</Text>
                  </Pressable>
                )}
              </>
            )}
          </ScrollView>
        )}
        <Pressable style={styles.btnSecondary} onPress={() => { mobileApi.setToken(null); setUser(null); setScreen('auth'); setRide(null); }}>
          <Text style={styles.btnSecondaryText}>{t('common.logout')}</Text>
        </Pressable>
      </SafeAreaView>
    </View>
  );
}

