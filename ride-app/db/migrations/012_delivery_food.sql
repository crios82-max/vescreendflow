-- Food delivery service mode (moto / bicicleta couriers)
DO $$ BEGIN
  CREATE TYPE service_mode AS ENUM ('ride', 'delivery');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE rides
  ADD COLUMN IF NOT EXISTS service_mode service_mode NOT NULL DEFAULT 'ride';

ALTER TABLE rides
  ADD COLUMN IF NOT EXISTS delivery_notes TEXT;

UPDATE rides
SET service_mode = 'delivery'
WHERE vehicle_type IN ('moto', 'bicicleta')
  AND service_mode = 'ride';

CREATE INDEX IF NOT EXISTS idx_rides_service_mode ON rides(service_mode);
