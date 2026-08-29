CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TYPE user_role AS ENUM ('passenger', 'driver');
CREATE TYPE ride_status AS ENUM (
  'requested',
  'accepted',
  'arriving',
  'in_progress',
  'completed',
  'cancelled'
);
CREATE TYPE vehicle_type AS ENUM ('standard', 'moto', 'bicicleta', 'comfort', 'xl', 'vans');
CREATE TYPE payment_status AS ENUM ('pending', 'paid', 'failed');

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  phone TEXT,
  role user_role NOT NULL,
  is_admin BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE driver_profiles (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  is_online BOOLEAN NOT NULL DEFAULT FALSE,
  vehicle_make TEXT,
  vehicle_model TEXT,
  vehicle_plate TEXT,
  vehicle_type vehicle_type NOT NULL DEFAULT 'standard',
  rating NUMERIC(3,2) NOT NULL DEFAULT 5.00,
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE rides (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  passenger_id UUID NOT NULL REFERENCES users(id),
  driver_id UUID REFERENCES users(id),
  status ride_status NOT NULL DEFAULT 'requested',
  pickup_address TEXT NOT NULL,
  pickup_lat DOUBLE PRECISION NOT NULL,
  pickup_lng DOUBLE PRECISION NOT NULL,
  dropoff_address TEXT NOT NULL,
  dropoff_lat DOUBLE PRECISION NOT NULL,
  dropoff_lng DOUBLE PRECISION NOT NULL,
  vehicle_type vehicle_type NOT NULL DEFAULT 'standard',
  estimated_price NUMERIC(10,2) NOT NULL,
  final_price NUMERIC(10,2),
  payment_status payment_status NOT NULL DEFAULT 'pending',
  distance_km NUMERIC(10,2) NOT NULL,
  duration_min NUMERIC(10,2) NOT NULL,
  route_polyline TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  accepted_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ
);

CREATE TABLE payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
  amount NUMERIC(10,2) NOT NULL,
  method TEXT NOT NULL DEFAULT 'mock_card',
  card_last4 TEXT NOT NULL DEFAULT '4242',
  stripe_payment_intent_id TEXT,
  status payment_status NOT NULL DEFAULT 'paid',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ratings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
  rater_id UUID NOT NULL REFERENCES users(id),
  ratee_id UUID NOT NULL REFERENCES users(id),
  stars INTEGER NOT NULL CHECK (stars >= 1 AND stars <= 5),
  comment TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (ride_id, rater_id)
);

CREATE TABLE push_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token TEXT NOT NULL,
  platform TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, token)
);

CREATE INDEX idx_rides_passenger ON rides(passenger_id);
CREATE INDEX idx_rides_driver ON rides(driver_id);
CREATE INDEX idx_rides_status ON rides(status);
CREATE INDEX idx_driver_online ON driver_profiles(is_online) WHERE is_online = TRUE;
CREATE INDEX idx_rides_vehicle_type ON rides(vehicle_type);
CREATE INDEX idx_driver_vehicle_type ON driver_profiles(vehicle_type);
CREATE INDEX idx_payments_ride ON payments(ride_id);
CREATE INDEX idx_ratings_ride ON ratings(ride_id);
CREATE INDEX idx_push_tokens_user ON push_tokens(user_id);
