import type { FareBreakdown } from '@ride-app/shared';
import { useI18n } from './I18nProvider';

export function FareBreakdownView({ breakdown, surgeMultiplier }: { breakdown: FareBreakdown | null; surgeMultiplier?: number }) {
  const { t } = useI18n();
  if (!breakdown) return null;
  return (
    <div className="fare-breakdown">
      <div className="meta-row"><span>{t('common.baseFare')}</span><span>${breakdown.baseFare}</span></div>
      <div className="meta-row"><span>{t('common.distance')}</span><span>${breakdown.distanceFare}</span></div>
      <div className="meta-row"><span>{t('common.time')}</span><span>${breakdown.timeFare}</span></div>
      {(surgeMultiplier ?? breakdown.surgeMultiplier) > 1 && (
        <div className="meta-row"><span>{t('common.surgeLine', { multiplier: breakdown.surgeMultiplier })}</span><span>+${breakdown.surgeAmount}</span></div>
      )}
      {breakdown.promoDiscount > 0 && (
        <div className="meta-row"><span>{t('common.discount')}</span><span>-${breakdown.promoDiscount}</span></div>
      )}
      <div className="meta-row"><strong>{t('common.total')}</strong><strong>${breakdown.total}</strong></div>
    </div>
  );
}
