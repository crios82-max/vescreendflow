-- Phase 2: split fare invites, Stripe customers, saved cards

ALTER TABLE users ADD COLUMN IF NOT EXISTS stripe_customer_id TEXT;

ALTER TABLE rides ADD COLUMN IF NOT EXISTS split_mode BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE rides ADD COLUMN IF NOT EXISTS split_share_amount NUMERIC(10,2);
ALTER TABLE rides ADD COLUMN IF NOT EXISTS organizer_split_paid BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ride_split_participants ADD COLUMN IF NOT EXISTS invite_token TEXT UNIQUE;
ALTER TABLE ride_split_participants ADD COLUMN IF NOT EXISTS stripe_payment_intent_id TEXT;
ALTER TABLE ride_split_participants ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_split_invite_token ON ride_split_participants(invite_token);
