import { type Locale, FALLBACK_LOCALE, isLocale } from '@ride-app/shared';
import { pool } from '../db.js';

export async function getUserLocale(userId: string): Promise<Locale> {
  const result = await pool.query('SELECT preferred_locale FROM users WHERE id = $1', [userId]);
  const locale = result.rows[0]?.preferred_locale;
  return isLocale(locale) ? locale : FALLBACK_LOCALE;
}
