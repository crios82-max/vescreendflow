import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  DELIVERY_RESTAURANTS,
  deliveryCities,
  getDeliveryRestaurant,
  listDeliveryRestaurants,
} from './restaurants.ts';

describe('delivery restaurants (Spain)', () => {
  it('has Spain-only catalog with fast food and restaurants', () => {
    assert.ok(DELIVERY_RESTAURANTS.length >= 20);
    assert.ok(DELIVERY_RESTAURANTS.every((r) => r.country === 'ES'));
    assert.ok(DELIVERY_RESTAURANTS.some((r) => r.category === 'fast_food'));
    assert.ok(DELIVERY_RESTAURANTS.some((r) => r.category === 'restaurant'));
  });

  it('filters by city and category', () => {
    const madridFast = listDeliveryRestaurants({ country: 'ES', city: 'Madrid', category: 'fast_food' });
    assert.ok(madridFast.length >= 3);
    assert.ok(madridFast.every((r) => r.city === 'Madrid' && r.category === 'fast_food'));
  });

  it('searches by name', () => {
    const hits = listDeliveryRestaurants({ q: 'mcdonald' });
    assert.ok(hits.length >= 1);
    assert.ok(hits.every((r) => r.name.toLowerCase().includes('mcdonald')));
  });

  it('lists cities and gets by id', () => {
    const cities = deliveryCities('ES');
    assert.ok(cities.includes('Madrid'));
    assert.ok(cities.includes('Barcelona'));
    const one = getDeliveryRestaurant('es-mad-mcd-sol');
    assert.equal(one?.city, 'Madrid');
  });
});
