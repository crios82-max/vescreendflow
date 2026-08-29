/** Delivery restaurant / fast-food catalog — Spain first. */

export type RestaurantCategory = 'fast_food' | 'restaurant';

export interface DeliveryRestaurant {
  id: string;
  name: string;
  category: RestaurantCategory;
  cuisine: string;
  city: string;
  country: 'ES';
  address: string;
  lat: number;
  lng: number;
}

export const DELIVERY_COUNTRIES = ['ES'] as const;
export type DeliveryCountry = (typeof DELIVERY_COUNTRIES)[number];

export const SPAIN_CITIES = [
  'Madrid',
  'Barcelona',
  'Valencia',
  'Sevilla',
  'Málaga',
  'Bilbao',
  'Zaragoza',
] as const;

export type SpainCity = (typeof SPAIN_CITIES)[number];

/** Curated pickup points for food delivery (real neighbourhood coords, illustrative branches). */
export const DELIVERY_RESTAURANTS: DeliveryRestaurant[] = [
  // —— Madrid ——
  {
    id: 'es-mad-mcd-sol',
    name: "McDonald's Sol",
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Madrid',
    country: 'ES',
    address: 'Plaza de la Puerta del Sol 6, Madrid',
    lat: 40.4169,
    lng: -3.7035,
  },
  {
    id: 'es-mad-bk-granvia',
    name: 'Burger King Gran Vía',
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Madrid',
    country: 'ES',
    address: 'Gran Vía 49, Madrid',
    lat: 40.4203,
    lng: -3.7058,
  },
  {
    id: 'es-mad-kfc-príncipe',
    name: 'KFC Príncipe Pío',
    category: 'fast_food',
    cuisine: 'chicken',
    city: 'Madrid',
    country: 'ES',
    address: 'Paseo de la Florida 2, Madrid',
    lat: 40.4211,
    lng: -3.7201,
  },
  {
    id: 'es-mad-telepizza-malasaña',
    name: 'Telepizza Malasaña',
    category: 'fast_food',
    cuisine: 'pizza',
    city: 'Madrid',
    country: 'ES',
    address: 'Calle Fuencarral 45, Madrid',
    lat: 40.4241,
    lng: -3.7009,
  },
  {
    id: 'es-mad-dominos-latina',
    name: "Domino's Pizza Latina",
    category: 'fast_food',
    cuisine: 'pizza',
    city: 'Madrid',
    country: 'ES',
    address: 'Calle de la Princesa 25, Madrid',
    lat: 40.4272,
    lng: -3.7165,
  },
  {
    id: 'es-mad-goiko-chueca',
    name: 'Goiko Grill Chueca',
    category: 'restaurant',
    cuisine: 'burgers',
    city: 'Madrid',
    country: 'ES',
    address: 'Calle Hortaleza 49, Madrid',
    lat: 40.4248,
    lng: -3.6975,
  },
  {
    id: 'es-mad-100montaditos',
    name: '100 Montaditos Plaza Mayor',
    category: 'restaurant',
    cuisine: 'spanish',
    city: 'Madrid',
    country: 'ES',
    address: 'Plaza Mayor 1, Madrid',
    lat: 40.4155,
    lng: -3.7074,
  },
  {
    id: 'es-mad-vips-castellana',
    name: "Vips Castellana",
    category: 'restaurant',
    cuisine: 'casual',
    city: 'Madrid',
    country: 'ES',
    address: 'Paseo de la Castellana 89, Madrid',
    lat: 40.4475,
    lng: -3.6905,
  },
  {
    id: 'es-mad-fosters-azca',
    name: "Foster's Hollywood AZCA",
    category: 'restaurant',
    cuisine: 'american',
    city: 'Madrid',
    country: 'ES',
    address: 'Paseo de la Castellana 95, Madrid',
    lat: 40.4502,
    lng: -3.6912,
  },
  {
    id: 'es-mad-rodilla-atocha',
    name: 'Rodilla Atocha',
    category: 'fast_food',
    cuisine: 'sandwiches',
    city: 'Madrid',
    country: 'ES',
    address: 'Plaza del Emperador Carlos V, Madrid',
    lat: 40.4065,
    lng: -3.6915,
  },

  // —— Barcelona ——
  {
    id: 'es-bcn-mcd-rambla',
    name: "McDonald's La Rambla",
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Barcelona',
    country: 'ES',
    address: 'La Rambla 115, Barcelona',
    lat: 41.3835,
    lng: 2.1734,
  },
  {
    id: 'es-bcn-bk-diagonal',
    name: 'Burger King Diagonal',
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Barcelona',
    country: 'ES',
    address: 'Avinguda Diagonal 523, Barcelona',
    lat: 41.3947,
    lng: 2.1528,
  },
  {
    id: 'es-bcn-kfc-glories',
    name: 'KFC Glòries',
    category: 'fast_food',
    cuisine: 'chicken',
    city: 'Barcelona',
    country: 'ES',
    address: 'Avinguda Diagonal 208, Barcelona',
    lat: 41.4036,
    lng: 2.1914,
  },
  {
    id: 'es-bcn-telepizza-gracia',
    name: 'Telepizza Gràcia',
    category: 'fast_food',
    cuisine: 'pizza',
    city: 'Barcelona',
    country: 'ES',
    address: 'Carrer Gran de Gràcia 78, Barcelona',
    lat: 41.4002,
    lng: 2.1575,
  },
  {
    id: 'es-bcn-tagliatella',
    name: 'La Tagliatella Born',
    category: 'restaurant',
    cuisine: 'italian',
    city: 'Barcelona',
    country: 'ES',
    address: 'Passeig del Born 16, Barcelona',
    lat: 41.3852,
    lng: 2.1833,
  },
  {
    id: 'es-bcn-goiko-eixample',
    name: 'Goiko Eixample',
    category: 'restaurant',
    cuisine: 'burgers',
    city: 'Barcelona',
    country: 'ES',
    address: 'Carrer de Provença 231, Barcelona',
    lat: 41.3931,
    lng: 2.1608,
  },
  {
    id: 'es-bcn-100montaditos',
    name: '100 Montaditos Barceloneta',
    category: 'restaurant',
    cuisine: 'spanish',
    city: 'Barcelona',
    country: 'ES',
    address: 'Passeig Joan de Borbó 42, Barcelona',
    lat: 41.3789,
    lng: 2.1895,
  },

  // —— Valencia ——
  {
    id: 'es-vlc-mcd-colon',
    name: "McDonald's Colón",
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Valencia',
    country: 'ES',
    address: 'Calle de Colón 20, Valencia',
    lat: 39.4695,
    lng: -0.3712,
  },
  {
    id: 'es-vlc-bk-nuevo-centro',
    name: 'Burger King Nuevo Centro',
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Valencia',
    country: 'ES',
    address: 'Avenida de Pius XII 2, Valencia',
    lat: 39.4812,
    lng: -0.3905,
  },
  {
    id: 'es-vlc-telepizza-ruzafa',
    name: 'Telepizza Ruzafa',
    category: 'fast_food',
    cuisine: 'pizza',
    city: 'Valencia',
    country: 'ES',
    address: 'Calle de Cádiz 38, Valencia',
    lat: 39.4628,
    lng: -0.3725,
  },
  {
    id: 'es-vlc-100montaditos',
    name: '100 Montaditos Mercado Central',
    category: 'restaurant',
    cuisine: 'spanish',
    city: 'Valencia',
    country: 'ES',
    address: 'Plaza del Mercado, Valencia',
    lat: 39.4745,
    lng: -0.3788,
  },

  // —— Sevilla ——
  {
    id: 'es-sev-mcd-sierpes',
    name: "McDonald's Sierpes",
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Sevilla',
    country: 'ES',
    address: 'Calle Sierpes 55, Sevilla',
    lat: 37.3891,
    lng: -5.9945,
  },
  {
    id: 'es-sev-bk-nervion',
    name: 'Burger King Nervión',
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Sevilla',
    country: 'ES',
    address: 'Avenida Luis de Morales, Sevilla',
    lat: 37.3835,
    lng: -5.9728,
  },
  {
    id: 'es-sev-kfc-plaza',
    name: 'KFC Plaza de Armas',
    category: 'fast_food',
    cuisine: 'chicken',
    city: 'Sevilla',
    country: 'ES',
    address: 'Plaza de Armas, Sevilla',
    lat: 37.3918,
    lng: -6.0042,
  },
  {
    id: 'es-sev-tagliatella',
    name: 'La Tagliatella Triana',
    category: 'restaurant',
    cuisine: 'italian',
    city: 'Sevilla',
    country: 'ES',
    address: 'Calle Betis 12, Sevilla',
    lat: 37.3832,
    lng: -6.0025,
  },

  // —— Málaga ——
  {
    id: 'es-mlg-mcd-larios',
    name: "McDonald's Larios",
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Málaga',
    country: 'ES',
    address: 'Calle Marqués de Larios 5, Málaga',
    lat: 36.7202,
    lng: -4.4214,
  },
  {
    id: 'es-mlg-bk-malagueta',
    name: 'Burger King Malagueta',
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Málaga',
    country: 'ES',
    address: 'Paseo Marítimo Pablo Ruiz Picasso, Málaga',
    lat: 36.7165,
    lng: -4.4112,
  },
  {
    id: 'es-mlg-telepizza',
    name: 'Telepizza Centro',
    category: 'fast_food',
    cuisine: 'pizza',
    city: 'Málaga',
    country: 'ES',
    address: 'Calle Granada 40, Málaga',
    lat: 36.7218,
    lng: -4.4185,
  },

  // —— Bilbao ——
  {
    id: 'es-bio-mcd-moyua',
    name: "McDonald's Moyúa",
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Bilbao',
    country: 'ES',
    address: 'Plaza Federico Moyúa, Bilbao',
    lat: 43.2627,
    lng: -2.9350,
  },
  {
    id: 'es-bio-bk-indautxu',
    name: 'Burger King Indautxu',
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Bilbao',
    country: 'ES',
    address: 'Plaza Indautxu, Bilbao',
    lat: 43.2605,
    lng: -2.9442,
  },
  {
    id: 'es-bio-100montaditos',
    name: '100 Montaditos Casco Viejo',
    category: 'restaurant',
    cuisine: 'spanish',
    city: 'Bilbao',
    country: 'ES',
    address: 'Calle del Correo 2, Bilbao',
    lat: 43.2575,
    lng: -2.9235,
  },

  // —— Zaragoza ——
  {
    id: 'es-zgz-mcd-paseo',
    name: "McDonald's Paseo Independencia",
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Zaragoza',
    country: 'ES',
    address: 'Paseo de la Independencia 24, Zaragoza',
    lat: 41.6505,
    lng: -0.8805,
  },
  {
    id: 'es-zgz-bk-granvia',
    name: 'Burger King Gran Vía',
    category: 'fast_food',
    cuisine: 'burgers',
    city: 'Zaragoza',
    country: 'ES',
    address: 'Avenida de César Augusto, Zaragoza',
    lat: 41.6542,
    lng: -0.8865,
  },
  {
    id: 'es-zgz-telepizza',
    name: 'Telepizza Delicias',
    category: 'fast_food',
    cuisine: 'pizza',
    city: 'Zaragoza',
    country: 'ES',
    address: 'Avenida de Navarra 50, Zaragoza',
    lat: 41.6485,
    lng: -0.9102,
  },
];

export interface RestaurantFilter {
  country?: DeliveryCountry | string;
  city?: string;
  category?: RestaurantCategory | 'all';
  q?: string;
}

export function listDeliveryRestaurants(filter: RestaurantFilter = {}): DeliveryRestaurant[] {
  const q = filter.q?.trim().toLowerCase();
  return DELIVERY_RESTAURANTS.filter((r) => {
    if (filter.country && r.country !== filter.country) return false;
    if (filter.city && r.city.toLowerCase() !== filter.city.toLowerCase()) return false;
    if (filter.category && filter.category !== 'all' && r.category !== filter.category) return false;
    if (q) {
      const hay = `${r.name} ${r.cuisine} ${r.city} ${r.address}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });
}

export function getDeliveryRestaurant(id: string): DeliveryRestaurant | undefined {
  return DELIVERY_RESTAURANTS.find((r) => r.id === id);
}

export function deliveryCities(country: DeliveryCountry | string = 'ES'): string[] {
  const cities = new Set(
    DELIVERY_RESTAURANTS.filter((r) => r.country === country).map((r) => r.city),
  );
  return [...cities].sort((a, b) => a.localeCompare(b, 'es'));
}
