import type { FareBreakdown } from '@ride-app/shared';

export function FareBreakdownView({ breakdown, surgeMultiplier }: { breakdown: FareBreakdown | null; surgeMultiplier?: number }) {
  if (!breakdown) return null;
  return (
    <div className="fare-breakdown">
      <div className="meta-row"><span>Tarifa base</span><span>${breakdown.baseFare}</span></div>
      <div className="meta-row"><span>Distancia</span><span>${breakdown.distanceFare}</span></div>
      <div className="meta-row"><span>Tiempo</span><span>${breakdown.timeFare}</span></div>
      {(surgeMultiplier ?? breakdown.surgeMultiplier) > 1 && (
        <div className="meta-row"><span>Surge {breakdown.surgeMultiplier}x</span><span>+${breakdown.surgeAmount}</span></div>
      )}
      {breakdown.promoDiscount > 0 && (
        <div className="meta-row"><span>Descuento</span><span>-${breakdown.promoDiscount}</span></div>
      )}
      <div className="meta-row"><strong>Total</strong><strong>${breakdown.total}</strong></div>
    </div>
  );
}
