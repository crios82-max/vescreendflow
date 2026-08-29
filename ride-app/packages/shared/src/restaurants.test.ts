import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  DELIVERY_COUNTRIES,
  DELIVERY_RESTAURANTS,
  deliveryCities,
  getDeliveryRestaurant,
  listDeliveryRestaurants,
} from './restaurants.ts';

describe('delivery restaurants', () => {
  it('covers ES, VE and IT', () => {
    assert.deepEqual([...DELIVERY_COUNTRIES], ['ES', 'VE', 'IT']);
    assert.ok(DELIVERY_RESTAURANTS.some((r) => r.country === 'ES'));
    assert.ok(DELIVERY_RESTAURANTS.some((r) => r.country === 'VE'));
    assert.ok(DELIVERY_RESTAURANTS.some((r) => r.country === 'IT'));
    assert.ok(DELIVERY_RESTAURANTS.some((r) => r.category === 'fast_food'));
    assert.ok(DELIVERY_RESTAURANTS.some((r) => r.category === 'restaurant'));
  });

  it('filters Spain Madrid fast food', () => {
    const madridFast = listDeliveryRestaurants({ country: 'ES', city: 'Madrid', category: 'fast_food' });
    assert.ok(madridFast.length >= 3);
    assert.ok(madridFast.every((r) => r.city === 'Madrid' && r.category === 'fast_food'));
  });

  it('filters Venezuela Caracas', () => {
    const caracas = listDeliveryRestaurants({ country: 'VE', city: 'Caracas' });
    assert.ok(caracas.length >= 5);
    assert.ok(caracas.every((r) => r.country === 'VE' && r.city === 'Caracas'));
    assert.ok(deliveryCities('VE').includes('Caracas'));
    assert.ok(deliveryCities('VE').includes('Maracaibo'));
  });

  it('filters Italy Roma', () => {
    const roma = listDeliveryRestaurants({ country: 'IT', city: 'Roma' });
    assert.ok(roma.length >= 3);
    assert.ok(roma.every((r) => r.country === 'IT'));
    assert.ok(deliveryCities('IT').includes('Milano'));
  });

  it('searches by name and gets by id', () => {
    const hits = listDeliveryRestaurants({ country: 'VE', q: 'arepera' });
    assert.ok(hits.length >= 1);
    const one = getDeliveryRestaurant('ve-ccs-mcd-chacao');
    assert.equal(one?.city, 'Caracas');
  });
});
