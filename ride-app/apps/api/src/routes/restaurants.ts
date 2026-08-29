import { Router } from 'express';
import {
  DELIVERY_COUNTRIES,
  deliveryCities,
  getDeliveryRestaurant,
  listDeliveryRestaurants,
  type RestaurantCategory,
} from '@ride-app/shared';

const router = Router();

router.get('/', (req, res) => {
  const country = typeof req.query.country === 'string' ? req.query.country : 'ES';
  const city = typeof req.query.city === 'string' ? req.query.city : undefined;
  const categoryRaw = typeof req.query.category === 'string' ? req.query.category : 'all';
  const q = typeof req.query.q === 'string' ? req.query.q : undefined;
  const category =
    categoryRaw === 'fast_food' || categoryRaw === 'restaurant' || categoryRaw === 'all'
      ? (categoryRaw as RestaurantCategory | 'all')
      : 'all';

  const restaurants = listDeliveryRestaurants({ country, city, category, q });
  res.json({
    country,
    cities: deliveryCities(country),
    countries: [...DELIVERY_COUNTRIES],
    count: restaurants.length,
    restaurants,
  });
});

router.get('/:id', (req, res) => {
  const restaurant = getDeliveryRestaurant(req.params.id);
  if (!restaurant) return res.status(404).json({ error: 'Restaurante no encontrado' });
  res.json({ restaurant });
});

export default router;
