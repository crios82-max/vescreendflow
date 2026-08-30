import type { Ride } from '@ride-app/shared';
import { BRAND, translate, type Locale } from '@ride-app/shared';
import { pool } from '../db.js';
import { sendEmail } from './email.js';
import { getUserLocale } from './locale.js';

export function buildReceiptHtml(
  ride: Ride & { tipAmount?: number; fareBreakdown?: Ride['fareBreakdown'] },
  locale: Locale,
) {
  const t = (key: string, params?: Record<string, string | number>) => translate(locale, key, params);
  const total = (ride.finalPrice ?? ride.estimatedPrice) + (ride.tipAmount ?? 0);
  const breakdown = ride.fareBreakdown;
  return `<!DOCTYPE html><html><body style="font-family:sans-serif;padding:24px;max-width:480px">
    <h1 style="margin:0 0 16px">${t('receipt.title', { brand: BRAND.name })}</h1>
    <p><strong>${t('receipt.ride')}:</strong> ${ride.id.slice(0, 8)}…</p>
    <p><strong>${t('receipt.origin')}:</strong> ${ride.pickupAddress}</p>
    <p><strong>${t('receipt.destination')}:</strong> ${ride.dropoffAddress}</p>
    <p><strong>${t('receipt.distanceLine', { km: ride.distanceKm, min: ride.durationMin })}</strong></p>
    ${breakdown ? `
    <hr style="border:none;border-top:1px solid #eee;margin:16px 0"/>
    <p>${t('receipt.base')}: $${breakdown.baseFare}</p>
    <p>${t('receipt.distanceFare')}: $${breakdown.distanceFare}</p>
    <p>${t('receipt.timeFare')}: $${breakdown.timeFare}</p>
    ${breakdown.surgeAmount > 0 ? `<p>${t('receipt.surge', { multiplier: breakdown.surgeMultiplier })}: $${breakdown.surgeAmount}</p>` : ''}
    ${breakdown.promoDiscount > 0 ? `<p>${t('receipt.discount')}: -$${breakdown.promoDiscount}</p>` : ''}
    ` : ''}
    <p><strong>${t('receipt.tripTotal')}:</strong> $${ride.finalPrice ?? ride.estimatedPrice}</p>
    ${(ride.tipAmount ?? 0) > 0 ? `<p><strong>${t('receipt.tip')}:</strong> $${ride.tipAmount}</p>` : ''}
    <p style="font-size:1.2em"><strong>${t('receipt.paidTotal')}: $${total}</strong></p>
    <p style="color:#888;margin-top:24px">${t('receipt.thanks', { brand: BRAND.name })}</p>
  </body></html>`;
}

export async function markReceiptSent(rideId: string) {
  await pool.query(`UPDATE rides SET receipt_sent_at = NOW() WHERE id = $1`, [rideId]);
}

export async function sendReceiptEmail(ride: Ride, userEmail: string, userId?: string) {
  const locale = userId ? await getUserLocale(userId) : 'es';
  const html = buildReceiptHtml(ride, locale);
  const total = (ride.finalPrice ?? ride.estimatedPrice) + (ride.tipAmount ?? 0);
  const subject = translate(locale, 'receipt.subject', { brand: BRAND.name, amount: total });
  const result = await sendEmail(userEmail, subject, html);
  await markReceiptSent(ride.id);
  return result;
}
