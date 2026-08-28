import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';

const router = Router();
router.use(authMiddleware);

router.get('/:rideId', async (req, res) => {
  const ride = await pool.query('SELECT passenger_id, driver_id FROM rides WHERE id = $1', [req.params.rideId]);
  if (ride.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
  const { passenger_id, driver_id } = ride.rows[0];
  const userId = req.auth!.userId;
  if (userId !== passenger_id && userId !== driver_id) {
    return res.status(403).json({ error: 'Acceso denegado' });
  }

  const result = await pool.query(
    `SELECT m.*, u.name AS sender_name FROM ride_messages m
     JOIN users u ON u.id = m.sender_id
     WHERE m.ride_id = $1 ORDER BY m.created_at ASC`,
    [req.params.rideId],
  );
  res.json({
    messages: result.rows.map((r) => ({
      id: r.id,
      rideId: r.ride_id,
      senderId: r.sender_id,
      senderName: r.sender_name,
      message: r.message,
      createdAt: (r.created_at as Date).toISOString(),
    })),
  });
});

router.post('/:rideId', async (req, res) => {
  const schema = z.object({ message: z.string().min(1).max(500) });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const ride = await pool.query('SELECT passenger_id, driver_id FROM rides WHERE id = $1', [req.params.rideId]);
  if (ride.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
  const { passenger_id, driver_id } = ride.rows[0];
  const userId = req.auth!.userId;
  if (userId !== passenger_id && userId !== driver_id) {
    return res.status(403).json({ error: 'Acceso denegado' });
  }

  const result = await pool.query(
    `INSERT INTO ride_messages (ride_id, sender_id, message) VALUES ($1, $2, $3) RETURNING *`,
    [req.params.rideId, userId, parsed.data.message],
  );
  const r = result.rows[0];
  res.status(201).json({
    message: {
      id: r.id,
      rideId: r.ride_id,
      senderId: r.sender_id,
      message: r.message,
      createdAt: (r.created_at as Date).toISOString(),
    },
  });
});

export default router;
