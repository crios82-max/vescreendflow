import { Router } from 'express';
import { pool } from '../db.js';
import { mapRide } from '../mappers.js';

const router = Router();

router.get('/:token', async (req, res) => {
  const result = await pool.query('SELECT * FROM rides WHERE share_token = $1', [req.params.token]);
  if (result.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

  const ride = mapRide(result.rows[0]);
  const driver = ride.driverId
    ? await pool.query('SELECT lat, lng FROM driver_profiles WHERE user_id = $1', [ride.driverId])
    : { rows: [] };

  res.json({
    ride: {
      id: ride.id,
      status: ride.status,
      pickupAddress: ride.pickupAddress,
      dropoffAddress: ride.dropoffAddress,
      etaPickupMin: ride.etaPickupMin,
      etaDropoffMin: ride.etaDropoffMin,
    },
    driverLocation: driver.rows[0]
      ? { lat: Number(driver.rows[0].lat), lng: Number(driver.rows[0].lng) }
      : null,
  });
});

export default router;
