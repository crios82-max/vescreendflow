import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { validatePromo } from '../services/promo.js';

const router = Router();
router.use(authMiddleware);

router.post('/validate', async (req, res) => {
  const schema = z.object({ code: z.string().min(2), subtotal: z.number().min(0) });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const promo = await validatePromo(parsed.data.code, parsed.data.subtotal);
  if (!promo) return res.status(404).json({ error: 'Código inválido o expirado' });
  res.json(promo);
});

export default router;
