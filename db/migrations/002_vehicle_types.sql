-- Run on existing DBs: docker compose exec db psql -U ride -d ride_app -f /path/to/002_vehicle_types.sql

DO $$ BEGIN
  CREATE TYPE vehicle_type AS ENUM ('standard', 'comfort', 'xl', 'vans');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE driver_profiles
  ADD COLUMN IF NOT EXISTS vehicle_type vehicle_type NOT NULL DEFAULT 'standard';

ALTER TABLE rides
  ADD COLUMN IF NOT EXISTS vehicle_type vehicle_type NOT NULL DEFAULT 'standard';

CREATE INDEX IF NOT EXISTS idx_rides_vehicle_type ON rides(vehicle_type);
CREATE INDEX IF NOT EXISTS idx_driver_vehicle_type ON driver_profiles(vehicle_type);
