import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import * as Location from 'expo-location';
import MapView, { Marker, PROVIDER_GOOGLE } from 'react-native-maps';
import { StatusBar } from 'expo-status-bar';
import type { Ride, User } from '@ride-app/shared';
import { RIDE_STATUS_LABELS } from '@ride-app/shared';
import { mobileApi } from './src/api';
import { mobileSocket } from './src/socket';

type Screen = 'auth' | 'home';

export default function App() {
  const [screen, setScreen] = useState<Screen>('auth');
  const [user, setUser] = useState<User | null>(null);
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [role, setRole] = useState<'passenger' | 'driver'>('passenger');
  const [form, setForm] = useState({ name: '', email: '', password: '', phone: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const [position, setPosition] = useState<{ latitude: number; longitude: number } | null>(null);
  const [dropoff, setDropoff] = useState<{ latitude: number; longitude: number } | null>(null);
  const [ride, setRide] = useState<Ride | null>(null);
  const [estimate, setEstimate] = useState<{ estimatedPrice: number; distanceKm: number } | null>(null);
  const [online, setOnline] = useState(false);
  const [pending, setPending] = useState<Array<{ id: string; pickupAddress: string; estimatedPrice: number }>>([]);

  useEffect(() => {
    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') return;
      const loc = await Location.getCurrentPositionAsync({});
      setPosition({ latitude: loc.coords.latitude, longitude: loc.coords.longitude });
    })();
  }, []);

  useEffect(() => {
    if (!ride) return;
    mobileSocket.emit('join:ride', ride.id);
    const onUpdate = (updated: Ride) => setRide(updated);
    mobileSocket.on('ride:updated', onUpdate);
    return () => mobileSocket.off('ride:updated', onUpdate);
  }, [ride?.id]);

  useEffect(() => {
    if (user?.role !== 'driver' || !online) return;
    const sub = Location.watchPositionAsync(
      { accuracy: Location.Accuracy.High, distanceInterval: 10 },
      (loc) => {
        const coords = { latitude: loc.coords.latitude, longitude: loc.coords.longitude };
        setPosition(coords);
        mobileApi.sendLocation(coords.latitude, coords.longitude).catch(() => {});
      },
    );
    return () => { sub.then((s) => s.remove()); };
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
      if (data.user.role === 'passenger') {
        const active = await mobileApi.getActiveRide();
        if (active.ride) setRide(active.ride);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
    } finally {
      setLoading(false);
    }
  };

  const requestRide = async () => {
    if (!position || !dropoff) return;
    setLoading(true);
    try {
      const created = await mobileApi.createRide({
        pickupAddress: 'Mi ubicación',
        pickupLat: position.latitude,
        pickupLng: position.longitude,
        dropoffAddress: `${dropoff.latitude.toFixed(5)}, ${dropoff.longitude.toFixed(5)}`,
        dropoffLat: dropoff.latitude,
        dropoffLng: dropoff.longitude,
      });
      setRide(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!position || !dropoff || user?.role !== 'passenger') return;
    mobileApi.estimateRide({
      pickupAddress: 'origen',
      pickupLat: position.latitude,
      pickupLng: position.longitude,
      dropoffAddress: 'destino',
      dropoffLat: dropoff.latitude,
      dropoffLng: dropoff.longitude,
    }).then(setEstimate).catch(() => setEstimate(null));
  }, [position, dropoff, user?.role]);

  const toggleOnline = async () => {
    if (!position) return;
    if (online) {
      await mobileApi.goOffline();
      setOnline(false);
      setPending([]);
      return;
    }
    await mobileApi.goOnline(position.latitude, position.longitude);
    mobileSocket.emit('join:drivers');
    setOnline(true);
    const list = await mobileApi.getPendingRides();
    setPending(list.rides);
  };

  if (screen === 'auth') {
    return (
      <SafeAreaView style={styles.container}>
        <StatusBar style="light" />
        <Text style={styles.title}>Ride Mobile</Text>
        <View style={styles.row}>
          <Pressable style={[styles.chip, mode === 'login' && styles.chipActive]} onPress={() => setMode('login')}><Text>Login</Text></Pressable>
          <Pressable style={[styles.chip, mode === 'register' && styles.chipActive]} onPress={() => setMode('register')}><Text>Registro</Text></Pressable>
        </View>
        {mode === 'register' && (
          <View style={styles.row}>
            <Pressable style={[styles.chip, role === 'passenger' && styles.chipActive]} onPress={() => setRole('passenger')}><Text>Pasajero</Text></Pressable>
            <Pressable style={[styles.chip, role === 'driver' && styles.chipActive]} onPress={() => setRole('driver')}><Text>Conductor</Text></Pressable>
          </View>
        )}
        {mode === 'register' && <TextInput style={styles.input} placeholder="Nombre" placeholderTextColor="#888" value={form.name} onChangeText={(name) => setForm({ ...form, name })} />}
        <TextInput style={styles.input} placeholder="Email" placeholderTextColor="#888" autoCapitalize="none" value={form.email} onChangeText={(email) => setForm({ ...form, email })} />
        <TextInput style={styles.input} placeholder="Contraseña" placeholderTextColor="#888" secureTextEntry value={form.password} onChangeText={(password) => setForm({ ...form, password })} />
        {error ? <Text style={styles.error}>{error}</Text> : null}
        <Pressable style={styles.btn} onPress={submitAuth} disabled={loading}>
          {loading ? <ActivityIndicator color="#000" /> : <Text style={styles.btnText}>{mode === 'login' ? 'Entrar' : 'Crear cuenta'}</Text>}
        </Pressable>
      </SafeAreaView>
    );
  }

  return (
    <View style={styles.flex}>
      <StatusBar style="light" />
      {position && (
        <MapView
          style={styles.map}
          provider={PROVIDER_GOOGLE}
          initialRegion={{
            latitude: position.latitude,
            longitude: position.longitude,
            latitudeDelta: 0.05,
            longitudeDelta: 0.05,
          }}
          onPress={(e) => user?.role === 'passenger' && !ride && setDropoff(e.nativeEvent.coordinate)}
        >
          <Marker coordinate={position} title="Tú" />
          {dropoff && <Marker coordinate={dropoff} title="Destino" pinColor="green" />}
        </MapView>
      )}
      <SafeAreaView style={styles.sheet}>
        <Text style={styles.sheetTitle}>{user?.name} — {user?.role === 'passenger' ? 'Pasajero' : 'Conductor'}</Text>
        {user?.role === 'passenger' ? (
          <ScrollView>
            {!ride ? (
              <>
                <Text style={styles.muted}>Toca el mapa para elegir destino</Text>
                {estimate && <Text style={styles.muted}>${estimate.estimatedPrice} — {estimate.distanceKm} km</Text>}
                <Pressable style={styles.btn} onPress={requestRide} disabled={!dropoff || loading}>
                  <Text style={styles.btnText}>Pedir Ride</Text>
                </Pressable>
              </>
            ) : (
              <>
                <Text>{RIDE_STATUS_LABELS[ride.status]}</Text>
                <Text style={styles.muted}>${ride.finalPrice ?? ride.estimatedPrice}</Text>
                {ride.status === 'completed' && ride.paymentStatus !== 'paid' && (
                  <Pressable style={styles.btn} onPress={async () => {
                    const r = await mobileApi.payRide(ride.id);
                    setRide(r.ride);
                  }}>
                    <Text style={styles.btnText}>Pagar mock •••• 4242</Text>
                  </Pressable>
                )}
                {ride.status === 'requested' && (
                  <Pressable style={styles.btnSecondary} onPress={async () => {
                    await mobileApi.updateRideStatus(ride.id, 'cancelled');
                    setRide(null);
                  }}>
                    <Text>Cancelar</Text>
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
                    <Text>${p.estimatedPrice} — {p.pickupAddress}</Text>
                    <Pressable style={styles.btn} onPress={async () => {
                      const accepted = await mobileApi.acceptRide(p.id);
                      setRide(accepted);
                    }}>
                      <Text style={styles.btnText}>Aceptar</Text>
                    </Pressable>
                  </View>
                ))}
              </>
            ) : (
              <>
                <Text>{RIDE_STATUS_LABELS[ride.status]}</Text>
                {ride.status === 'accepted' && (
                  <Pressable style={styles.btn} onPress={async () => setRide(await mobileApi.updateRideStatus(ride.id, 'arriving'))}>
                    <Text style={styles.btnText}>En camino</Text>
                  </Pressable>
                )}
                {ride.status === 'arriving' && (
                  <Pressable style={styles.btn} onPress={async () => setRide(await mobileApi.updateRideStatus(ride.id, 'in_progress'))}>
                    <Text style={styles.btnText}>Iniciar viaje</Text>
                  </Pressable>
                )}
                {ride.status === 'in_progress' && (
                  <Pressable style={styles.btn} onPress={async () => {
                    await mobileApi.updateRideStatus(ride.id, 'completed');
                    setRide(null);
                  }}>
                    <Text style={styles.btnText}>Completar</Text>
                  </Pressable>
                )}
              </>
            )}
          </ScrollView>
        )}
        <Pressable style={styles.btnSecondary} onPress={() => { setUser(null); setScreen('auth'); setRide(null); }}>
          <Text>Salir</Text>
        </Pressable>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1, backgroundColor: '#000' },
  container: { flex: 1, backgroundColor: '#000', padding: 20, gap: 12 },
  map: { flex: 1 },
  title: { color: '#fff', fontSize: 28, fontWeight: '700' },
  input: { backgroundColor: '#111', color: '#fff', borderRadius: 12, padding: 14, borderWidth: 1, borderColor: '#333' },
  btn: { backgroundColor: '#fff', borderRadius: 999, padding: 14, alignItems: 'center', marginTop: 8 },
  btnSecondary: { backgroundColor: '#222', borderRadius: 999, padding: 12, alignItems: 'center', marginTop: 8 },
  btnText: { color: '#000', fontWeight: '700' },
  error: { color: '#ff8f8f' },
  row: { flexDirection: 'row', gap: 8 },
  chip: { backgroundColor: '#222', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 999 },
  chipActive: { backgroundColor: '#fff' },
  sheet: { backgroundColor: '#111', padding: 16, borderTopLeftRadius: 20, borderTopRightRadius: 20, maxHeight: '40%' },
  sheetTitle: { color: '#fff', fontSize: 18, fontWeight: '600', marginBottom: 8 },
  muted: { color: '#aaa', marginBottom: 8 },
  card: { backgroundColor: '#0d0d0d', borderRadius: 12, padding: 12, marginTop: 8, gap: 8 },
});
