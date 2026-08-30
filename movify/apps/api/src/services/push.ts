import { Expo, type ExpoPushMessage } from 'expo-server-sdk';
import { translate } from '@ride-app/shared';
import { pool } from '../db.js';
import { getUserLocale } from './locale.js';

const expo = new Expo();

const RIDE_STATUS_KEYS: Record<string, { title: string; body: string }> = {
  accepted: { title: 'push.acceptedTitle', body: 'push.acceptedBody' },
  arriving: { title: 'push.arrivingTitle', body: 'push.arrivingBody' },
  in_progress: { title: 'push.in_progressTitle', body: 'push.in_progressBody' },
  completed: { title: 'push.completedTitle', body: 'push.completedBody' },
  cancelled: { title: 'push.cancelledTitle', body: 'push.cancelledBody' },
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

export async function sendLocalizedPush(
  userId: string,
  titleKey: string,
  bodyKey: string,
  params?: Record<string, string | number>,
  data?: Record<string, string>,
) {
  const locale = await getUserLocale(userId);
  const title = translate(locale, titleKey, params);
  const body = translate(locale, bodyKey, params);
  await sendPushToUser(userId, title, body, data);
}

export async function notifyRideEvent(
  ride: { id: string; passengerId: string; driverId: string | null; status: string },
  title?: string,
  body?: string,
) {
  const data = { rideId: ride.id, status: ride.status };
  const keys = RIDE_STATUS_KEYS[ride.status];

  const notifyUser = async (userId: string) => {
    if (title && body) {
      await sendPushToUser(userId, title, body, data);
      return;
    }
    if (keys) {
      await sendLocalizedPush(userId, keys.title, keys.body, undefined, data);
      return;
    }
    const locale = await getUserLocale(userId);
    await sendPushToUser(
      userId,
      translate(locale, 'push.rideUpdate'),
      translate(locale, 'push.rideStatus', { status: ride.status }),
      data,
    );
  };

  await notifyUser(ride.passengerId);
  if (ride.driverId) await notifyUser(ride.driverId);
}
