-- Optional restaurant pickup id for food delivery rides
ALTER TABLE rides
  ADD COLUMN IF NOT EXISTS restaurant_id TEXT;

CREATE INDEX IF NOT EXISTS idx_rides_restaurant_id ON rides(restaurant_id)
  WHERE restaurant_id IS NOT NULL;
