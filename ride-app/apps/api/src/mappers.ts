import type { FareBreakdown, Ride, User, DriverProfile } from '@ride-app/shared';

export function mapUser(row: Record<string, unknown>): User {
  return {
    id: row.id as string,
    email: row.email as string,
    name: row.name as string,
    phone: (row.phone as string) ?? null,
    role: row.role as User['role'],
    walletBalance: row.wallet_balance != null ? Number(row.wallet_balance) : undefined,
  };
}

export function mapDriverProfile(row: Record<string, unknown>): DriverProfile {
  return {
    userId: row.user_id as string,
    isOnline: row.is_online as boolean,
    vehicleMake: (row.vehicle_make as string) ?? null,
    vehicleModel: (row.vehicle_model as string) ?? null,
    vehiclePlate: (row.vehicle_plate as string) ?? null,
    vehicleType: (row.vehicle_type as DriverProfile['vehicleType']) ?? 'standard',
    rating: Number(row.rating),
    lat: row.lat != null ? Number(row.lat) : null,
    lng: row.lng != null ? Number(row.lng) : null,
    approvalStatus: (row.approval_status as DriverProfile['approvalStatus']) ?? 'approved',
    totalEarnings: Number(row.total_earnings ?? 0),
  };
}

function parseFareBreakdown(raw: unknown): FareBreakdown | null {
  if (!raw || typeof raw !== 'object') return null;
  return raw as FareBreakdown;
}

export function mapRide(row: Record<string, unknown>): Ride {
  return {
    id: row.id as string,
    passengerId: row.passenger_id as string,
    driverId: (row.driver_id as string) ?? null,
    status: row.status as Ride['status'],
    pickupAddress: row.pickup_address as string,
    pickupLat: Number(row.pickup_lat),
    pickupLng: Number(row.pickup_lng),
    dropoffAddress: row.dropoff_address as string,
    dropoffLat: Number(row.dropoff_lat),
    dropoffLng: Number(row.dropoff_lng),
    vehicleType: (row.vehicle_type as Ride['vehicleType']) ?? 'standard',
    serviceMode: (row.service_mode as Ride['serviceMode']) ?? 'ride',
    deliveryNotes: (row.delivery_notes as string) ?? null,
    restaurantId: (row.restaurant_id as string) ?? null,
    estimatedPrice: Number(row.estimated_price),
    finalPrice: row.final_price != null ? Number(row.final_price) : null,
    paymentStatus: (row.payment_status as Ride['paymentStatus']) ?? 'pending',
    distanceKm: Number(row.distance_km),
    durationMin: Number(row.duration_min),
    routePolyline: (row.route_polyline as string) ?? null,
    scheduledAt: row.scheduled_at ? (row.scheduled_at as Date).toISOString() : null,
    surgeMultiplier: Number(row.surge_multiplier ?? 1),
    fareBreakdown: parseFareBreakdown(row.fare_breakdown),
    tipAmount: Number(row.tip_amount ?? 0),
    promoCode: (row.promo_code as string) ?? null,
    promoDiscount: Number(row.promo_discount ?? 0),
    cancellationFee: row.cancellation_fee != null ? Number(row.cancellation_fee) : null,
    shareToken: (row.share_token as string) ?? null,
    rideForName: (row.ride_for_name as string) ?? null,
    rideForPhone: (row.ride_for_phone as string) ?? null,
    etaPickupMin: row.eta_pickup_min != null ? Number(row.eta_pickup_min) : null,
    etaDropoffMin: row.eta_dropoff_min != null ? Number(row.eta_dropoff_min) : null,
    createdAt: (row.created_at as Date).toISOString(),
    acceptedAt: row.accepted_at ? (row.accepted_at as Date).toISOString() : null,
    completedAt: row.completed_at ? (row.completed_at as Date).toISOString() : null,
  };
}
