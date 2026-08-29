import { Router } from 'express';
import { z } from 'zod';
import { authMiddleware, requireRole } from '../middleware/auth.js';
import { pool } from '../db.js';
import { mapDriverProfile } from '../mappers.js';
import { sendError } from '../httpError.js';

const router = Router();
router.use(authMiddleware, requireRole('driver'));

const docsSchema = z.object({
  licenseUrl: z.string().url().optional(),
  idUrl: z.string().url().optional(),
  vehiclePhotoUrl: z.string().url().optional(),
});

router.get('/status', async (req, res) => {
  const result = await pool.query('SELECT * FROM driver_profiles WHERE user_id = $1', [req.auth!.userId]);
  if (result.rows.length === 0) return sendError(res, 404, 'Perfil no encontrado', 'PROFILE_NOT_FOUND');
  const profile = mapDriverProfile(result.rows[0]);
  res.json({
    approvalStatus: profile.approvalStatus,
    rejectionReason: result.rows[0].rejection_reason ?? null,
    documents: {
      licenseUrl: result.rows[0].license_url ?? null,
      idUrl: result.rows[0].id_url ?? null,
      vehiclePhotoUrl: result.rows[0].vehicle_photo_url ?? null,
    },
  });
});

router.post('/documents', async (req, res) => {
  const parsed = docsSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: parsed.error.flatten() });

  const result = await pool.query(
    `UPDATE driver_profiles
     SET license_url = COALESCE($1, license_url),
         id_url = COALESCE($2, id_url),
         vehicle_photo_url = COALESCE($3, vehicle_photo_url),
         approval_status = 'pending'
     WHERE user_id = $4 RETURNING *`,
    [parsed.data.licenseUrl ?? null, parsed.data.idUrl ?? null, parsed.data.vehiclePhotoUrl ?? null, req.auth!.userId],
  );
  res.json({ approvalStatus: result.rows[0].approval_status });
});

router.get('/earnings', async (req, res) => {
  const profile = await pool.query('SELECT total_earnings FROM driver_profiles WHERE user_id = $1', [req.auth!.userId]);
  const today = await pool.query(
    `SELECT COALESCE(SUM(final_price), 0)::float AS total, COUNT(*)::int AS rides
     FROM rides WHERE driver_id = $1 AND status = 'completed' AND completed_at >= CURRENT_DATE`,
    [req.auth!.userId],
  );
  const week = await pool.query(
    `SELECT COALESCE(SUM(final_price), 0)::float AS total, COUNT(*)::int AS rides
     FROM rides WHERE driver_id = $1 AND status = 'completed' AND completed_at >= NOW() - INTERVAL '7 days'`,
    [req.auth!.userId],
  );
  res.json({
    totalEarnings: Number(profile.rows[0]?.total_earnings ?? 0),
    today: today.rows[0],
    week: week.rows[0],
  });
});

export default router;
