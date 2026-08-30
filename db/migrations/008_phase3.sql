-- Phase 3: masked call sessions (Twilio Voice)

CREATE TABLE IF NOT EXISTS call_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ride_id UUID NOT NULL REFERENCES rides(id) ON DELETE CASCADE,
  caller_id UUID NOT NULL REFERENCES users(id),
  callee_phone TEXT NOT NULL,
  twilio_call_sid TEXT,
  status TEXT NOT NULL DEFAULT 'initiated',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_call_sessions_ride ON call_sessions(ride_id);
