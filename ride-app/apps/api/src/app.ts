import express from 'express';
import cors from 'cors';
import { createServer, type Server as HttpServer } from 'http';
import { Server as SocketServer } from 'socket.io';
import authRoutes from './routes/auth.js';
import userRoutes from './routes/users.js';
import { createRidesRouter } from './routes/rides.js';
import { createDriversRouter } from './routes/drivers.js';
import pushRoutes from './routes/push.js';
import adminRoutes from './routes/admin.js';
import placesRoutes from './routes/places.js';
import walletRoutes from './routes/wallet.js';
import promoRoutes from './routes/promos.js';
import { createChatRouter } from './routes/chat.js';
import sosRoutes from './routes/sos.js';
import shareRoutes from './routes/share.js';
import onboardingRoutes from './routes/onboarding.js';
import connectRoutes from './routes/connect.js';
import verifyRoutes from './routes/verify.js';
import { createSplitRouter } from './routes/split.js';
import contactRoutes from './routes/contact.js';
import stripeWebhookRoutes from './routes/webhooks/stripe.js';
import twilioWebhookRoutes from './routes/webhooks/twilio.js';
import paymentsRoutes from './routes/payments.js';
import { pool } from './db.js';
import { activateScheduledRides } from './services/matching.js';

export interface RideApp {
  app: express.Express;
  httpServer: HttpServer;
  io: SocketServer;
}

export function createRideApp(): RideApp {
  const app = express();
  const httpServer = createServer(app);

  const corsOrigins = (process.env.CORS_ORIGINS ?? 'http://localhost:5174,http://localhost:5175,http://localhost:5176')
    .split(',')
    .map((s) => s.trim());

  const io = new SocketServer(httpServer, {
    cors: { origin: corsOrigins, credentials: true },
  });

  app.use(cors({ origin: corsOrigins, credentials: true }));

  app.use('/webhooks/stripe', express.raw({ type: 'application/json' }), stripeWebhookRoutes);
  app.use('/webhooks/twilio', express.urlencoded({ extended: false }), twilioWebhookRoutes);
  app.use(express.json());

  app.get('/health', async (_req, res) => {
    try {
      await pool.query('SELECT 1');
      res.json({ ok: true });
    } catch {
      res.status(503).json({ ok: false });
    }
  });

  app.use('/auth', authRoutes);
  app.use('/users', userRoutes);
  app.use('/rides', createRidesRouter(io));
  app.use('/drivers', createDriversRouter(io));
  app.use('/push', pushRoutes);
  app.use('/admin', adminRoutes);
  app.use('/places', placesRoutes);
  app.use('/wallet', walletRoutes);
  app.use('/promos', promoRoutes);
  app.use('/chat', createChatRouter(io));
  app.use('/sos', sosRoutes);
  app.use('/share', shareRoutes);
  app.use('/onboarding', onboardingRoutes);
  app.use('/connect', connectRoutes);
  app.use('/verify', verifyRoutes);
  app.use('/split', createSplitRouter(io));
  app.use('/payments', paymentsRoutes);
  app.use('/contact', contactRoutes);

  io.on('connection', (socket) => {
    socket.on('join:ride', (rideId: string) => {
      socket.join(`ride:${rideId}`);
    });
    socket.on('join:drivers', () => {
      socket.join('drivers:online');
    });
  });

  return { app, httpServer, io };
}

let scheduledTimer: ReturnType<typeof setInterval> | null = null;

export function startScheduledRidesJob() {
  if (scheduledTimer) return;
  scheduledTimer = setInterval(async () => {
    try {
      await activateScheduledRides();
    } catch (err) {
      console.warn('Scheduled rides tick failed:', err);
    }
  }, 60_000);
}

export function stopScheduledRidesJob() {
  if (scheduledTimer) {
    clearInterval(scheduledTimer);
    scheduledTimer = null;
  }
}
