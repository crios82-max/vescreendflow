import { pool } from '../db.js';

export async function validatePromo(code: string, subtotal: number): Promise<{ discount: number; code: string } | null> {
  const result = await pool.query(
    `SELECT * FROM promo_codes WHERE UPPER(code) = UPPER($1) AND active = TRUE`,
    [code],
  );
  if (result.rows.length === 0) return null;

  const promo = result.rows[0];
  if (promo.expires_at && new Date(promo.expires_at as string) < new Date()) return null;
  if (promo.max_uses != null && (promo.uses_count as number) >= (promo.max_uses as number)) return null;

  let discount = 0;
  if (promo.discount_type === 'percent') {
    discount = Math.round(subtotal * (Number(promo.discount_value) / 100) * 100) / 100;
  } else {
    discount = Number(promo.discount_value);
  }
  return { discount: Math.min(discount, subtotal), code: promo.code as string };
}

export async function redeemPromo(code: string) {
  await pool.query(
    `UPDATE promo_codes SET uses_count = uses_count + 1 WHERE UPPER(code) = UPPER($1)`,
    [code],
  );
}
