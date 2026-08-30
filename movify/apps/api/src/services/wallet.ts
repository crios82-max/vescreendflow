import { pool } from '../db.js';

export async function getWalletBalance(userId: string): Promise<number> {
  const result = await pool.query('SELECT wallet_balance FROM users WHERE id = $1', [userId]);
  return Number(result.rows[0]?.wallet_balance ?? 0);
}

export async function creditWallet(userId: string, amount: number, description: string, rideId?: string) {
  await pool.query(
    `UPDATE users SET wallet_balance = wallet_balance + $1 WHERE id = $2`,
    [amount, userId],
  );
  await pool.query(
    `INSERT INTO wallet_transactions (user_id, amount, type, description, ride_id)
     VALUES ($1, $2, 'credit', $3, $4)`,
    [userId, amount, description, rideId ?? null],
  );
}

export async function debitWallet(userId: string, amount: number, description: string, rideId?: string): Promise<boolean> {
  const balance = await getWalletBalance(userId);
  if (balance < amount) return false;
  await pool.query(
    `UPDATE users SET wallet_balance = wallet_balance - $1 WHERE id = $2`,
    [amount, userId],
  );
  await pool.query(
    `INSERT INTO wallet_transactions (user_id, amount, type, description, ride_id)
     VALUES ($1, $2, 'debit', $3, $4)`,
    [userId, -amount, description, rideId ?? null],
  );
  return true;
}
