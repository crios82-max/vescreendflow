import { Router } from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide } from '../mappers.js';

const router = Router();
router.use(authMiddleware);

// Llamada enmascarada: con Twilio usa número relay; sin Twilio expone tel del otro (solo viaje activo)
router.get('/rides/:id', async (req, res) => {
  const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
  if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

  const ride = mapRide(rideResult.rows[0]);
  const { userId } = req.auth!;
  const isPassenger = ride.passengerId === userId;
  const isDriver = ride.driverId === userId;
  if (!isPassenger && !isDriver) return res.status(403).json({ error: 'Acceso denegado' });

  const active = ['accepted', 'arriving', 'in_progress'].includes(ride.status);
  if (!active) return res.status(400).json({ error: 'Solo durante viaje activo' });

  const otherId = isPassenger ? ride.driverId : ride.passengerId;
  if (!otherId) return res.status(400).json({ error: 'Sin contraparte asignada' });

  const other = await pool.query('SELECT name, phone FROM users WHERE id = $1', [otherId]);
  const phone = other.rows[0]?.phone as string | null;
  const relay = process.env.TWILIO_PHONE_NUMBER;

  if (relay && process.env.TWILIO_ACCOUNT_SID) {
    return res.json({
      name: other.rows[0].name,
      masked: true,
      dialNumber: relay,
      hint: 'Llama al número relay; identifícate con el ID del viaje',
      rideId: ride.id,
    });
  }

  if (!phone) {
    return res.json({
      name: other.rows[0].name,
      masked: false,
      dialNumber: null,
      hint: 'La contraparte no tiene teléfono registrado',
    });
  }

  res.json({
    name: other.rows[0].name,
    masked: false,
    dialNumber: phone,
    dialUrl: `tel:${phone}`,
  });
});

export default router;
