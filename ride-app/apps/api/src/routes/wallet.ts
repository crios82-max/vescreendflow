import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { getWalletBalance } from '../services/wallet.js';

const router = Router();
router.use(authMiddleware);

router.get('/balance', async (req, res) => {
  const balance = await getWalletBalance(req.auth!.userId);
  res.json({ balance });
});

router.get('/transactions', async (req, res) => {
  const result = await pool.query(
    'SELECT * FROM wallet_transactions WHERE user_id = $1 ORDER BY created_at DESC LIMIT 50',
    [req.auth!.userId],
  );
  res.json({
    transactions: result.rows.map((r) => ({
      id: r.id,
      amount: Number(r.amount),
      type: r.type,
      description: r.description,
      rideId: r.ride_id,
      createdAt: (r.created_at as Date).toISOString(),
    })),
  });
});

const topupSchema = z.object({ amount: z.number().min(1).max(500) });

router.post('/topup', async (req, res) => {
  const parsed = topupSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  await pool.query(
    `UPDATE users SET wallet_balance = wallet_balance + $1 WHERE id = $2`,
    [parsed.data.amount, req.auth!.userId],
  );
  await pool.query(
    `INSERT INTO wallet_transactions (user_id, amount, type, description) VALUES ($1, $2, 'credit', 'Wallet top-up')`,
    [req.auth!.userId, parsed.data.amount],
  );
  const balance = await getWalletBalance(req.auth!.userId);
  res.json({ balance });
});

export default router;
