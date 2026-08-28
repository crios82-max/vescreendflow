import type { Ride } from '@ride-app/shared';
import { pool } from '../db.js';
import { sendEmail } from './email.js';

export function buildReceiptHtml(ride: Ride & { tipAmount?: number; fareBreakdown?: Ride['fareBreakdown'] }) {
  const total = (ride.finalPrice ?? ride.estimatedPrice) + (ride.tipAmount ?? 0);
  const breakdown = ride.fareBreakdown;
  return `<!DOCTYPE html><html><body style="font-family:sans-serif;padding:24px;max-width:480px">
    <h1 style="margin:0 0 16px">Recibo Ride</h1>
    <p><strong>Viaje:</strong> ${ride.id.slice(0, 8)}…</p>
    <p><strong>Origen:</strong> ${ride.pickupAddress}</p>
    <p><strong>Destino:</strong> ${ride.dropoffAddress}</p>
    <p><strong>Distancia:</strong> ${ride.distanceKm} km · ${ride.durationMin} min</p>
    ${breakdown ? `
    <hr style="border:none;border-top:1px solid #eee;margin:16px 0"/>
    <p>Base: $${breakdown.baseFare}</p>
    <p>Distancia: $${breakdown.distanceFare}</p>
    <p>Tiempo: $${breakdown.timeFare}</p>
    ${breakdown.surgeAmount > 0 ? `<p>Surge (${breakdown.surgeMultiplier}x): $${breakdown.surgeAmount}</p>` : ''}
    ${breakdown.promoDiscount > 0 ? `<p>Descuento: -$${breakdown.promoDiscount}</p>` : ''}
    ` : ''}
    <p><strong>Total viaje:</strong> $${ride.finalPrice ?? ride.estimatedPrice}</p>
    ${(ride.tipAmount ?? 0) > 0 ? `<p><strong>Propina:</strong> $${ride.tipAmount}</p>` : ''}
    <p style="font-size:1.2em"><strong>Total pagado: $${total}</strong></p>
    <p style="color:#888;margin-top:24px">Gracias por viajar con Ride</p>
  </body></html>`;
}

export async function markReceiptSent(rideId: string) {
  await pool.query(`UPDATE rides SET receipt_sent_at = NOW() WHERE id = $1`, [rideId]);
}

export async function sendReceiptEmail(ride: Ride, userEmail: string) {
  const html = buildReceiptHtml(ride);
  const subject = `Recibo Ride — $${(ride.finalPrice ?? ride.estimatedPrice) + (ride.tipAmount ?? 0)}`;
  const result = await sendEmail(userEmail, subject, html);
  await markReceiptSent(ride.id);
  return result;
}
