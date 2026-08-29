import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { mobileApi } from './api';
import { colors } from './theme';
import { useMobileI18n } from './i18n';

interface Place {
  id: string;
  label: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
}

interface Props {
  currentDropoff?: { latitude: number; longitude: number; address: string } | null;
  onSelect: (place: { latitude: number; longitude: number; address: string }) => void;
}

export function SavedPlacesBar({ currentDropoff, onSelect }: Props) {
  const { t, te } = useMobileI18n();
  const [places, setPlaces] = useState<Place[]>([]);
  const [error, setError] = useState('');

  const load = () => {
    mobileApi.getSavedPlaces()
      .then((r) => {
        setPlaces(r.places);
        setError('');
      })
      .catch((err) => setError(te(err instanceof Error ? err.message : t('common.loadFailed'))));
  };

  useEffect(() => { load(); }, []);

  const saveCurrent = async (label: string) => {
    if (!currentDropoff) return;
    try {
      await mobileApi.savePlace({
        label,
        name: label,
        address: currentDropoff.address,
        lat: currentDropoff.latitude,
        lng: currentDropoff.longitude,
      });
      load();
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    }
  };

  return (
    <>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.row}>
        {places.map((p) => (
          <Pressable
            key={p.id}
            style={styles.chip}
            onPress={() => onSelect({ latitude: p.lat, longitude: p.lng, address: p.address })}
          >
            <Text style={styles.chipText}>{p.label}</Text>
          </Pressable>
        ))}
        {currentDropoff && (
          <>
            <Pressable style={styles.chip} onPress={() => saveCurrent(t('places.home'))}>
              <Text style={styles.chipText}>{t('places.addHome')}</Text>
            </Pressable>
            <Pressable style={styles.chip} onPress={() => saveCurrent(t('places.work'))}>
              <Text style={styles.chipText}>{t('places.addWork')}</Text>
            </Pressable>
          </>
        )}
      </ScrollView>
    </>
  );
}

const styles = StyleSheet.create({
  row: { marginVertical: 6, maxHeight: 40 },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    marginRight: 8,
  },
  chipText: { color: colors.textMuted, fontSize: 13, fontWeight: '600' },
  error: { color: colors.danger, fontSize: 12, marginBottom: 4 },
});
