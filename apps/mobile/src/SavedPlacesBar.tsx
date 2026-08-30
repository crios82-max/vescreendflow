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
  const { t } = useMobileI18n();
  const [places, setPlaces] = useState<Place[]>([]);

  const load = () => {
    mobileApi.getSavedPlaces().then((r) => setPlaces(r.places)).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const saveCurrent = async (label: string) => {
    if (!currentDropoff) return;
    await mobileApi.savePlace({
      label,
      name: label,
      address: currentDropoff.address,
      lat: currentDropoff.latitude,
      lng: currentDropoff.longitude,
    });
    load();
  };

  return (
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
});
