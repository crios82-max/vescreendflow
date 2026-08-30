import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';

const router = Router();

router.use(authMiddleware);

const schema = z.object({
  token: z.string().min(10),
  platform: z.string().optional(),
});

router.post('/register', async (req, res) => {
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.flatten() });
  }
  const { token, platform } = parsed.data;
  await pool.query(
    `INSERT INTO push_tokens (user_id, token, platform)
     VALUES ($1, $2, $3)
     ON CONFLICT (user_id, token) DO UPDATE SET platform = EXCLUDED.platform`,
    [req.auth!.userId, token, platform ?? null],
  );
  res.json({ ok: true });
});

export default router;
