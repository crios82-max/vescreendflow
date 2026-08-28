import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import type { RideEstimateOption, VehicleType } from '@ride-app/shared';
import { colors } from './theme';

interface Props {
  options: RideEstimateOption[];
  selected: VehicleType;
  onSelect: (type: VehicleType) => void;
}

export function VehicleTypePicker({ options, selected, onSelect }: Props) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.row}>
      {options.map((option) => {
        const active = option.vehicleType === selected;
        return (
          <Pressable
            key={option.vehicleType}
            style={[styles.card, active && styles.cardActive]}
            onPress={() => onSelect(option.vehicleType)}
          >
            <Text style={styles.icon}>{option.icon}</Text>
            <Text style={styles.label}>{option.label}</Text>
            <Text style={styles.meta}>{option.seats} pax</Text>
            <Text style={[styles.price, active && styles.priceActive]}>${option.estimatedPrice}</Text>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  row: { marginVertical: 8 },
  card: {
    width: 110,
    padding: 12,
    marginRight: 8,
    borderRadius: 14,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    alignItems: 'center',
    gap: 4,
  },
  cardActive: {
    borderColor: colors.primary,
    backgroundColor: colors.primaryDim,
  },
  icon: { fontSize: 22 },
  label: { color: colors.text, fontWeight: '700', fontSize: 13 },
  meta: { color: colors.textMuted, fontSize: 11 },
  price: { color: colors.textMuted, fontWeight: '700', fontSize: 14, marginTop: 4 },
  priceActive: { color: colors.primary },
});
