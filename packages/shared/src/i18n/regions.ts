import type { Locale } from './locales.js';

/** Países → idioma Movify (ISO 3166-1 alpha-2) */
export const COUNTRY_TO_LOCALE: Record<string, Locale> = {
  // Español
  ES: 'es', MX: 'es', AR: 'es', CO: 'es', CL: 'es', PE: 'es', VE: 'es', EC: 'es',
  GT: 'es', CU: 'es', BO: 'es', DO: 'es', HN: 'es', PY: 'es', SV: 'es', NI: 'es',
  CR: 'es', PA: 'es', UY: 'es', PR: 'es', GQ: 'es', GY: 'es',
  // Português
  BR: 'pt', PT: 'pt', AO: 'pt', MZ: 'pt', CV: 'pt', GW: 'pt', ST: 'pt', TL: 'pt',
  // Italiano
  IT: 'it', SM: 'it', VA: 'it',
  // English (principales mercados)
  US: 'en', GB: 'en', AU: 'en', NZ: 'en', IE: 'en', CA: 'en', ZA: 'en',
  IN: 'en', PH: 'en', SG: 'en', MY: 'en', NG: 'en', KE: 'en', GH: 'en',
  JM: 'en', TT: 'en', BB: 'en', BS: 'en', BZ: 'en', MT: 'en', CY: 'en',
};

/** Zonas horarias → idioma (fallback sin GPS) */
const TIMEZONE_PREFIXES: Array<{ match: string; locale: Locale }> = [
  { match: 'America/Sao_Paulo', locale: 'pt' },
  { match: 'America/Manaus', locale: 'pt' },
  { match: 'America/Belem', locale: 'pt' },
  { match: 'America/Fortaleza', locale: 'pt' },
  { match: 'America/Recife', locale: 'pt' },
  { match: 'America/Bahia', locale: 'pt' },
  { match: 'America/Cuiaba', locale: 'pt' },
  { match: 'America/Campo_Grande', locale: 'pt' },
  { match: 'America/Porto_Velho', locale: 'pt' },
  { match: 'America/Boa_Vista', locale: 'pt' },
  { match: 'America/Rio_Branco', locale: 'pt' },
  { match: 'America/Noronha', locale: 'pt' },
  { match: 'Europe/Lisbon', locale: 'pt' },
  { match: 'Atlantic/Azores', locale: 'pt' },
  { match: 'Atlantic/Madeira', locale: 'pt' },
  { match: 'Europe/Rome', locale: 'it' },
  { match: 'Europe/London', locale: 'en' },
  { match: 'Europe/Dublin', locale: 'en' },
  { match: 'America/New_York', locale: 'en' },
  { match: 'America/Chicago', locale: 'en' },
  { match: 'America/Denver', locale: 'en' },
  { match: 'America/Los_Angeles', locale: 'en' },
  { match: 'America/Phoenix', locale: 'en' },
  { match: 'America/Toronto', locale: 'en' },
  { match: 'America/Vancouver', locale: 'en' },
  { match: 'Australia/', locale: 'en' },
  { match: 'Pacific/Auckland', locale: 'en' },
  { match: 'America/Caracas', locale: 'es' },
  { match: 'America/Bogota', locale: 'es' },
  { match: 'America/Lima', locale: 'es' },
  { match: 'America/Santiago', locale: 'es' },
  { match: 'America/Argentina', locale: 'es' },
  { match: 'America/Mexico', locale: 'es' },
  { match: 'America/Guatemala', locale: 'es' },
  { match: 'America/El_Salvador', locale: 'es' },
  { match: 'America/Costa_Rica', locale: 'es' },
  { match: 'America/Panama', locale: 'es' },
  { match: 'America/Montevideo', locale: 'es' },
  { match: 'America/Asuncion', locale: 'es' },
  { match: 'America/La_Paz', locale: 'es' },
  { match: 'America/Guayaquil', locale: 'es' },
  { match: 'America/Havana', locale: 'es' },
  { match: 'America/Santo_Domingo', locale: 'es' },
  { match: 'America/Puerto_Rico', locale: 'es' },
  { match: 'Europe/Madrid', locale: 'es' },
];

export function localeFromCountry(countryCode: string | null | undefined): Locale | null {
  if (!countryCode) return null;
  return COUNTRY_TO_LOCALE[countryCode.toUpperCase()] ?? null;
}

export function localeFromTimezone(timeZone?: string): Locale | null {
  const tz = timeZone ?? (typeof Intl !== 'undefined' ? Intl.DateTimeFormat().resolvedOptions().timeZone : '');
  if (!tz) return null;
  for (const { match, locale } of TIMEZONE_PREFIXES) {
    if (tz.startsWith(match) || tz.includes(match)) return locale;
  }
  return null;
}
