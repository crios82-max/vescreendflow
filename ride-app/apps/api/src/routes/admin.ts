import { Router } from 'express';
import { authMiddleware } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapRide, mapUser } from '../mappers.js';

const router = Router();

async function requireAdmin(req: import('express').Request, res: import('express').Response, next: import('express').NextFunction) {
  if (!req.auth) return res.status(401).json({ error: 'No autorizado' });
  const result = await pool.query('SELECT is_admin FROM users WHERE id = $1', [req.auth.userId]);
  if (!result.rows[0]?.is_admin) {
    return res.status(403).json({ error: 'Solo administradores' });
  }
  next();
}

router.use(authMiddleware, requireAdmin);

router.get('/stats', async (_req, res) => {
  const [users, drivers, rides, revenue] = await Promise.all([
    pool.query('SELECT COUNT(*)::int AS c FROM users'),
    pool.query('SELECT COUNT(*)::int AS c FROM users WHERE role = \'driver\''),
    pool.query('SELECT COUNT(*)::int AS c FROM rides'),
    pool.query('SELECT COALESCE(SUM(amount),0)::float AS t FROM payments WHERE status = \'paid\''),
  ]);
  res.json({
    users: users.rows[0].c,
    drivers: drivers.rows[0].c,
    rides: rides.rows[0].c,
    revenue: revenue.rows[0].t,
  });
});

router.get('/rides', async (_req, res) => {
  const result = await pool.query(
    `SELECT r.*, p.name AS passenger_name, d.name AS driver_name
     FROM rides r
     JOIN users p ON p.id = r.passenger_id
     LEFT JOIN users d ON d.id = r.driver_id
     ORDER BY r.created_at DESC LIMIT 100`,
  );
  res.json({
    rides: result.rows.map((row) => ({
      ...mapRide(row),
      passengerName: row.passenger_name,
      driverName: row.driver_name,
    })),
  });
});

router.get('/users', async (_req, res) => {
  const result = await pool.query('SELECT id, email, name, role, is_admin, created_at FROM users ORDER BY created_at DESC LIMIT 100');
  res.json({
    users: result.rows.map((row) => ({
      ...mapUser(row),
      isAdmin: row.is_admin as boolean,
      createdAt: (row.created_at as Date).toISOString(),
    })),
  });
});

export default router;
