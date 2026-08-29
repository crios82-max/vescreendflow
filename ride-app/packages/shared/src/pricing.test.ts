import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { buildRideEstimate, estimateFare, haversineKm, isDeliveryVehicle, vehiclesForMode } from './index.ts';

describe('haversineKm', () => {
  it('returns 0 for same point', () => {
    assert.equal(haversineKm(10.5, -66.9, 10.5, -66.9), 0);
  });

  it('returns positive distance for different points', () => {
    const d = haversineKm(10.48, -66.9, 10.5, -66.88);
    assert.ok(d > 0);
    assert.ok(d < 50);
  });
});

describe('estimateFare', () => {
  it('applies vehicle multiplier', () => {
    const base = estimateFare(10, 20, 2.5, 1.2, 0.25, 1);
    const comfort = estimateFare(10, 20, 2.5, 1.2, 0.25, 1.35);
    assert.ok(comfort > base);
  });
});

describe('buildRideEstimate', () => {
  it('returns all vehicle options', () => {
    const est = buildRideEstimate(5, 15);
    assert.equal(est.options.length, 6);
    assert.ok(est.options.some((o) => o.vehicleType === 'moto'));
    assert.ok(est.options.some((o) => o.vehicleType === 'bicicleta'));
    const moto = est.options.find((o) => o.vehicleType === 'moto')!;
    const standard = est.options.find((o) => o.vehicleType === 'standard')!;
    assert.ok(moto.estimatedPrice < standard.estimatedPrice);
    assert.ok(est.options[0].estimatedPrice > 0);
  });

  it('applies surge multiplier', () => {
    const normal = buildRideEstimate(5, 15, 2.5, 1.2, 0.25, null, 1);
    const surge = buildRideEstimate(5, 15, 2.5, 1.2, 0.25, null, 1.5);
    assert.ok(surge.options[0].estimatedPrice > normal.options[0].estimatedPrice);
  });

  it('splits ride vs delivery vehicles', () => {
    assert.deepEqual(vehiclesForMode('delivery'), ['moto', 'bicicleta']);
    assert.ok(isDeliveryVehicle('moto'));
    assert.ok(isDeliveryVehicle('bicicleta'));
    assert.equal(isDeliveryVehicle('standard'), false);
  });
});
