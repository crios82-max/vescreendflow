CREATE TABLE IF NOT EXISTS phone_otps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  phone TEXT NOT NULL,
  code TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_phone_otps_phone ON phone_otps(phone);
CREATE INDEX IF NOT EXISTS idx_phone_otps_user ON phone_otps(user_id);

ALTER TABLE driver_profiles ADD COLUMN IF NOT EXISTS stripe_connect_onboarded BOOLEAN NOT NULL DEFAULT FALSE;
