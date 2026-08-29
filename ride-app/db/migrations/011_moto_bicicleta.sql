-- Add motorcycle and bicycle vehicle modes
ALTER TYPE vehicle_type ADD VALUE IF NOT EXISTS 'moto';
ALTER TYPE vehicle_type ADD VALUE IF NOT EXISTS 'bicicleta';
