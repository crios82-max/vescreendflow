import type { Locale, TranslationParams } from './types.js';
import {
  dictionaries,
  FALLBACK_LOCALE,
  LOCALE_LABELS,
  SUPPORTED_LOCALES,
  detectLocaleFromLanguage,
  isLocale,
} from './locales.js';

export type { Locale, TranslationParams } from './types.js';
export {
  SUPPORTED_LOCALES,
  SUPPORTED_LOCALES as LOCALES,
  LOCALE_LABELS,
  LOCALE_META,
  LOCALE_DETECT_ORDER,
  DEFAULT_LOCALE,
  FALLBACK_LOCALE,
  dictionaries,
  isLocale,
  detectLocaleFromLanguage,
} from './locales.js';

export const LOCALE_STORAGE_KEY = 'movify_locale';

type RideStatus =
  | 'scheduled' | 'requested' | 'accepted' | 'arriving'
  | 'in_progress' | 'completed' | 'cancelled';

type VehicleType = 'standard' | 'comfort' | 'xl' | 'vans';

export type TranslationKey = string;

function getNested(obj: Record<string, unknown>, path: string): string | undefined {
  const value = path.split('.').reduce<unknown>((acc, part) => {
    if (acc && typeof acc === 'object' && part in acc) {
      return (acc as Record<string, unknown>)[part];
    }
    return undefined;
  }, obj);
  return typeof value === 'string' ? value : undefined;
}

export function translate(locale: Locale, key: TranslationKey, params?: TranslationParams): string {
  const dict = dictionaries[locale] as unknown as Record<string, unknown>;
  const fallback = dictionaries[FALLBACK_LOCALE] as unknown as Record<string, unknown>;
  let text = getNested(dict, key) ?? getNested(fallback, key) ?? key;
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      text = text.replaceAll(`{${k}}`, String(v));
    }
  }
  return text;
}

export function detectLocale(): Locale {
  if (typeof navigator !== 'undefined') {
    return detectLocaleFromLanguage(navigator.language);
  }
  return FALLBACK_LOCALE;
}

export function getStoredLocale(read: (key: string) => string | null): Locale {
  const stored = read(LOCALE_STORAGE_KEY);
  if (stored && isLocale(stored)) return stored;
  return detectLocale();
}

export function setStoredLocale(write: (key: string, value: string) => void, locale: Locale): void {
  write(LOCALE_STORAGE_KEY, locale);
}

export function rideStatusLabel(locale: Locale, status: RideStatus): string {
  return translate(locale, `rideStatus.${status}`);
}

export function vehicleLabel(locale: Locale, type: VehicleType): string {
  return translate(locale, `vehicle.${type}.label`);
}

export function vehicleDescription(locale: Locale, type: VehicleType): string {
  return translate(locale, `vehicle.${type}.description`);
}

export function brandTagline(locale: Locale): string {
  return translate(locale, 'brand.tagline');
}

export function brandRoleLabel(locale: Locale, role: 'passenger' | 'driver' | 'admin'): string {
  const key = role === 'passenger' ? 'brand.rolePassenger' : role === 'driver' ? 'brand.roleDriver' : 'brand.roleAdmin';
  return translate(locale, key);
}
