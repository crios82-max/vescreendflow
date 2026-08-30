import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { buildFareBreakdown } from './fare.ts';

describe('buildFareBreakdown', () => {
  it('calculates total with surge', () => {
    const b = buildFareBreakdown(10, 20, 'standard', 1.5, 0, 2.5, 1.2, 0.25);
    assert.ok(b.total > 0);
    assert.ok(b.surgeAmount > 0);
  });

  it('applies promo discount cap', () => {
    const b = buildFareBreakdown(5, 10, 'standard', 1, 100, 2.5, 1.2, 0.25);
    assert.ok(b.promoDiscount <= b.total + b.promoDiscount);
    assert.equal(b.total, 0);
  });
});
