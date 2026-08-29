import { Router } from 'express';
import { authMiddleware, requireRole } from '../middleware/auth.js';
import { pool } from '../db.js';
import { createConnectOnboardingLink, getConnectStatus } from '../services/stripeConnect.js';
import { sendError } from '../httpError.js';

const router = Router();
router.use(authMiddleware, requireRole('driver'));

router.get('/status', async (req, res) => {
  const status = await getConnectStatus(req.auth!.userId);
  res.json(status);
});

router.post('/onboard', async (req, res) => {
  const user = await pool.query('SELECT email FROM users WHERE id = $1', [req.auth!.userId]);
  if (user.rows.length === 0) return sendError(res, 404, 'Usuario no encontrado', 'USER_NOT_FOUND');

  const link = await createConnectOnboardingLink(req.auth!.userId, user.rows[0].email as string);
  if (!link) {
    return res.json({
      mock: true,
      message: 'Stripe no configurado — payouts simulados en DB',
      errorCode: 'STRIPE_NOT_CONFIGURED',
    });
  }
  res.json(link);
});

export default router;
