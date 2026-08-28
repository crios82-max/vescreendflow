export type UserRole = 'passenger' | 'driver';

export type RideStatus =
  | 'requested'
  | 'accepted'
  | 'arriving'
  | 'in_progress'
  | 'completed'
  | 'cancelled';

export interface User {
  id: string;
  email: string;
  name: string;
  phone: string | null;
  role: UserRole;
}

export interface DriverProfile {
  userId: string;
  isOnline: boolean;
  vehicleMake: string | null;
  vehicleModel: string | null;
  vehiclePlate: string | null;
  rating: number;
  lat: number | null;
  lng: number | null;
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
  estimatedPrice: number;
  finalPrice: number | null;
  paymentStatus: PaymentStatus;
  distanceKm: number;
  durationMin: number;
  createdAt: string;
  acceptedAt: string | null;
  completedAt: string | null;
  passenger?: Pick<User, 'id' | 'name' | 'phone'>;
  driver?: Pick<User, 'id' | 'name' | 'phone'> & { profile?: DriverProfile };
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
  requested: 'Buscando conductor',
  accepted: 'Conductor asignado',
  arriving: 'Conductor en camino',
  in_progress: 'En viaje',
  completed: 'Completado',
  cancelled: 'Cancelado',
};

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
): number {
  return Math.round((baseFare + distanceKm * pricePerKm + durationMin * pricePerMin) * 100) / 100;
}
