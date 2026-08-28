import dotenv from 'dotenv';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { createRideApp, startScheduledRidesJob } from './app.js';
import { BRAND } from '@ride-app/shared';

const __dirname = dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: join(__dirname, '../../../.env') });

const { httpServer } = createRideApp();
startScheduledRidesJob();

const port = Number(process.env.PORT ?? 4001);
httpServer.listen(port, () => {
  console.log(`${BRAND.name} API listening on http://localhost:${port}`);
});
