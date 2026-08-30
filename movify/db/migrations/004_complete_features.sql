-- Complete Uber-like features migration

ALTER TYPE ride_status ADD VALUE IF NOT EXISTS 'scheduled';

ALTER TABLE users ADD COLUMN IF NOT EXISTS wallet_balance NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS banned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS approval_status TEXT NOT NULL DEFAULT 'approved';
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS license_url TEXT;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS id_url TEXT;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS vehicle_photo_url TEXT;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS stripe_connect_account_id TEXT;
ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS total_earnings NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE rides ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMPTZ;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS surge_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.0;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS fare_breakdown JSONB;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS tip_amount NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS promo_code TEXT;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS promo_discount NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS cancellation_fee NUMERIC(10,2);
ALTER TABLE rides ADD COLUMN IF NOT EXISTS cancelled_by UUID REFERENCES users(id);
ALTER TABLE rides ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS share_token TEXT UNIQUE;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS receipt_sent_at TIMESTAMPTZ;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS ride_for_name TEXT;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS ride_for_phone TEXT;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS eta_pickup_min INTEGER;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS eta_dropoff_min INTEGER;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS tip_amount NUMERIC(10,2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS saved_places (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label TEXT NOT NULL,
  name TEXT NOT NULL,
  address TEXT NOT NULL,
  lat DOUBLE PRECISION NOT NULL,
  lng DOUBLE PRECISION NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ride_stops (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
  stop_order INTEGER NOT NULL,
  address TEXT NOT NULL,
  lat DOUBLE PRECISION NOT NULL,
  lng DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS promo_codes (
  code TEXT PRIMARY KEY,
  discount_type TEXT NOT NULL CHECK (discount_type IN ('percent', 'fixed')),
  discount_value NUMERIC(10,2) NOT NULL,
  max_uses INTEGER,
  uses_count INTEGER NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  amount NUMERIC(10,2) NOT NULL,
  type TEXT NOT NULL,
  description TEXT,
  ride_id UUID REFERENCES rides(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ride_messages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
  sender_id UUID NOT NULL REFERENCES users(id),
  message TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sos_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id),
  lat DOUBLE PRECISION,
  lng DOUBLE PRECISION,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ride_split_participants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
  email TEXT NOT NULL,
  amount NUMERIC(10,2) NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending'
);

CREATE INDEX IF NOT EXISTS idx_saved_places_user ON saved_places(user_id);
CREATE INDEX IF NOT EXISTS idx_ride_stops_ride ON ride_stops(ride_id);
CREATE INDEX IF NOT EXISTS idx_ride_messages_ride ON ride_messages(ride_id);
CREATE INDEX IF NOT EXISTS idx_sos_ride ON sos_events(ride_id);
CREATE INDEX IF NOT EXISTS idx_rides_scheduled ON rides(scheduled_at) WHERE status = 'scheduled';
CREATE INDEX IF NOT EXISTS idx_rides_share_token ON rides(share_token);

INSERT INTO promo_codes (code, discount_type, discount_value, max_uses) VALUES
  ('BIENVENIDA', 'percent', 15, 1000),
  ('RIDE5', 'fixed', 5, 500)
ON CONFLICT (code) DO NOTHING;
