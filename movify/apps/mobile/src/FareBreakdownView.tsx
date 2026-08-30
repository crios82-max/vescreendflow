import { StyleSheet, Text, View } from 'react-native';
import type { FareBreakdown } from '@ride-app/shared';
import { colors } from './theme';
import { useMobileI18n } from './i18n';

export function FareBreakdownView({
  breakdown,
  surgeMultiplier,
}: {
  breakdown: FareBreakdown | null | undefined;
  surgeMultiplier?: number;
}) {
  const { t } = useMobileI18n();
  if (!breakdown) return null;
  return (
    <View style={styles.box}>
      <View style={styles.row}><Text style={styles.label}>{t('common.baseFare')}</Text><Text style={styles.value}>${breakdown.baseFare}</Text></View>
      <View style={styles.row}><Text style={styles.label}>{t('common.distance')}</Text><Text style={styles.value}>${breakdown.distanceFare}</Text></View>
      <View style={styles.row}><Text style={styles.label}>{t('common.time')}</Text><Text style={styles.value}>${breakdown.timeFare}</Text></View>
      {(surgeMultiplier ?? breakdown.surgeMultiplier) > 1 && (
        <View style={styles.row}>
          <Text style={styles.label}>{t('common.surgeLine', { multiplier: breakdown.surgeMultiplier })}</Text>
          <Text style={styles.value}>+${breakdown.surgeAmount}</Text>
        </View>
      )}
      {breakdown.promoDiscount > 0 && (
        <View style={styles.row}><Text style={styles.label}>{t('common.discount')}</Text><Text style={styles.value}>-${breakdown.promoDiscount}</Text></View>
      )}
      <View style={styles.row}>
        <Text style={styles.total}>{t('common.total')}</Text>
        <Text style={styles.total}>${breakdown.total}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { marginVertical: 6, gap: 4 },
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  label: { color: colors.textMuted, fontSize: 13 },
  value: { color: colors.textMuted, fontSize: 13 },
  total: { color: colors.primary, fontWeight: '700', fontSize: 14 },
});
