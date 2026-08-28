import { Expo, type ExpoPushMessage } from 'expo-server-sdk';
import { pool } from '../db.js';

const expo = new Expo();

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
  title: string,
  body: string,
) {
  await sendPushToUser(ride.passengerId, title, body, { rideId: ride.id, status: ride.status });
  if (ride.driverId) {
    await sendPushToUser(ride.driverId, title, body, { rideId: ride.id, status: ride.status });
  }
}
