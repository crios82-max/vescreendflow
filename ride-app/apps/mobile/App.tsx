import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import * as Location from 'expo-location';
import MapView, { Marker, Polyline, PROVIDER_GOOGLE } from 'react-native-maps';
import { StatusBar } from 'expo-status-bar';
import type { Ride, RideEstimate, User, VehicleType } from '@ride-app/shared';
import { RIDE_STATUS_LABELS, VEHICLE_OPTIONS, VEHICLE_TYPES } from '@ride-app/shared';
import { mobileApi } from './src/api';
import { getMobileSocket, reconnectSocket } from './src/socket';
import { PlaceSearch } from './src/PlaceSearch';
import { VehicleTypePicker } from './src/VehicleTypePicker';
import { defaultApiUrl, getApiUrl } from './src/storage';
import { registerForPushNotifications } from './src/push';
import { decodePolyline } from './src/polyline';
import { openTurnByTurnNavigation } from './src/navigation';

type Screen = 'auth' | 'home';
type Tab = 'ride' | 'history';

export default function App() {
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
  const [online, setOnline] = useState(false);
  const [pending, setPending] = useState<Array<{ id: string; pickupAddress: string; estimatedPrice: number; vehicleType: string }>>([]);
  const [tab, setTab] = useState<Tab>('ride');
  const [history, setHistory] = useState<Ride[]>([]);
  const [rated, setRated] = useState(false);
  const [tipAmount, setTipAmount] = useState(2);
  const [etaPickup, setEtaPickup] = useState<number | null>(null);
  const [rideForName, setRideForName] = useState('');
  const [stop, setStop] = useState<{ latitude: number; longitude: number; address: string } | null>(null);
  const [phoneVerified, setPhoneVerified] = useState(true);
  const [phoneInput, setPhoneInput] = useState('');
  const [otpInput, setOtpInput] = useState('');

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
      setPickup({ ...coords, address: 'Mi ubicación' });
    })();
  }, [ready]);

  useEffect(() => {
    if (!ride) return;
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
    return () => cleanup();
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
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
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
        pickupAddress: pickup?.address ?? 'Mi ubicación',
        pickupLat: origin.latitude,
        pickupLng: origin.longitude,
        dropoffAddress: dropoff.address,
        dropoffLat: dropoff.latitude,
        dropoffLng: dropoff.longitude,
        vehicleType,
        rideForName: rideForName || undefined,
        stops: stop ? [{ address: stop.address, lat: stop.latitude, lng: stop.longitude }] : undefined,
      });
      setRide(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const origin = pickup ?? position;
    if (!origin || !dropoff || user?.role !== 'passenger') return;
    mobileApi.estimateRide({
      pickupAddress: pickup?.address ?? 'origen',
      pickupLat: origin.latitude,
      pickupLng: origin.longitude,
      dropoffAddress: dropoff.address,
      dropoffLat: dropoff.latitude,
      dropoffLng: dropoff.longitude,
    }).then((data) => {
      setEstimate(data);
      setVehicleType(data.options[0]?.vehicleType ?? 'standard');
    }).catch(() => setEstimate(null));
  }, [pickup, position, dropoff, user?.role]);

  const loadHistory = async () => {
    try {
      const data = await mobileApi.getHistory();
      setHistory(data.rides);
    } catch {
      setHistory([]);
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
        <ActivityIndicator color="#fff" size="large" />
      </View>
    );
  }

  const connectionBadge = apiConnected === null ? '…' : apiConnected ? '🟢 API' : '🔴 Sin API';

  if (screen === 'auth') {
    return (
      <SafeAreaView style={styles.container}>
        <StatusBar style="light" />
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.flex}>
          <ScrollView contentContainerStyle={styles.authScroll} keyboardShouldPersistTaps="handled">
            <Text style={styles.title}>Ride</Text>
            <Text style={styles.subtitle}>{connectionBadge}</Text>

            <Pressable onPress={() => setShowSettings(!showSettings)}>
              <Text style={styles.link}>Configurar servidor API</Text>
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
                  placeholderTextColor="#666"
                />
                <Pressable style={styles.btnSmall} onPress={saveApiUrl}>
                  <Text style={styles.btnText}>Guardar y probar</Text>
                </Pressable>
              </View>
            )}

            <View style={styles.row}>
              <Pressable style={[styles.chip, mode === 'login' && styles.chipActive]} onPress={() => setMode('login')}>
                <Text style={mode === 'login' ? styles.chipTextActive : styles.chipText}>Login</Text>
              </Pressable>
              <Pressable style={[styles.chip, mode === 'register' && styles.chipActive]} onPress={() => setMode('register')}>
                <Text style={mode === 'register' ? styles.chipTextActive : styles.chipText}>Registro</Text>
              </Pressable>
            </View>
            {mode === 'register' && (
              <View style={styles.row}>
                <Pressable style={[styles.chip, role === 'passenger' && styles.chipActive]} onPress={() => setRole('passenger')}>
                  <Text style={role === 'passenger' ? styles.chipTextActive : styles.chipText}>Pasajero</Text>
                </Pressable>
                <Pressable style={[styles.chip, role === 'driver' && styles.chipActive]} onPress={() => setRole('driver')}>
                  <Text style={role === 'driver' ? styles.chipTextActive : styles.chipText}>Conductor</Text>
                </Pressable>
              </View>
            )}
            {mode === 'register' && (
              <TextInput style={styles.input} placeholder="Nombre" placeholderTextColor="#888" value={form.name} onChangeText={(name) => setForm({ ...form, name })} />
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
                      {VEHICLE_OPTIONS[type].icon} {VEHICLE_OPTIONS[type].label}
                    </Text>
                  </Pressable>
                ))}
              </View>
            )}
            <TextInput style={styles.input} placeholder="Email" placeholderTextColor="#888" autoCapitalize="none" keyboardType="email-address" value={form.email} onChangeText={(email) => setForm({ ...form, email })} />
            <TextInput style={styles.input} placeholder="Contraseña" placeholderTextColor="#888" secureTextEntry value={form.password} onChangeText={(password) => setForm({ ...form, password })} />
            {error ? <Text style={styles.error}>{error}</Text> : null}
            <Pressable style={[styles.btn, apiConnected === false && styles.btnDisabled]} onPress={submitAuth} disabled={loading || apiConnected === false}>
              {loading ? <ActivityIndicator color="#000" /> : <Text style={styles.btnText}>{mode === 'login' ? 'Entrar' : 'Crear cuenta'}</Text>}
            </Pressable>
            {apiConnected === false && (
              <Text style={styles.muted}>Sin conexión al API. Puedes seguir viendo la UI cuando vuelvas a casa.</Text>
            )}
          </ScrollView>
        </KeyboardAvoidingView>
      </SafeAreaView>
    );
  }

  const mapCenter = driverPos ?? dropoff ?? pickup ?? position;
  const selectedOption = estimate?.options.find((o) => o.vehicleType === vehicleType);
  const routeCoords = ride?.routePolyline ? decodePolyline(ride.routePolyline) : estimate?.polyline ? decodePolyline(estimate.polyline) : [];

  return (
    <View style={styles.flex}>
      <StatusBar style="light" />
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
          {(pickup ?? position) && <Marker coordinate={(pickup ?? position)!} title="Origen" pinColor="#fff" />}
          {dropoff && <Marker coordinate={dropoff} title="Destino" pinColor="green" />}
          {driverPos && <Marker coordinate={driverPos} title="Conductor" pinColor="#3b82f6" />}
          {routeCoords.length > 0 && <Polyline coordinates={routeCoords} strokeColor="#3b82f6" strokeWidth={4} />}
        </MapView>
      )}
      {user?.role === 'passenger' && !ride && (
        <SafeAreaView style={styles.searchOverlay}>
          <PlaceSearch placeholder="Origen" bias={position} onSelect={(p) => setPickup(p)} />
          <PlaceSearch placeholder="¿A dónde vas?" bias={pickup ?? position} onSelect={(p) => setDropoff(p)} />
          <TextInput style={styles.input} placeholder="Parada opcional" placeholderTextColor="#666" value={stop?.address ?? ''} editable={false} />
          <PlaceSearch placeholder="Agregar parada" bias={pickup ?? position} onSelect={(p) => setStop(p)} />
          <TextInput style={styles.input} placeholder="Viaje para (nombre)" placeholderTextColor="#666" value={rideForName} onChangeText={setRideForName} />
        </SafeAreaView>
      )}
      <SafeAreaView style={styles.sheet}>
        {user?.role === 'passenger' && !phoneVerified && (
          <View style={styles.settingsBox}>
            <Text style={styles.muted}>Verifica tu teléfono</Text>
            <TextInput style={styles.input} placeholder="+58..." value={phoneInput} onChangeText={setPhoneInput} placeholderTextColor="#666" />
            <TextInput style={styles.input} placeholder="Código 6 dígitos" value={otpInput} onChangeText={setOtpInput} placeholderTextColor="#666" keyboardType="number-pad" />
            <View style={styles.row}>
              <Pressable style={styles.btnSmall} onPress={async () => {
                const r = await mobileApi.sendPhoneOtp(phoneInput);
                if (r.devHint) alert(`Dev OTP: ${r.devHint}`);
              }}><Text style={styles.btnText}>Enviar OTP</Text></Pressable>
              <Pressable style={styles.btnSmall} onPress={async () => {
                await mobileApi.confirmPhoneOtp(phoneInput, otpInput);
                setPhoneVerified(true);
              }}><Text style={styles.btnText}>Verificar</Text></Pressable>
            </View>
          </View>
        )}
        <View style={styles.sheetHeader}>
          <Text style={styles.sheetTitle}>{user?.name}</Text>
          <Text style={styles.roleBadge}>{user?.role === 'passenger' ? 'Pasajero' : 'Conductor'} · {connectionBadge}</Text>
          <View style={styles.row}>
            <Pressable style={[styles.chip, tab === 'ride' && styles.chipActive]} onPress={() => setTab('ride')}>
              <Text style={tab === 'ride' ? styles.chipTextActive : styles.chipText}>Viaje</Text>
            </Pressable>
            <Pressable style={[styles.chip, tab === 'history' && styles.chipActive]} onPress={() => setTab('history')}>
              <Text style={tab === 'history' ? styles.chipTextActive : styles.chipText}>Historial</Text>
            </Pressable>
          </View>
        </View>
        {tab === 'history' ? (
          <ScrollView>
            {history.length === 0 ? (
              <Text style={styles.muted}>Sin viajes anteriores</Text>
            ) : history.map((h) => (
              <View key={h.id} style={styles.card}>
                <Text style={styles.cardTitle}>{RIDE_STATUS_LABELS[h.status]}</Text>
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
                {dropoff && <Text style={styles.muted} numberOfLines={2}>{dropoff.address}</Text>}
                {estimate && (
                  <Text style={styles.muted}>{estimate.distanceKm} km · ~{Math.round(estimate.durationMin)} min</Text>
                )}
                {estimate && (
                  <VehicleTypePicker
                    options={estimate.options}
                    selected={vehicleType}
                    onSelect={setVehicleType}
                  />
                )}
                {error ? <Text style={styles.error}>{error}</Text> : null}
                <Pressable style={[styles.btn, (!dropoff || loading) && styles.btnDisabled]} onPress={requestRide} disabled={!dropoff || loading}>
                  <Text style={styles.btnText}>
                    {loading ? 'Solicitando…' : `Pedir ${selectedOption?.label ?? 'Ride'} · $${selectedOption?.estimatedPrice ?? ''}`}
                  </Text>
                </Pressable>
              </>
            ) : (
              <>
                <Text style={styles.status}>{RIDE_STATUS_LABELS[ride.status]}</Text>
                {etaPickup != null && ['accepted', 'arriving'].includes(ride.status) && (
                  <Text style={styles.muted}>Llega en ~{etaPickup} min</Text>
                )}
                <Text style={styles.muted}>{VEHICLE_OPTIONS[ride.vehicleType].label}</Text>
                <Text style={styles.price}>${ride.finalPrice ?? ride.estimatedPrice}</Text>
                {ride.status === 'completed' && ride.paymentStatus !== 'paid' && (
                  <>
                    <View style={styles.row}>
                      {[0, 1, 2, 5].map((t) => (
                        <Pressable key={t} style={[styles.chip, tipAmount === t && styles.chipActive]} onPress={() => setTipAmount(t)}>
                          <Text style={tipAmount === t ? styles.chipTextActive : styles.chipText}>{t === 0 ? 'Sin tip' : `$${t}`}</Text>
                        </Pressable>
                      ))}
                    </View>
                    <Pressable style={styles.btn} onPress={async () => {
                      const r = await mobileApi.payRide(ride.id, tipAmount);
                      setRide(r.ride);
                    }}>
                      <Text style={styles.btnText}>Pagar ${(ride.finalPrice ?? ride.estimatedPrice) + tipAmount}</Text>
                    </Pressable>
                  </>
                )}
                <View style={styles.row}>
                  <Pressable style={styles.btnSecondary} onPress={async () => {
                    const s = await mobileApi.shareRide(ride.id);
                    alert('Comparte: ' + s.shareToken);
                  }}>
                    <Text style={styles.btnSecondaryText}>Compartir</Text>
                  </Pressable>
                  <Pressable style={styles.btnSecondary} onPress={() => mobileApi.triggerSos(ride.id, position?.latitude, position?.longitude)}>
                    <Text style={styles.btnSecondaryText}>SOS</Text>
                  </Pressable>
                </View>
                {ride.status === 'completed' && ride.paymentStatus === 'paid' && !rated && (
                  <View style={styles.ratingBox}>
                    <Text style={styles.status}>Califica tu viaje</Text>
                    {[1, 2, 3, 4, 5].map((stars) => (
                      <Pressable
                        key={stars}
                        style={styles.btnSecondary}
                        onPress={async () => {
                          await mobileApi.rateRide(ride.id, stars);
                          setRated(true);
                        }}
                      >
                        <Text style={styles.btnSecondaryText}>{'★'.repeat(stars)}</Text>
                      </Pressable>
                    ))}
                  </View>
                )}
                {ride.status === 'completed' && ride.paymentStatus === 'paid' && rated && (
                  <Pressable style={styles.btn} onPress={() => { setRide(null); setRated(false); }}>
                    <Text style={styles.btnText}>Nuevo viaje</Text>
                  </Pressable>
                )}
                {ride.status === 'requested' && (
                  <Pressable style={styles.btnSecondary} onPress={async () => {
                    await mobileApi.updateRideStatus(ride.id, 'cancelled');
                    setRide(null);
                  }}>
                    <Text style={styles.btnSecondaryText}>Cancelar</Text>
                  </Pressable>
                )}
              </>
            )}
          </ScrollView>
        ) : (
          <ScrollView>
            {!ride ? (
              <>
                <Pressable style={styles.btn} onPress={toggleOnline}>
                  <Text style={styles.btnText}>{online ? 'Ir offline' : 'Ir online'}</Text>
                </Pressable>
                {pending.map((p) => (
                  <View key={p.id} style={styles.card}>
                    <Text style={styles.cardTitle}>${p.estimatedPrice} · {VEHICLE_OPTIONS[p.vehicleType as VehicleType]?.label ?? p.vehicleType}</Text>
                    <Text style={styles.muted} numberOfLines={2}>{p.pickupAddress}</Text>
                    <Pressable style={styles.btn} onPress={async () => setRide(await mobileApi.acceptRide(p.id))}>
                      <Text style={styles.btnText}>Aceptar</Text>
                    </Pressable>
                  </View>
                ))}
              </>
            ) : (
              <>
                <Text style={styles.status}>{RIDE_STATUS_LABELS[ride.status]}</Text>
                {ride.status === 'accepted' && (
                  <Pressable style={styles.btn} onPress={async () => {
                    openTurnByTurnNavigation(ride.pickupLat, ride.pickupLng, ride.pickupAddress, false);
                    setRide(await mobileApi.updateRideStatus(ride.id, 'arriving'));
                  }}>
                    <Text style={styles.btnText}>Navegar al pickup</Text>
                  </Pressable>
                )}
                {ride.status === 'arriving' && (
                  <Pressable style={styles.btn} onPress={async () => setRide(await mobileApi.updateRideStatus(ride.id, 'in_progress'))}>
                    <Text style={styles.btnText}>Iniciar viaje</Text>
                  </Pressable>
                )}
                {ride.status === 'in_progress' && (
                  <>
                    <Pressable style={styles.btnSecondary} onPress={() => openTurnByTurnNavigation(ride.dropoffLat, ride.dropoffLng, ride.dropoffAddress, true)}>
                      <Text style={styles.btnSecondaryText}>Navegar al destino</Text>
                    </Pressable>
                    <Pressable style={styles.btn} onPress={async () => {
                      const updated = await mobileApi.updateRideStatus(ride.id, 'completed');
                      setRide(updated);
                      setRated(false);
                    }}>
                      <Text style={styles.btnText}>Completar</Text>
                    </Pressable>
                  </>
                )}
                {ride.status === 'completed' && !rated && (
                  <View style={styles.ratingBox}>
                    <Text style={styles.status}>Califica al pasajero</Text>
                    {[5, 4, 3].map((stars) => (
                      <Pressable
                        key={stars}
                        style={styles.btnSecondary}
                        onPress={async () => {
                          await mobileApi.rateRide(ride.id, stars);
                          setRated(true);
                        }}
                      >
                        <Text style={styles.btnSecondaryText}>{'★'.repeat(stars)}</Text>
                      </Pressable>
                    ))}
                  </View>
                )}
                {ride.status === 'completed' && rated && (
                  <Pressable style={styles.btn} onPress={() => { setRide(null); setRated(false); }}>
                    <Text style={styles.btnText}>Listo</Text>
                  </Pressable>
                )}
              </>
            )}
          </ScrollView>
        )}
        <Pressable style={styles.btnSecondary} onPress={() => { mobileApi.setToken(null); setUser(null); setScreen('auth'); setRide(null); }}>
          <Text style={styles.btnSecondaryText}>Salir</Text>
        </Pressable>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#000' },
  center: { flex: 1, backgroundColor: '#000', alignItems: 'center', justifyContent: 'center' },
  container: { flex: 1, backgroundColor: '#000' },
  authScroll: { padding: 20, gap: 12 },
  map: { flex: 1 },
  searchOverlay: { position: 'absolute', top: 0, left: 16, right: 16, zIndex: 20, gap: 8 },
  title: { color: '#fff', fontSize: 32, fontWeight: '800' },
  subtitle: { color: '#888', marginBottom: 4 },
  link: { color: '#aaa', textDecorationLine: 'underline', marginBottom: 8 },
  settingsBox: { gap: 8, marginBottom: 8 },
  input: { backgroundColor: '#111', color: '#fff', borderRadius: 12, padding: 14, borderWidth: 1, borderColor: '#333' },
  btn: { backgroundColor: '#fff', borderRadius: 999, padding: 16, alignItems: 'center', marginTop: 8 },
  btnSmall: { backgroundColor: '#fff', borderRadius: 999, padding: 12, alignItems: 'center' },
  btnDisabled: { opacity: 0.45 },
  btnSecondary: { backgroundColor: '#222', borderRadius: 999, padding: 14, alignItems: 'center', marginTop: 8 },
  btnSecondaryText: { color: '#fff', fontWeight: '600' },
  btnText: { color: '#000', fontWeight: '700', fontSize: 16 },
  error: { color: '#ff8f8f' },
  row: { flexDirection: 'row', gap: 8 },
  chip: { backgroundColor: '#222', paddingHorizontal: 14, paddingVertical: 10, borderRadius: 999 },
  chipActive: { backgroundColor: '#fff' },
  chipText: { color: '#fff' },
  chipTextActive: { color: '#000', fontWeight: '600' },
  sheet: { backgroundColor: '#111', padding: 16, borderTopLeftRadius: 24, borderTopRightRadius: 24, maxHeight: '42%' },
  sheetHeader: { marginBottom: 10 },
  sheetTitle: { color: '#fff', fontSize: 20, fontWeight: '700' },
  roleBadge: { color: '#888', fontSize: 13, marginTop: 2 },
  muted: { color: '#aaa', marginBottom: 8, fontSize: 14 },
  price: { color: '#fff', fontSize: 22, fontWeight: '700', marginBottom: 8 },
  status: { color: '#fff', fontSize: 18, fontWeight: '600', marginBottom: 8 },
  card: { backgroundColor: '#0d0d0d', borderRadius: 14, padding: 14, marginTop: 10, gap: 6, borderWidth: 1, borderColor: '#222' },
  cardTitle: { color: '#fff', fontSize: 18, fontWeight: '700' },
  ratingBox: { gap: 6, marginTop: 8 },
});
