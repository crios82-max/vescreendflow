import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import type { RideEstimateOption, VehicleType } from '@ride-app/shared';

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
            <Text style={styles.price}>${option.estimatedPrice}</Text>
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
    backgroundColor: '#0d0d0d',
    borderWidth: 1,
    borderColor: '#2a2a2a',
    alignItems: 'center',
    gap: 4,
  },
  cardActive: { borderColor: '#fff', backgroundColor: '#1a1a1a' },
  icon: { fontSize: 22 },
  label: { color: '#fff', fontWeight: '700', fontSize: 13 },
  meta: { color: '#888', fontSize: 11 },
  price: { color: '#fff', fontWeight: '700', fontSize: 14, marginTop: 4 },
});
