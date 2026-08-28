import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { sendPushToUser } from '../services/push.js';

const router = Router();
router.use(authMiddleware);

router.post('/', async (req, res) => {
  const schema = z.object({
    rideId: z.string().uuid(),
    lat: z.number().optional(),
    lng: z.number().optional(),
  });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const ride = await pool.query('SELECT passenger_id, driver_id FROM rides WHERE id = $1', [parsed.data.rideId]);
  if (ride.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

  const result = await pool.query(
    `INSERT INTO sos_events (ride_id, user_id, lat, lng) VALUES ($1, $2, $3, $4) RETURNING *`,
    [parsed.data.rideId, req.auth!.userId, parsed.data.lat ?? null, parsed.data.lng ?? null],
  );

  const admins = await pool.query('SELECT id FROM users WHERE is_admin = TRUE');
  for (const admin of admins.rows) {
    await sendPushToUser(
      admin.id as string,
      '🚨 SOS activado',
      `Emergencia en viaje ${parsed.data.rideId}`,
      { rideId: parsed.data.rideId, type: 'sos' },
    );
  }

  res.status(201).json({ ok: true, id: result.rows[0].id });
});

export default router;
