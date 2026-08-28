/** Marca central — cambia aquí para renombrar toda la app */
export const BRAND = {
  name: 'Movify',
  tagline: 'Muévete fácil.',
  legalName: 'Movify',
  domain: 'vescreenflow.com',
  apiHost: 'movify-api.vescreenflow.com',
  webHost: 'movify.vescreenflow.com',
  apiUrl: 'https://movify-api.vescreenflow.com',
  webUrl: 'https://movify.vescreenflow.com',
  colors: {
    primary: '#A3E635',
    primaryOnDark: '#000000',
    background: '#000000',
    surface: '#111111',
  },
  /** iOS / Android — fijar antes del primer build TestFlight */
  bundleId: 'com.movify.app',
  slug: 'movify',
  scheme: 'movify',
  supportEmail: 'hola@vescreenflow.com',
} as const;

export function brandTitle(suffix?: string): string {
  return suffix ? `${BRAND.name} — ${suffix}` : BRAND.name;
}

export function brandAppLabel(role: 'passenger' | 'driver' | 'admin', locale: 'es' | 'en' | 'it' = 'es'): string {
  const labels = {
    es: { passenger: 'Pasajero', driver: 'Conductor', admin: 'Admin' },
    en: { passenger: 'Passenger', driver: 'Driver', admin: 'Admin' },
    it: { passenger: 'Passeggero', driver: 'Autista', admin: 'Admin' },
  };
  return `${BRAND.name} ${labels[locale][role]}`;
}
