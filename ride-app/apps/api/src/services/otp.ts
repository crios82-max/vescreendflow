import { randomInt } from 'crypto';
import { pool } from '../db.js';
import { sendEmail } from './email.js';

const OTP_TTL_MIN = 10;
const DEV_OTP = '123456';

function generateCode(): string {
  if (process.env.NODE_ENV !== 'production' && !process.env.TWILIO_ACCOUNT_SID) {
    return DEV_OTP;
  }
  return String(randomInt(100000, 999999));
}

async function sendSms(phone: string, message: string) {
  const sid = process.env.TWILIO_ACCOUNT_SID;
  const token = process.env.TWILIO_AUTH_TOKEN;
  const from = process.env.TWILIO_PHONE_NUMBER;

  if (!sid || !token || !from) {
    console.log(`[sms:mock] To: ${phone} | ${message}`);
    return { mock: true };
  }

  const auth = Buffer.from(`${sid}:${token}`).toString('base64');
  const body = new URLSearchParams({ To: phone, From: from, Body: message });
  const res = await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/Messages.json`, {
    method: 'POST',
    headers: { Authorization: `Basic ${auth}`, 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Twilio error: ${err}`);
  }
  return { mock: false };
}

export async function sendPhoneOtp(userId: string, phone: string) {
  const code = generateCode();
  const expiresAt = new Date(Date.now() + OTP_TTL_MIN * 60 * 1000);

  await pool.query(
    `INSERT INTO phone_otps (user_id, phone, code, expires_at) VALUES ($1, $2, $3, $4)`,
    [userId, phone, code, expiresAt],
  );

  const message = `Tu código Ride es: ${code}. Válido ${OTP_TTL_MIN} min.`;
  await sendSms(phone, message);

  if (process.env.OTP_EMAIL_FALLBACK === 'true') {
    const user = await pool.query('SELECT email FROM users WHERE id = $1', [userId]);
    if (user.rows[0]?.email) {
      await sendEmail(user.rows[0].email as string, 'Código Ride', `<p>${message}</p>`);
    }
  }

  return {
    sent: true,
    mock: !process.env.TWILIO_ACCOUNT_SID,
    devHint: process.env.NODE_ENV !== 'production' ? DEV_OTP : undefined,
  };
}

export async function verifyPhoneOtp(userId: string, phone: string, code: string) {
  const result = await pool.query(
    `SELECT * FROM phone_otps
     WHERE user_id = $1 AND phone = $2 AND verified = FALSE AND expires_at > NOW()
     ORDER BY created_at DESC LIMIT 1`,
    [userId, phone],
  );
  if (result.rows.length === 0) return false;

  const row = result.rows[0];
  const valid = row.code === code || (process.env.NODE_ENV !== 'production' && code === DEV_OTP);
  if (!valid) return false;

  await pool.query('UPDATE phone_otps SET verified = TRUE WHERE id = $1', [row.id]);
  await pool.query('UPDATE users SET phone = $1, phone_verified = TRUE WHERE id = $2', [phone, userId]);
  return true;
}
