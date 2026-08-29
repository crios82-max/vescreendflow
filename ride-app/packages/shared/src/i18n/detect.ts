import { DEFAULT_LOCALE, LOCALE_DETECT_ORDER, LOCALE_META, isLocale, type Locale } from './locales.js';
import { localeFromCountry, localeFromTimezone } from './regions.js';

export const LOCALE_MANUAL_KEY = 'movify_locale_manual';

/** Parsea etiquetas BCP-47: es-VE, pt-BR, en-US */
export function detectLocaleFromLanguageTag(tag: string): Locale | null {
  const normalized = tag.trim().toLowerCase().replace('_', '-');
  if (!normalized) return null;

  const [language, region] = normalized.split('-');
  if (region) {
    const fromRegion = localeFromCountry(region.toUpperCase());
    if (fromRegion) return fromRegion;
  }

  for (const code of LOCALE_DETECT_ORDER) {
    if (LOCALE_META[code].browserPrefixes.some((prefix) => language === prefix || language.startsWith(prefix))) {
      return code;
    }
  }
  return null;
}

export function detectLocaleFromLanguages(languages: string[]): Locale {
  for (const tag of languages) {
    const locale = detectLocaleFromLanguageTag(tag);
    if (locale) return locale;
  }
  return DEFAULT_LOCALE;
}

export function detectLocaleFromBrowser(): Locale {
  if (typeof navigator === 'undefined') return DEFAULT_LOCALE;
  const languages = navigator.languages?.length ? [...navigator.languages] : [navigator.language];
  return detectLocaleFromLanguages(languages.filter(Boolean));
}

export interface DetectLocaleOptions {
  /** Código país ISO (GPS, IP, reverse geocode) */
  countryCode?: string | null;
  /** Etiquetas de idioma del dispositivo/navegador */
  languages?: string[];
  /** Zona horaria (ej. America/Caracas) */
  timeZone?: string;
}

/** Resuelve idioma: país → zona horaria → navegador → español */
export function resolveLocaleFromPlace(options: DetectLocaleOptions = {}): Locale {
  const fromCountry = localeFromCountry(options.countryCode);
  if (fromCountry) return fromCountry;

  const fromTz = localeFromTimezone(options.timeZone);
  if (fromTz) return fromTz;

  if (options.languages?.length) {
    return detectLocaleFromLanguages(options.languages);
  }

  return detectLocaleFromBrowser();
}

export function hasManualLocale(read: (key: string) => string | null): boolean {
  return read(LOCALE_MANUAL_KEY) === '1';
}

export function getManualLocale(read: (key: string) => string | null): Locale | null {
  if (!hasManualLocale(read)) return null;
  const stored = read('movify_locale');
  return stored && isLocale(stored) ? stored : null;
}

export function clearManualLocale(remove: (key: string) => void): void {
  remove(LOCALE_MANUAL_KEY);
  remove('movify_locale');
}
