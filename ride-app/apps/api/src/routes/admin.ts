import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide, mapUser } from '../mappers.js';
import { sendPushToUser } from '../services/push.js';

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
    pool.query('SELECT COUNT(*)::int AS c FROM sos_events WHERE created_at >= NOW() - INTERVAL \'24 hours\''),
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

router.get('/rides', async (_req, res) => {
  const result = await pool.query(
    `SELECT r.*, p.name AS passenger_name, d.name AS driver_name
     FROM rides r JOIN users p ON p.id = r.passenger_id
     LEFT JOIN users d ON d.id = r.driver_id
     ORDER BY r.created_at DESC LIMIT 100`,
  );
  res.json({ rides: result.rows.map((row) => ({ ...mapRide(row), passengerName: row.passenger_name, driverName: row.driver_name })) });
});

router.get('/users', async (_req, res) => {
  const result = await pool.query(
    'SELECT id, email, name, role, is_admin, banned, wallet_balance, created_at FROM users ORDER BY created_at DESC LIMIT 100',
  );
  res.json({
    users: result.rows.map((row) => ({
      ...mapUser(row),
      isAdmin: row.is_admin as boolean,
      banned: row.banned as boolean,
      createdAt: (row.created_at as Date).toISOString(),
    })),
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
  await sendPushToUser(req.params.userId, 'Cuenta aprobada', 'Ya puedes ir online y recibir viajes');
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

router.post('/rides/:id/refund', async (req, res) => {
  const rideResult = await pool.query('SELECT * FROM rides WHERE id = $1', [req.params.id]);
  if (rideResult.rows.length === 0) return res.status(404).json({ error: 'Viaje no encontrado' });

  await pool.query(`UPDATE rides SET payment_status = 'failed' WHERE id = $1`, [req.params.id]);
  await pool.query(`UPDATE payments SET status = 'failed' WHERE ride_id = $1`, [req.params.id]);

  const ride = mapRide(rideResult.rows[0]);
  await pool.query(
    `INSERT INTO wallet_transactions (user_id, amount, type, description, ride_id)
     VALUES ($1, $2, 'credit', 'Reembolso admin', $3)`,
    [ride.passengerId, ride.finalPrice ?? ride.estimatedPrice, ride.id],
  );
  await pool.query(
    `UPDATE users SET wallet_balance = wallet_balance + $1 WHERE id = $2`,
    [ride.finalPrice ?? ride.estimatedPrice, ride.passengerId],
  );
  res.json({ ok: true });
});

router.get('/sos', async (_req, res) => {
  const result = await pool.query(
    `SELECT s.*, u.name, r.pickup_address FROM sos_events s
     JOIN users u ON u.id = s.user_id JOIN rides r ON r.id = s.ride_id
     ORDER BY s.created_at DESC LIMIT 50`,
  );
  res.json({ events: result.rows });
});

export default router;
