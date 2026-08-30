import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';

const router = Router();
router.use(authMiddleware);

const schema = z.object({
  label: z.string().min(1),
  name: z.string().min(1),
  address: z.string().min(3),
  lat: z.number(),
  lng: z.number(),
});

router.get('/', async (req, res) => {
  const result = await pool.query(
    'SELECT * FROM saved_places WHERE user_id = $1 ORDER BY created_at DESC',
    [req.auth!.userId],
  );
  res.json({
    places: result.rows.map((r) => ({
      id: r.id,
      label: r.label,
      name: r.name,
      address: r.address,
      lat: Number(r.lat),
      lng: Number(r.lng),
    })),
  });
});

router.post('/', async (req, res) => {
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const result = await pool.query(
    `INSERT INTO saved_places (user_id, label, name, address, lat, lng)
     VALUES ($1, $2, $3, $4, $5, $6) RETURNING *`,
    [req.auth!.userId, parsed.data.label, parsed.data.name, parsed.data.address, parsed.data.lat, parsed.data.lng],
  );
  const r = result.rows[0];
  res.status(201).json({
    place: { id: r.id, label: r.label, name: r.name, address: r.address, lat: Number(r.lat), lng: Number(r.lng) },
  });
});

router.delete('/:id', async (req, res) => {
  await pool.query('DELETE FROM saved_places WHERE id = $1 AND user_id = $2', [req.params.id, req.auth!.userId]);
  res.json({ ok: true });
});

export default router;
