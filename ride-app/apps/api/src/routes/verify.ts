import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { sendPhoneOtp, verifyPhoneOtp } from '../services/otp.js';
import { sendError } from '../httpError.js';

const router = Router();
router.use(authMiddleware);

router.get('/phone/status', async (req, res) => {
  const result = await pool.query('SELECT phone, phone_verified FROM users WHERE id = $1', [req.auth!.userId]);
  if (result.rows.length === 0) return sendError(res, 404, 'Usuario no encontrado', 'USER_NOT_FOUND');
  res.json({
    phone: result.rows[0].phone,
    verified: result.rows[0].phone_verified as boolean,
  });
});

router.post('/phone/send', async (req, res) => {
  const schema = z.object({ phone: z.string().min(8).max(20) });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const result = await sendPhoneOtp(req.auth!.userId, parsed.data.phone);
  res.json(result);
});

router.post('/phone/confirm', async (req, res) => {
  const schema = z.object({ phone: z.string().min(8), code: z.string().length(6) });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const ok = await verifyPhoneOtp(req.auth!.userId, parsed.data.phone, parsed.data.code);
  if (!ok) return sendError(res, 400, 'Código inválido o expirado', 'INVALID_CODE');
  res.json({ verified: true });
});

export default router;
