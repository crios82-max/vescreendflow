import { after, before, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import request from 'supertest';
import { createRideApp } from '../app.js';

const runE2e = process.env.RUN_E2E === 'true';

describe('API E2E', { skip: !runE2e }, () => {
  const { app, httpServer } = createRideApp();
  let passengerToken = '';

  before(async () => {
    const health = await request(app).get('/health');
    if (health.status !== 200) {
      throw new Error('DB not ready — run scripts/setup-test-db.sh first');
    }
  });

  after(() => {
    httpServer.close();
  });

  it('GET /health', async () => {
    const res = await request(app).get('/health');
    assert.equal(res.status, 200);
    assert.equal(res.body.ok, true);
  });

  it('register + login passenger', async () => {
    const email = `e2e-${Date.now()}@test.local`;
    const reg = await request(app).post('/auth/register').send({
      email,
      password: 'secret123',
      name: 'E2E User',
      role: 'passenger',
      phone: '+15551234567',
    });
    assert.equal(reg.status, 201);
    assert.ok(reg.body.token);

    const login = await request(app).post('/auth/login').send({
      email,
      password: 'secret123',
    });
    assert.equal(login.status, 200);
    passengerToken = login.body.token;
  });

  it('POST /rides/estimate', async () => {
    const res = await request(app)
      .post('/rides/estimate')
      .set('Authorization', `Bearer ${passengerToken}`)
      .send({
        pickupAddress: 'Origen test',
        pickupLat: 10.48,
        pickupLng: -66.9,
        dropoffAddress: 'Destino test',
        dropoffLat: 10.5,
        dropoffLng: -66.88,
      });
    assert.equal(res.status, 200);
    assert.ok(res.body.options?.length > 0);
    assert.ok(res.body.distanceKm > 0);
  });

  it('POST /webhooks/twilio/voice/connect returns TwiML', async () => {
    const res = await request(app)
      .post('/webhooks/twilio/voice/connect?callee=%2B15559876543')
      .type('form')
      .send({});
    assert.equal(res.status, 200);
    assert.match(res.text, /<Dial/);
  });
});
