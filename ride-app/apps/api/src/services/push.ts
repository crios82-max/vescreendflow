import { Expo, type ExpoPushMessage } from 'expo-server-sdk';
import { pool } from '../db.js';

const expo = new Expo();

const STATUS_MESSAGES: Record<string, { title: string; body: string }> = {
  accepted: { title: 'Conductor asignado', body: 'Tu conductor va en camino' },
  arriving: { title: 'Conductor cerca', body: 'Tu conductor está llegando al punto de recogida' },
  in_progress: { title: 'Viaje iniciado', body: '¡Buen viaje!' },
  completed: { title: 'Viaje completado', body: 'Califica tu experiencia' },
  cancelled: { title: 'Viaje cancelado', body: 'Tu viaje fue cancelado' },
};

export async function sendPushToUser(userId: string, title: string, body: string, data?: Record<string, string>) {
  const result = await pool.query('SELECT token FROM push_tokens WHERE user_id = $1', [userId]);
  const messages: ExpoPushMessage[] = [];

  for (const row of result.rows) {
    const token = row.token as string;
    if (!Expo.isExpoPushToken(token)) continue;
    messages.push({ to: token, title, body, data, sound: 'default' });
  }

  if (messages.length === 0) return;

  const chunks = expo.chunkPushNotifications(messages);
  for (const chunk of chunks) {
    try {
      await expo.sendPushNotificationsAsync(chunk);
    } catch (err) {
      console.warn('Push failed:', err);
    }
  }
}

export async function notifyRideEvent(
  ride: { id: string; passengerId: string; driverId: string | null; status: string },
  title?: string,
  body?: string,
) {
  const msg = STATUS_MESSAGES[ride.status];
  const t = title ?? msg?.title ?? 'Actualización de viaje';
  const b = body ?? msg?.body ?? `Estado: ${ride.status}`;
  const data = { rideId: ride.id, status: ride.status };

  await sendPushToUser(ride.passengerId, t, b, data);
  if (ride.driverId) {
    await sendPushToUser(ride.driverId, t, b, data);
  }
}
