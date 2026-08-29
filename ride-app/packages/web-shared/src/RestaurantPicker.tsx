import { useEffect, useMemo, useState } from 'react';
import type { DeliveryCountry, DeliveryRestaurant, RestaurantCategory } from '@ride-app/shared';
import { DELIVERY_COUNTRIES, DELIVERY_COUNTRY_META } from '@ride-app/shared';
import { api } from './api';
import { useI18n } from './I18nProvider';

export interface RestaurantPick {
  restaurant: DeliveryRestaurant;
  lat: number;
  lng: number;
  address: string;
}

interface Props {
  selectedId?: string | null;
  onSelect: (pick: RestaurantPick) => void;
  onCountryChange?: (country: DeliveryCountry) => void;
}

type CategoryFilter = RestaurantCategory | 'all';

export function RestaurantPicker({ selectedId, onSelect, onCountryChange }: Props) {
  const { t } = useI18n();
  const [country, setCountry] = useState<DeliveryCountry>('ES');
  const [cities, setCities] = useState<string[]>([]);
  const [city, setCity] = useState(DELIVERY_COUNTRY_META.ES.defaultCity);
  const [category, setCategory] = useState<CategoryFilter>('all');
  const [q, setQ] = useState('');
  const [restaurants, setRestaurants] = useState<DeliveryRestaurant[]>([]);
  const [loading, setLoading] = useState(true);

  const changeCountry = (code: DeliveryCountry) => {
    setCountry(code);
    onCountryChange?.(code);
  };

  useEffect(() => {
    setCity(DELIVERY_COUNTRY_META[country].defaultCity);
    setQ('');
  }, [country]);

  useEffect(() => {
    setLoading(true);
    api
      .listRestaurants({ country, city, category, q: q || undefined })
      .then((r) => {
        setCities(r.cities);
        setRestaurants(r.restaurants);
        if (r.cities.length && !r.cities.includes(city)) {
          setCity(r.cities[0]);
        }
      })
      .catch(() => setRestaurants([]))
      .finally(() => setLoading(false));
  }, [country, city, category, q]);

  const labelFor = useMemo(
    () => ({
      all: t('service.allCategories'),
      fast_food: t('service.fastFood'),
      restaurant: t('service.restaurants'),
    }),
    [t],
  );

  const countryLabel = (code: DeliveryCountry) => t(DELIVERY_COUNTRY_META[code].labelKey);

  return (
    <div className="restaurant-picker">
      <div className="restaurant-picker__filters">
        <div className="tab-row" role="tablist" aria-label={t('service.country')}>
          {DELIVERY_COUNTRIES.map((code) => (
            <button
              key={code}
              type="button"
              className={`tab-btn${country === code ? ' tab-btn--active' : ''}`}
              onClick={() => changeCountry(code)}
            >
              {countryLabel(code)}
            </button>
          ))}
        </div>
        <select
          className="place-input"
          value={city}
          onChange={(e) => setCity(e.target.value)}
          aria-label={t('service.city')}
        >
          {(cities.length ? cities : [DELIVERY_COUNTRY_META[country].defaultCity]).map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
        <div className="tab-row" role="tablist" aria-label={t('service.category')}>
          {(['all', 'fast_food', 'restaurant'] as const).map((c) => (
            <button
              key={c}
              type="button"
              className={`tab-btn${category === c ? ' tab-btn--active' : ''}`}
              onClick={() => setCategory(c)}
            >
              {labelFor[c]}
            </button>
          ))}
        </div>
        <input
          className="place-input"
          placeholder={t('service.searchRestaurant')}
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>
      {loading ? (
        <p className="muted-text">{t('common.loading')}</p>
      ) : restaurants.length === 0 ? (
        <p className="muted-text">{t('service.noRestaurants')}</p>
      ) : (
        <div className="restaurant-list">
          {restaurants.map((r) => {
            const active = selectedId === r.id;
            return (
              <button
                key={r.id}
                type="button"
                className={`restaurant-card${active ? ' restaurant-card--active' : ''}`}
                onClick={() => onSelect({
                  restaurant: r,
                  lat: r.lat,
                  lng: r.lng,
                  address: `${r.name} · ${r.address}`,
                })}
              >
                <span className="restaurant-card__badge">
                  {r.category === 'fast_food' ? t('service.fastFood') : t('service.restaurants')}
                </span>
                <strong>{r.name}</strong>
                <span className="restaurant-card__meta">{r.address}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
