import { Router } from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide } from '../mappers.js';
import { initiateMaskedCall, isTwilioVoiceEnabled } from '../services/maskedCall.js';
import { sendError } from '../httpError.js';

const router = Router();
router.use(authMiddleware);

async function resolveRideContact(rideId: string, userId: string) {
  const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [rideId]);
  if (rideResult.rows.length === 0) return { error: 'Viaje no encontrado', errorCode: 'RIDE_NOT_FOUND', status: 404 as const };

  const ride = mapRide(rideResult.rows[0]);
  const isPassenger = ride.passengerId === userId;
  const isDriver = ride.driverId === userId;
  if (!isPassenger && !isDriver) return { error: 'Acceso denegado', errorCode: 'ACCESS_DENIED', status: 403 as const };

  const active = ['accepted', 'arriving', 'in_progress'].includes(ride.status);
  if (!active) return { error: 'Solo durante viaje activo', errorCode: 'ACTIVE_RIDE_ONLY', status: 400 as const };

  const otherId = isPassenger ? ride.driverId : ride.passengerId;
  if (!otherId) return { error: 'Sin contraparte asignada', errorCode: 'NO_COUNTERPART', status: 400 as const };

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
  if ('error' in ctx) return sendError(res, ctx.status ?? 500, ctx.error!, ctx.errorCode ?? 'NETWORK_ERROR');

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
  if ('error' in ctx) return sendError(res, ctx.status ?? 500, ctx.error!, ctx.errorCode ?? 'NETWORK_ERROR');

  if (!ctx.callerVerified || !ctx.callerPhone) {
    return sendError(res, 400, 'Verifica tu teléfono antes de llamar', 'VERIFY_PHONE_CALL');
  }
  if (!ctx.otherPhone) {
    return sendError(res, 400, 'La contraparte no tiene teléfono', 'NO_PHONE');
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
    sendError(res, 502, err instanceof Error ? err.message : 'Error al iniciar llamada', 'CALL_FAILED');
  }
});

export default router;
