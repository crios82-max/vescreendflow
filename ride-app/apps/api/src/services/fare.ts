import type { FareBreakdown, VehicleType } from '@ride-app/shared';
import { VEHICLE_OPTIONS } from '@ride-app/shared';

export function buildFareBreakdown(
  distanceKm: number,
  durationMin: number,
  vehicleType: VehicleType,
  surgeMultiplier: number,
  promoDiscount: number,
  baseFare: number,
  pricePerKm: number,
  pricePerMin: number,
): FareBreakdown {
  const vehicleMultiplier = VEHICLE_OPTIONS[vehicleType].multiplier;
  const distanceFare = Math.round(distanceKm * pricePerKm * 100) / 100;
  const timeFare = Math.round(durationMin * pricePerMin * 100) / 100;
  const subtotal = Math.round((baseFare + distanceFare + timeFare) * vehicleMultiplier * 100) / 100;
  const surgeAmount = surgeMultiplier > 1
    ? Math.round(subtotal * (surgeMultiplier - 1) * 100) / 100
    : 0;
  const totalBeforeDiscount = Math.round((subtotal + surgeAmount) * 100) / 100;
  const discount = Math.min(promoDiscount, totalBeforeDiscount);
  const total = Math.round((totalBeforeDiscount - discount) * 100) / 100;

  return {
    baseFare,
    distanceFare,
    timeFare,
    vehicleMultiplier,
    surgeMultiplier,
    surgeAmount,
    promoDiscount: discount,
    total,
  };
}
