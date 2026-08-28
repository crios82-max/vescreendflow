/** Marca central — cambia aquí para renombrar toda la app */
export const BRAND = {
  name: 'Movi',
  tagline: 'Muévete fácil.',
  legalName: 'Movi',
  domain: 'vescreenflow.com',
  apiHost: 'movi-api.vescreenflow.com',
  webHost: 'movi.vescreenflow.com',
  apiUrl: 'https://movi-api.vescreenflow.com',
  webUrl: 'https://movi.vescreenflow.com',
  colors: {
    primary: '#A3E635',
    primaryOnDark: '#000000',
    background: '#000000',
    surface: '#111111',
  },
  /** iOS / Android — fijar antes del primer build TestFlight */
  bundleId: 'com.movi.app',
  slug: 'movi',
  scheme: 'movi',
  supportEmail: 'hola@vescreenflow.com',
} as const;

export function brandTitle(suffix?: string): string {
  return suffix ? `${BRAND.name} — ${suffix}` : BRAND.name;
}

export function brandAppLabel(role: 'passenger' | 'driver' | 'admin'): string {
  const labels = { passenger: 'Pasajero', driver: 'Conductor', admin: 'Admin' };
  return `${BRAND.name} ${labels[role]}`;
}
