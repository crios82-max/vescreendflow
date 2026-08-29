export type UserRole = 'passenger' | 'driver';

export type RideStatus =
  | 'scheduled'
  | 'requested'
  | 'accepted'
  | 'arriving'
  | 'in_progress'
  | 'completed'
  | 'cancelled';

export type DriverApprovalStatus = 'pending' | 'approved' | 'rejected';

export interface FareBreakdown {
  baseFare: number;
  distanceFare: number;
  timeFare: number;
  vehicleMultiplier: number;
  surgeMultiplier: number;
  surgeAmount: number;
  promoDiscount: number;
  total: number;
}

export interface SavedPlace {
  id: string;
  label: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
}

export interface RideMessage {
  id: string;
  rideId: string;
  senderId: string;
  senderName?: string;
  message: string;
  createdAt: string;
}

export interface User {
  id: string;
  email: string;
  name: string;
  phone: string | null;
  role: UserRole;
  walletBalance?: number;
  isAdmin?: boolean;
}

export type VehicleType = 'standard' | 'comfort' | 'xl' | 'vans';

export const VEHICLE_TYPES: VehicleType[] = ['standard', 'comfort', 'xl', 'vans'];

export interface VehicleOption {
  type: VehicleType;
  label: string;
  description: string;
  seats: number;
  multiplier: number;
  icon: string;
}

export const VEHICLE_OPTIONS: Record<VehicleType, VehicleOption> = {
  standard: {
    type: 'standard',
    label: 'Standard',
    description: 'Viajes económicos',
    seats: 4,
    multiplier: 1,
    icon: '🚗',
  },
  comfort: {
    type: 'comfort',
    label: 'Confort',
    description: 'Autos más amplios',
    seats: 4,
    multiplier: 1.35,
    icon: '✨',
  },
  xl: {
    type: 'xl',
    label: 'XL',
    description: 'Hasta 6 pasajeros',
    seats: 6,
    multiplier: 1.55,
    icon: '🚙',
  },
  vans: {
    type: 'vans',
    label: 'Vans',
    description: 'Grupos y equipaje',
    seats: 8,
    multiplier: 1.85,
    icon: '🚐',
  },
};

export interface RideEstimateOption {
  vehicleType: VehicleType;
  label: string;
  description: string;
  seats: number;
  icon: string;
  estimatedPrice: number;
}

export interface RideEstimate {
  distanceKm: number;
  durationMin: number;
  options: RideEstimateOption[];
  polyline: string | null;
  surgeMultiplier: number;
}

export interface DriverProfile {
  userId: string;
  isOnline: boolean;
  vehicleMake: string | null;
  vehicleModel: string | null;
  vehiclePlate: string | null;
  vehicleType: VehicleType;
  rating: number;
  lat: number | null;
  lng: number | null;
  approvalStatus: DriverApprovalStatus;
  totalEarnings: number;
}

export type PaymentStatus = 'pending' | 'paid' | 'failed';

export interface Payment {
  id: string;
  rideId: string;
  amount: number;
  method: string;
  cardLast4: string;
  status: PaymentStatus;
  createdAt: string;
}

export interface Ride {
  id: string;
  passengerId: string;
  driverId: string | null;
  status: RideStatus;
  pickupAddress: string;
  pickupLat: number;
  pickupLng: number;
  dropoffAddress: string;
  dropoffLat: number;
  dropoffLng: number;
  vehicleType: VehicleType;
  estimatedPrice: number;
  finalPrice: number | null;
  paymentStatus: PaymentStatus;
  distanceKm: number;
  durationMin: number;
  routePolyline: string | null;
  scheduledAt: string | null;
  surgeMultiplier: number;
  fareBreakdown: FareBreakdown | null;
  tipAmount: number;
  promoCode: string | null;
  promoDiscount: number;
  cancellationFee: number | null;
  shareToken: string | null;
  rideForName: string | null;
  rideForPhone: string | null;
  etaPickupMin: number | null;
  etaDropoffMin: number | null;
  createdAt: string;
  acceptedAt: string | null;
  completedAt: string | null;
  passenger?: Pick<User, 'id' | 'name' | 'phone'>;
  driver?: Pick<User, 'id' | 'name' | 'phone'> & { profile?: DriverProfile };
}

export interface Rating {
  id: string;
  rideId: string;
  raterId: string;
  rateeId: string;
  stars: number;
  comment: string | null;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface LocationUpdate {
  rideId: string;
  lat: number;
  lng: number;
  heading?: number;
}

export const RIDE_STATUS_LABELS: Record<RideStatus, string> = {
  scheduled: 'Programado',
  requested: 'Buscando conductor',
  accepted: 'Conductor asignado',
  arriving: 'Conductor en camino',
  in_progress: 'En viaje',
  completed: 'Completado',
  cancelled: 'Cancelado',
};

export function vehicleTypeLabel(type: VehicleType): string {
  return VEHICLE_OPTIONS[type].label;
}

export function haversineKm(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function estimateFare(
  distanceKm: number,
  durationMin: number,
  baseFare = 2.5,
  pricePerKm = 1.2,
  pricePerMin = 0.25,
  multiplier = 1,
): number {
  const base = baseFare + distanceKm * pricePerKm + durationMin * pricePerMin;
  return Math.round(base * multiplier * 100) / 100;
}

export function buildRideEstimate(
  distanceKm: number,
  durationMin: number,
  baseFare = 2.5,
  pricePerKm = 1.2,
  pricePerMin = 0.25,
  polyline: string | null = null,
  surgeMultiplier = 1,
): RideEstimate {
  const roundedDistance = Math.round(distanceKm * 100) / 100;
  return {
    distanceKm: roundedDistance,
    durationMin,
    polyline,
    surgeMultiplier,
    options: VEHICLE_TYPES.map((type) => {
      const option = VEHICLE_OPTIONS[type];
      const base = estimateFare(
        roundedDistance,
        durationMin,
        baseFare,
        pricePerKm,
        pricePerMin,
        option.multiplier,
      );
      return {
        vehicleType: type,
        label: option.label,
        description: option.description,
        seats: option.seats,
        icon: option.icon,
        estimatedPrice: Math.round(base * surgeMultiplier * 100) / 100,
      };
    }),
  };
}

export { BRAND, brandTitle, brandAppLabel } from './brand.js';
export {
  type Locale,
  type TranslationKey,
  type TranslationParams,
  LOCALES,
  SUPPORTED_LOCALES,
  LOCALE_LABELS,
  LOCALE_META,
  LOCALE_DETECT_ORDER,
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
  LOCALE_STORAGE_KEY,
  LOCALE_MANUAL_KEY,
  translate,
  detectLocale,
  detectLocaleFromBrowser,
  detectLocaleFromLanguage,
  detectLocaleFromLanguageTag,
  resolveLocaleFromPlace,
  getManualLocale,
  hasManualLocale,
  getStoredLocale,
  setStoredLocale,
  isLocale,
  localeFromCountry,
  localeFromTimezone,
  COUNTRY_TO_LOCALE,
  rideStatusLabel,
  vehicleLabel,
  vehicleDescription,
  brandTagline,
  brandRoleLabel,
  translateApiError,
} from './i18n/index.js';
