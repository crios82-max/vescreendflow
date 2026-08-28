import type { Ride, User, DriverProfile } from '@ride-app/shared';

export function mapUser(row: Record<string, unknown>): User {
  return {
    id: row.id as string,
    email: row.email as string,
    name: row.name as string,
    phone: (row.phone as string) ?? null,
    role: row.role as User['role'],
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
  };
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
    estimatedPrice: Number(row.estimated_price),
    finalPrice: row.final_price != null ? Number(row.final_price) : null,
    paymentStatus: (row.payment_status as Ride['paymentStatus']) ?? 'pending',
    distanceKm: Number(row.distance_km),
    durationMin: Number(row.duration_min),
    routePolyline: (row.route_polyline as string) ?? null,
    createdAt: (row.created_at as Date).toISOString(),
    acceptedAt: row.accepted_at ? (row.accepted_at as Date).toISOString() : null,
    completedAt: row.completed_at ? (row.completed_at as Date).toISOString() : null,
  };
}
