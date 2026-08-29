import { useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import {
  DELIVERY_COUNTRIES,
  DELIVERY_COUNTRY_META,
  deliveryCities,
  listDeliveryRestaurants,
  type DeliveryCountry,
  type DeliveryRestaurant,
  type RestaurantCategory,
} from '@ride-app/shared';
import { colors, placeholderColor } from './theme';
import { useMobileI18n } from './i18n';

export interface RestaurantPick {
  restaurant: DeliveryRestaurant;
  latitude: number;
  longitude: number;
  address: string;
}

interface Props {
  selectedId?: string | null;
  onSelect: (pick: RestaurantPick) => void;
}

type CategoryFilter = RestaurantCategory | 'all';

export function RestaurantPicker({ selectedId, onSelect }: Props) {
  const { t } = useMobileI18n();
  const [country, setCountry] = useState<DeliveryCountry>('ES');
  const [city, setCity] = useState(DELIVERY_COUNTRY_META.ES.defaultCity);
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [q, setQ] = useState('');

  const cities = useMemo(() => deliveryCities(country), [country]);

  useEffect(() => {
    setCity(DELIVERY_COUNTRY_META[country].defaultCity);
    setQ('');
  }, [country]);

  const restaurants = useMemo(
    () => listDeliveryRestaurants({ country, city, category, q: q || undefined }),
    [country, city, category, q],
  );

  return (
    <View style={styles.wrap}>
      <Text style={styles.hint}>{t('service.pickRestaurant')}</Text>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.row}>
        {DELIVERY_COUNTRIES.map((code) => (
          <Pressable key={code} style={[styles.chip, country === code && styles.chipActive]} onPress={() => setCountry(code)}>
            <Text style={country === code ? styles.chipTextActive : styles.chipText}>{t(DELIVERY_COUNTRY_META[code].labelKey)}</Text>
          </Pressable>
        ))}
      </ScrollView>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.row}>
        {cities.map((c) => (
          <Pressable key={c} style={[styles.chip, city === c && styles.chipActive]} onPress={() => setCity(c)}>
            <Text style={city === c ? styles.chipTextActive : styles.chipText}>{c}</Text>
          </Pressable>
        ))}
      </ScrollView>
      <View style={styles.row}>
        {([
          ['all', t('service.allCategories')],
          ['fast_food', t('service.fastFood')],
          ['restaurant', t('service.restaurants')],
        ] as const).map(([key, label]) => (
          <Pressable key={key} style={[styles.chip, category === key && styles.chipActive]} onPress={() => setCategory(key)}>
            <Text style={category === key ? styles.chipTextActive : styles.chipText}>{label}</Text>
          </Pressable>
        ))}
      </View>
      <TextInput
        style={styles.input}
        placeholder={t('service.searchRestaurant')}
        placeholderTextColor={placeholderColor}
        value={q}
        onChangeText={setQ}
      />
      <ScrollView style={styles.list} nestedScrollEnabled>
        {restaurants.length === 0 ? (
          <Text style={styles.muted}>{t('service.noRestaurants')}</Text>
        ) : (
          restaurants.map((r) => {
            const active = selectedId === r.id;
            return (
              <Pressable
                key={r.id}
                style={[styles.card, active && styles.cardActive]}
                onPress={() => onSelect({
                  restaurant: r,
                  latitude: r.lat,
                  longitude: r.lng,
                  address: `${r.name} · ${r.address}`,
                })}
              >
                <Text style={styles.badge}>
                  {r.category === 'fast_food' ? t('service.fastFood') : t('service.restaurants')}
                </Text>
                <Text style={styles.name}>{r.name}</Text>
                <Text style={styles.meta}>{r.address}</Text>
              </Pressable>
            );
          })
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: 8, marginBottom: 4 },
  hint: { color: colors.textMuted, fontSize: 12 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
    marginRight: 4,
  },
  chipActive: { borderColor: colors.primary, backgroundColor: colors.primaryDim },
  chipText: { color: colors.textMuted, fontSize: 12, fontWeight: '600' },
  chipTextActive: { color: colors.primary, fontSize: 12, fontWeight: '700' },
  input: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    color: colors.text,
    paddingHorizontal: 12,
    paddingVertical: 10,
  },
  list: { maxHeight: 180 },
  card: {
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
    marginBottom: 8,
  },
  cardActive: { borderColor: colors.primary, backgroundColor: colors.primaryDim },
  badge: { color: colors.primary, fontSize: 10, fontWeight: '700', textTransform: 'uppercase' },
  name: { color: colors.text, fontWeight: '700', marginTop: 2 },
  meta: { color: colors.textMuted, fontSize: 12, marginTop: 2 },
  muted: { color: colors.textMuted },
});
