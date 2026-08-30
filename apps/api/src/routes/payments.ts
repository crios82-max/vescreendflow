import { Router } from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import {
  createSetupIntent,
  detachPaymentMethod,
  getOrCreateStripeCustomer,
  listPaymentMethods,
} from '../services/stripeCustomer.js';

const router = Router();
router.use(authMiddleware);

router.get('/methods', async (req, res) => {
  const user = await pool.query('SELECT email FROM users WHERE id = $1', [req.auth!.userId]);
  const customerId = await getOrCreateStripeCustomer(req.auth!.userId, user.rows[0].email);
  if (!customerId) return res.json({ methods: [], mock: true });

  const methods = await listPaymentMethods(customerId);
  res.json({ methods });
});

router.post('/setup-intent', async (req, res) => {
  const user = await pool.query('SELECT email FROM users WHERE id = $1', [req.auth!.userId]);
  const customerId = await getOrCreateStripeCustomer(req.auth!.userId, user.rows[0].email);
  if (!customerId) return res.json({ mock: true });

  const intent = await createSetupIntent(customerId);
  if (!intent) return res.json({ mock: true });
  res.json(intent);
});

router.delete('/methods/:id', async (req, res) => {
  const ok = await detachPaymentMethod(req.params.id);
  res.json({ ok });
});

export default router;
