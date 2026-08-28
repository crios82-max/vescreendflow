import type { Ride } from '@ride-app/shared';
import { pool } from '../db.js';

export function buildReceiptHtml(ride: Ride & { tipAmount?: number; fareBreakdown?: Ride['fareBreakdown'] }) {
  const total = (ride.finalPrice ?? ride.estimatedPrice) + (ride.tipAmount ?? 0);
  const breakdown = ride.fareBreakdown;
  return `<!DOCTYPE html><html><body style="font-family:sans-serif;padding:24px">
    <h1>Recibo Ride</h1>
    <p><strong>Viaje:</strong> ${ride.id}</p>
    <p><strong>Origen:</strong> ${ride.pickupAddress}</p>
    <p><strong>Destino:</strong> ${ride.dropoffAddress}</p>
    <p><strong>Distancia:</strong> ${ride.distanceKm} km · ${ride.durationMin} min</p>
    ${breakdown ? `
    <hr/>
    <p>Base: $${breakdown.baseFare}</p>
    <p>Distancia: $${breakdown.distanceFare}</p>
    <p>Tiempo: $${breakdown.timeFare}</p>
    ${breakdown.surgeAmount > 0 ? `<p>Surge (${breakdown.surgeMultiplier}x): $${breakdown.surgeAmount}</p>` : ''}
    ${breakdown.promoDiscount > 0 ? `<p>Descuento: -$${breakdown.promoDiscount}</p>` : ''}
    ` : ''}
    <p><strong>Total viaje:</strong> $${ride.finalPrice ?? ride.estimatedPrice}</p>
    ${(ride.tipAmount ?? 0) > 0 ? `<p><strong>Propina:</strong> $${ride.tipAmount}</p>` : ''}
    <p><strong>Total pagado:</strong> $${total}</p>
    <p style="color:#666">Gracias por viajar con Ride</p>
  </body></html>`;
}

export async function markReceiptSent(rideId: string) {
  await pool.query(`UPDATE rides SET receipt_sent_at = NOW() WHERE id = $1`, [rideId]);
}

export async function sendReceiptEmail(ride: Ride, userEmail: string) {
  const html = buildReceiptHtml(ride);
  // Mock email — log for now; plug SMTP/nodemailer when configured
  console.log(`[receipt] To: ${userEmail} ride=${ride.id} bytes=${html.length}`);
  await markReceiptSent(ride.id);
  return { sent: true, mock: !process.env.SMTP_HOST };
}
