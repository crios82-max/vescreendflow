import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import { pool } from '../db.js';
import { signToken } from '../middleware/auth.js';
import { mapUser } from '../mappers.js';

const router = Router();

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(6),
  name: z.string().min(2),
  phone: z.string().optional(),
  role: z.enum(['passenger', 'driver']),
  vehicleMake: z.string().optional(),
  vehicleModel: z.string().optional(),
  vehiclePlate: z.string().optional(),
  vehicleType: z.enum(['standard', 'comfort', 'xl', 'vans']).optional(),
});

router.post('/register', async (req, res) => {
  const parsed = registerSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.flatten() });
  }

  const { email, password, name, phone, role, vehicleMake, vehicleModel, vehiclePlate, vehicleType } = parsed.data;
  const hash = await bcrypt.hash(password, 10);

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const userResult = await client.query(
      `INSERT INTO users (email, password_hash, name, phone, role)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING *`,
      [email, hash, name, phone ?? null, role],
    );
    const user = mapUser(userResult.rows[0]);

    if (role === 'driver') {
      await client.query(
        `INSERT INTO driver_profiles (user_id, vehicle_make, vehicle_model, vehicle_plate, vehicle_type, approval_status)
         VALUES ($1, $2, $3, $4, $5, 'pending')`,
        [user.id, vehicleMake ?? null, vehicleModel ?? null, vehiclePlate ?? null, vehicleType ?? 'standard'],
      );
    }

    await client.query('COMMIT');
    const token = signToken({ userId: user.id, role: user.role });
    res.status(201).json({ token, user });
  } catch (err: unknown) {
    await client.query('ROLLBACK');
    if (err && typeof err === 'object' && 'code' in err && err.code === '23505') {
      return res.status(409).json({ error: 'Email ya registrado' });
    }
    throw err;
  } finally {
    client.release();
  }
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string(),
});

router.post('/login', async (req, res) => {
  const parsed = loginSchema.safeParse(req.body);
  if (!parsed.success) {
    return res.status(400).json({ error: parsed.error.flatten() });
  }

  const { email, password } = parsed.data;
  const result = await pool.query('SELECT * FROM users WHERE email = $1', [email]);
  if (result.rows.length === 0) {
    return res.status(401).json({ error: 'Credenciales inválidas' });
  }

  const row = result.rows[0];
  if (row.banned) {
    return res.status(403).json({ error: 'Cuenta suspendida' });
  }
  const valid = await bcrypt.compare(password, row.password_hash);
  if (!valid) {
    return res.status(401).json({ error: 'Credenciales inválidas' });
  }

  const user = mapUser(row);
  const token = signToken({ userId: user.id, role: user.role });
  res.json({ token, user });
});

export default router;
