import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide, mapUser } from '../mappers.js';
import { sendLocalizedPush } from '../services/push.js';
import { refundStripePayment } from '../services/stripe.js';
import { creditWallet } from '../services/wallet.js';

const router = Router();

async function requireAdmin(req: import('express').Request, res: import('express').Response, next: import('express').NextFunction) {
  if (!req.auth) return res.status(401).json({ error: 'No autorizado' });
  const result = await pool.query('SELECT is_admin FROM users WHERE id = $1', [req.auth.userId]);
  if (!result.rows[0]?.is_admin) return res.status(403).json({ error: 'Solo administradores' });
  next();
}

router.use(authMiddleware, requireAdmin);

router.get('/stats', async (_req, res) => {
  const [users, drivers, rides, revenue, pendingDrivers, sos] = await Promise.all([
    pool.query('SELECT COUNT(*)::int AS c FROM users'),
    pool.query('SELECT COUNT(*)::int AS c FROM users WHERE role = \'driver\''),
    pool.query('SELECT COUNT(*)::int AS c FROM rides'),
    pool.query('SELECT COALESCE(SUM(amount),0)::float AS t FROM payments WHERE status = \'paid\''),
    pool.query('SELECT COUNT(*)::int AS c FROM driver_profiles WHERE approval_status = \'pending\''),
    pool.query('SELECT COUNT(*)::int AS c FROM sos_events WHERE created_at >= NOW() - INTERVAL \'24 hours\' AND acknowledged_at IS NULL'),
  ]);
  res.json({
    users: users.rows[0].c,
    drivers: drivers.rows[0].c,
    rides: rides.rows[0].c,
    revenue: revenue.rows[0].t,
    pendingDrivers: pendingDrivers.rows[0].c,
    sosLast24h: sos.rows[0].c,
  });
});

router.get('/rides', async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit) || 20, 1), 100);
  const offset = Math.max(Number(req.query.offset) || 0, 0);
  const q = String(req.query.q ?? '').trim();
  const params: unknown[] = [];
  let where = '';
  if (q) {
    params.push(`%${q}%`);
    where = `WHERE p.name ILIKE $1 OR d.name ILIKE $1 OR r.pickup_address ILIKE $1 OR r.dropoff_address ILIKE $1 OR r.status ILIKE $1`;
  }
  const countResult = await pool.query(
    `SELECT COUNT(*)::int AS c FROM rides r
     JOIN users p ON p.id = r.passenger_id
     LEFT JOIN users d ON d.id = r.driver_id ${where}`,
    params,
  );
  params.push(limit);
  const limIdx = params.length;
  params.push(offset);
  const offIdx = params.length;
  const result = await pool.query(
    `SELECT r.*, p.name AS passenger_name, d.name AS driver_name
     FROM rides r JOIN users p ON p.id = r.passenger_id
     LEFT JOIN users d ON d.id = r.driver_id
     ${where}
     ORDER BY r.created_at DESC LIMIT $${limIdx} OFFSET $${offIdx}`,
    params,
  );
  res.json({
    rides: result.rows.map((row) => ({ ...mapRide(row), passengerName: row.passenger_name, driverName: row.driver_name })),
    total: countResult.rows[0].c,
    limit,
    offset,
  });
});

router.get('/users', async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit) || 20, 1), 100);
  const offset = Math.max(Number(req.query.offset) || 0, 0);
  const q = String(req.query.q ?? '').trim();
  const params: unknown[] = [];
  let where = '';
  if (q) {
    params.push(`%${q}%`);
    where = 'WHERE name ILIKE $1 OR email ILIKE $1';
  }
  const countResult = await pool.query(`SELECT COUNT(*)::int AS c FROM users ${where}`, params);
  params.push(limit);
  const limIdx = params.length;
  params.push(offset);
  const offIdx = params.length;
  const result = await pool.query(
    `SELECT id, email, name, role, is_admin, banned, wallet_balance, created_at FROM users
     ${where}
     ORDER BY created_at DESC LIMIT $${limIdx} OFFSET $${offIdx}`,
    params,
  );
  res.json({
    users: result.rows.map((row) => ({
      ...mapUser(row),
      isAdmin: row.is_admin as boolean,
      banned: row.banned as boolean,
      createdAt: (row.created_at as Date).toISOString(),
    })),
    total: countResult.rows[0].c,
    limit,
    offset,
  });
});

router.get('/drivers/pending', async (_req, res) => {
  const result = await pool.query(
    `SELECT dp.*, u.name, u.email FROM driver_profiles dp
     JOIN users u ON u.id = dp.user_id WHERE dp.approval_status = 'pending'`,
  );
  res.json({
    drivers: result.rows.map((r) => ({
      userId: r.user_id,
      name: r.name,
      email: r.email,
      vehicleType: r.vehicle_type,
      licenseUrl: r.license_url,
      idUrl: r.id_url,
      vehiclePhotoUrl: r.vehicle_photo_url,
    })),
  });
});

router.post('/drivers/:userId/approve', async (req, res) => {
  await pool.query(
    `UPDATE driver_profiles SET approval_status = 'approved', rejection_reason = NULL WHERE user_id = $1`,
    [req.params.userId],
  );
  await sendLocalizedPush(req.params.userId, 'push.accountApprovedTitle', 'push.accountApprovedBody');
  res.json({ ok: true });
});

router.post('/drivers/:userId/reject', async (req, res) => {
  const reason = (req.body as { reason?: string }).reason ?? 'Documentos incompletos';
  await pool.query(
    `UPDATE driver_profiles SET approval_status = 'rejected', rejection_reason = $1 WHERE user_id = $2`,
    [reason, req.params.userId],
  );
  res.json({ ok: true });
});

router.post('/users/:id/ban', async (req, res) => {
  await pool.query('UPDATE users SET banned = TRUE WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
});

router.post('/users/:id/unban', async (req, res) => {
  await pool.query('UPDATE users SET banned = FALSE WHERE id = $1', [req.params.id]);
  res.json({ ok: true });
});

router.get('/promos', async (_req, res) => {
  const result = await pool.query('SELECT * FROM promo_codes ORDER BY created_at DESC');
  res.json({ promos: result.rows });
});

router.post('/promos', async (req, res) => {
  const schema = z.object({
    code: z.string().min(2),
    discountType: z.enum(['percent', 'fixed']),
    discountValue: z.number().positive(),
    maxUses: z.number().int().optional(),
  });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  await pool.query(
    `INSERT INTO promo_codes (code, discount_type, discount_value, max_uses)
     VALUES ($1, $2, $3, $4) ON CONFLICT (code) DO UPDATE SET discount_type = EXCLUDED.discount_type,
       discount_value = EXCLUDED.discount_value, max_uses = EXCLUDED.max_uses, active = TRUE`,
    [parsed.data.code.toUpperCase(), parsed.data.discountType, parsed.data.discountValue, parsed.data.maxUses ?? null],
  );
  res.json({ ok: true });
});

router.post('/promos/:code/deactivate', async (req, res) => {
  const result = await pool.query(
    `UPDATE promo_codes SET active = FALSE WHERE code = $1 RETURNING code`,
    [req.params.code.toUpperCase()],
  );
  if (result.rows.length === 0) return res.status(404).json({ error: 'Código inválido o expirado' });
  res.json({ ok: true });
});

router.post('/rides/:id/refund', async (req, res) => {
  const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
  if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

  const ride = mapRide(rideResult.rows[0]);
  const amount = ride.finalPrice ?? ride.estimatedPrice;

  const paymentResult = await pool.query(
    `SELECT stripe_payment_intent_id, status FROM payments WHERE ride_id = $1 ORDER BY created_at DESC LIMIT 1`,
    [req.params.id],
  );
  const payment = paymentResult.rows[0];
  let stripeRefund = { refunded: false, mock: true };

  if (payment?.stripe_payment_intent_id && payment.status === 'paid') {
    stripeRefund = await refundStripePayment(payment.stripe_payment_intent_id);
  }

  await pool.query(`UPDATE rides SET payment_status = 'failed' WHERE id = $1`, [req.params.id]);
  await pool.query(`UPDATE payments SET status = 'failed' WHERE ride_id = $1`, [req.params.id]);

  if (!stripeRefund.refunded) {
    await creditWallet(ride.passengerId, amount, 'Reembolso admin', ride.id);
  }

  res.json({ ok: true, stripeRefund });
});

router.get('/sos', async (_req, res) => {
  const result = await pool.query(
    `SELECT s.*, u.name, r.pickup_address FROM sos_events s
     JOIN users u ON u.id = s.user_id JOIN rides r ON r.id = s.ride_id
     ORDER BY s.created_at DESC LIMIT 50`,
  );
  res.json({
    events: result.rows.map((row) => ({
      id: row.id,
      user_id: row.user_id,
      ride_id: row.ride_id,
      name: row.name,
      pickup_address: row.pickup_address,
      lat: row.lat,
      lng: row.lng,
      created_at: row.created_at,
      acknowledged_at: row.acknowledged_at ?? null,
    })),
  });
});

router.post('/sos/:id/ack', async (req, res) => {
  const result = await pool.query(
    `UPDATE sos_events SET acknowledged_at = NOW(), acknowledged_by = $1
     WHERE id = $2 RETURNING id`,
    [req.auth!.userId, req.params.id],
  );
  if (result.rows.length === 0) return res.status(404).json({ error: 'Alerta SOS no encontrada' });
  res.json({ ok: true });
});

export default router;
