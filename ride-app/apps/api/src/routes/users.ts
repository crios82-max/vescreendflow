import { Router } from 'express';
import { pool } from '../db.js';
import { authMiddleware } from '../middleware/auth.js';
import { mapUser, mapDriverProfile } from '../mappers.js';

const router = Router();

router.get('/me', authMiddleware, async (req, res) => {
  const result = await pool.query('SELECT * FROM users WHERE id = $1', [req.auth!.userId]);
  if (result.rows.length === 0) {
    return res.status(404).json({ error: 'Usuario no encontrado' });
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

export default router;
