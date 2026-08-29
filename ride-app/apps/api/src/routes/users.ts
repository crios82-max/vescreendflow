import { Router } from 'express';
import { z } from 'zod';
import { isLocale } from '@ride-app/shared';
import { pool } from '../db.js';
import { authMiddleware } from '../middleware/auth.js';
import { mapUser, mapDriverProfile } from '../mappers.js';
import { sendError } from '../httpError.js';

const router = Router();

router.get('/me', authMiddleware, async (req, res) => {
  const result = await pool.query('SELECT * FROM users WHERE id = $1', [req.auth!.userId]);
  if (result.rows.length === 0) {
    return sendError(res, 404, 'Usuario no encontrado', 'USER_NOT_FOUND');
  }

  const user = mapUser(result.rows[0]);
  if (user.role === 'driver') {
    const profileResult = await pool.query(
      'SELECT * FROM driver_profiles WHERE user_id = $1',
      [user.id],
    );
    if (profileResult.rows.length > 0) {
      return res.json({ user, profile: mapDriverProfile(profileResult.rows[0]) });
    }
  }

  res.json({ user });
});

router.patch('/me/locale', authMiddleware, async (req, res) => {
  const schema = z.object({ locale: z.string() });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });
  if (!isLocale(parsed.data.locale)) return sendError(res, 400, 'Idioma no soportado', 'UNSUPPORTED_LOCALE');

  await pool.query('UPDATE users SET preferred_locale = $1 WHERE id = $2', [
    parsed.data.locale,
    req.auth!.userId,
  ]);
  res.json({ ok: true, locale: parsed.data.locale });
});

export default router;
