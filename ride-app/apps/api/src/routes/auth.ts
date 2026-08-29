import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { randomBytes } from 'crypto';
import { z } from 'zod';
import { pool } from '../db.js';
import { signToken } from '../middleware/auth.js';
import { mapUser } from '../mappers.js';
import { sendEmail } from '../services/email.js';
import { sendError } from '../httpError.js';
import { BRAND } from '@ride-app/shared';

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
      return sendError(res, 409, 'Email ya registrado', 'EMAIL_TAKEN');
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
    return sendError(res, 401, 'Credenciales inválidas', 'INVALID_CREDENTIALS');
  }

  const row = result.rows[0];
  if (row.banned) {
    return sendError(res, 403, 'Cuenta suspendida', 'ACCOUNT_SUSPENDED');
  }
  const valid = await bcrypt.compare(password, row.password_hash);
  if (!valid) {
    return sendError(res, 401, 'Credenciales inválidas', 'INVALID_CREDENTIALS');
  }

  const user = mapUser(row);
  const token = signToken({ userId: user.id, role: user.role });
  res.json({ token, user });
});

router.post('/forgot-password', async (req, res) => {
  const schema = z.object({ email: z.string().email() });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const result = await pool.query('SELECT id, role, preferred_locale FROM users WHERE email = $1', [parsed.data.email]);
  if (result.rows.length === 0) {
    return res.json({ ok: true, message: 'Si el email existe, recibirás instrucciones' });
  }

  const token = randomBytes(32).toString('hex');
  const expires = new Date(Date.now() + 60 * 60 * 1000);
  await pool.query(
    `INSERT INTO password_reset_tokens (user_id, token, expires_at) VALUES ($1, $2, $3)`,
    [result.rows[0].id, token, expires],
  );

  const role = result.rows[0].role as string;
  const locale = (result.rows[0].preferred_locale as string) || 'es';
  const baseUrl =
    role === 'driver'
      ? (process.env.DRIVER_WEB_URL ?? 'http://localhost:5175')
      : (process.env.PASSENGER_WEB_URL ?? 'http://localhost:5174');
  const resetUrl = `${baseUrl}/reset-password?token=${token}`;
  const subject =
    locale === 'en'
      ? `Reset password — ${BRAND.name}`
      : locale === 'it'
        ? `Reimposta password — ${BRAND.name}`
        : locale === 'pt'
          ? `Redefinir senha — ${BRAND.name}`
          : `Restablecer contraseña — ${BRAND.name}`;
  const body =
    locale === 'en'
      ? `<p>Use this link to reset your password (valid 1h):</p><p><a href="${resetUrl}">${resetUrl}</a></p>`
      : locale === 'it'
        ? `<p>Usa questo link per reimpostare la password (valido 1h):</p><p><a href="${resetUrl}">${resetUrl}</a></p>`
        : locale === 'pt'
          ? `<p>Use este link para redefinir sua senha (válido 1h):</p><p><a href="${resetUrl}">${resetUrl}</a></p>`
          : `<p>Usa este enlace para restablecer tu contraseña (válido 1h):</p><p><a href="${resetUrl}">${resetUrl}</a></p>`;
  await sendEmail(parsed.data.email, subject, body);

  res.json({ ok: true, message: 'Si el email existe, recibirás instrucciones', devResetUrl: process.env.NODE_ENV !== 'production' ? resetUrl : undefined });
});

router.post('/reset-password', async (req, res) => {
  const schema = z.object({
    token: z.string().min(10),
    password: z.string().min(6),
  });
  const parsed = schema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const tokenResult = await pool.query(
    `SELECT * FROM password_reset_tokens WHERE token = $1 AND used_at IS NULL AND expires_at > NOW()`,
    [parsed.data.token],
  );
  if (tokenResult.rows.length === 0) {
    return sendError(res, 400, 'Token inválido o expirado', 'INVALID_TOKEN');
  }

  const hash = await bcrypt.hash(parsed.data.password, 10);
  const userId = tokenResult.rows[0].user_id;
  await pool.query('UPDATE users SET password_hash = $1 WHERE id = $2', [hash, userId]);
  await pool.query('UPDATE password_reset_tokens SET used_at = NOW() WHERE id = $1', [tokenResult.rows[0].id]);

  res.json({ ok: true });
});

export default router;
