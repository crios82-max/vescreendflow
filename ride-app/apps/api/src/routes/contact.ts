import { Router } from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide } from '../mappers.js';
import { initiateMaskedCall, isTwilioVoiceEnabled } from '../services/maskedCall.js';

const router = Router();
router.use(authMiddleware);

async function resolveRideContact(rideId: string, userId: string) {
  const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [rideId]);
  if (rideResult.rows.length === 0) return { error: 'Viaje no encontrado', status: 404 as const };

  const ride = mapRide(rideResult.rows[0]);
  const isPassenger = ride.passengerId === userId;
  const isDriver = ride.driverId === userId;
  if (!isPassenger && !isDriver) return { error: 'Acceso denegado', status: 403 as const };

  const active = ['accepted', 'arriving', 'in_progress'].includes(ride.status);
  if (!active) return { error: 'Solo durante viaje activo', status: 400 as const };

  const otherId = isPassenger ? ride.driverId : ride.passengerId;
  if (!otherId) return { error: 'Sin contraparte asignada', status: 400 as const };

  const [other, caller] = await Promise.all([
    pool.query('SELECT name, phone FROM users WHERE id = $1', [otherId]),
    pool.query('SELECT phone, phone_verified FROM users WHERE id = $1', [userId]),
  ]);

  return {
    ride,
    otherName: other.rows[0].name as string,
    otherPhone: other.rows[0].phone as string | null,
    callerPhone: caller.rows[0].phone as string | null,
    callerVerified: caller.rows[0].phone_verified as boolean,
  };
}

router.get('/rides/:id', async (req, res) => {
  const ctx = await resolveRideContact(req.params.id, req.auth!.userId);
  if ('error' in ctx) return res.status(ctx.status ?? 500).json({ error: ctx.error });

  const twilio = isTwilioVoiceEnabled();
  if (twilio && ctx.callerVerified && ctx.callerPhone && ctx.otherPhone) {
    return res.json({
      name: ctx.otherName,
      mode: 'twilio',
      masked: true,
      hint: 'Usa el botón Llamar — te conectamos sin mostrar números',
    });
  }

  if (!ctx.otherPhone) {
    return res.json({
      name: ctx.otherName,
      mode: 'unavailable',
      masked: false,
      hint: 'La contraparte no tiene teléfono registrado',
    });
  }

  res.json({
    name: ctx.otherName,
    mode: 'direct',
    masked: false,
    dialNumber: ctx.otherPhone,
    dialUrl: `tel:${ctx.otherPhone}`,
  });
});

router.post('/rides/:id/call', async (req, res) => {
  const ctx = await resolveRideContact(req.params.id, req.auth!.userId);
  if ('error' in ctx) return res.status(ctx.status ?? 500).json({ error: ctx.error });

  if (!ctx.callerVerified || !ctx.callerPhone) {
    return res.status(400).json({ error: 'Verifica tu teléfono antes de llamar' });
  }
  if (!ctx.otherPhone) {
    return res.status(400).json({ error: 'La contraparte no tiene teléfono' });
  }

  try {
    const result = await initiateMaskedCall({
      rideId: ctx.ride.id,
      callerUserId: req.auth!.userId,
      callerPhone: ctx.callerPhone,
      calleePhone: ctx.otherPhone,
    });
    res.json(result);
  } catch (err) {
    res.status(502).json({ error: err instanceof Error ? err.message : 'Error al iniciar llamada' });
  }
});

export default router;
