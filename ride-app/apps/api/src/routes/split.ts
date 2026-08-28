import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide } from '../mappers.js';

const router = Router();
router.use(authMiddleware);

router.get('/ride/:rideId/participants', async (req, res) => {
  const ride = await pool.query('SELECT passenger_id FROM rides WHERE id = $1', [req.params.rideId]);
  if (ride.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });
  if (ride.rows[0].passenger_id !== req.auth!.userId) {
    return res.status(403).json({ error: 'Solo el pasajero principal' });
  }

  const result = await pool.query(
    'SELECT * FROM ride_split_participants WHERE ride_id = $1 ORDER BY email',
    [req.params.rideId],
  );
  res.json({
    participants: result.rows.map((r) => ({
      id: r.id,
      email: r.email,
      amount: Number(r.amount),
      status: r.status,
    })),
  });
});

router.post('/ride/:rideId', async (req, res) => {
  const schema = z.object({ emails: z.array(z.string().email()).min(1).max(4) });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.rideId]);
  if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

  const ride = mapRide(rideResult.rows[0]);
  if (ride.passengerId !== req.auth!.userId) return res.status(403).json({ error: 'Solo el pasajero principal' });
  if (ride.status !== 'completed' || ride.paymentStatus === 'paid') {
    return res.status(400).json({ error: 'Solo viajes completados sin pagar' });
  }

  const total = ride.finalPrice ?? ride.estimatedPrice;
  const shares = parsed.data.emails.length + 1;
  const perPerson = Math.round((total / shares) * 100) / 100;

  await pool.query('DELETE FROM ride_split_participants WHERE ride_id = $1', [ride.id]);
  for (const email of parsed.data.emails) {
    await pool.query(
      `INSERT INTO ride_split_participants (ride_id, email, amount, status) VALUES ($1, $2, $3, 'pending')`,
      [ride.id, email, perPerson],
    );
  }

  res.json({ perPerson, participants: parsed.data.emails.length, yourShare: perPerson });
});

export default router;
