import { es } from './es.js';
import { en } from './en.js';
import { it } from './it.js';
import { pt } from './pt.js';

/**
 * Registro central de idiomas — para agregar uno nuevo:
 * 1. Copia `en.ts` → `xx.ts` y traduce
 * 2. Importa aquí y añade a SUPPORTED_LOCALES + LOCALE_META + dictionaries
 */
export const SUPPORTED_LOCALES = ['es', 'en', 'it', 'pt'] as const;
export type Locale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: Locale = 'es';
export const FALLBACK_LOCALE: Locale = 'es';

/** Orden de detección automática (más específico primero) */
export const LOCALE_DETECT_ORDER: Locale[] = ['pt', 'it', 'en', 'es'];

export const LOCALE_META: Record<
  Locale,
  { label: string; browserPrefixes: string[] }
> = {
  es: { label: 'Español', browserPrefixes: ['es'] },
  en: { label: 'English', browserPrefixes: ['en'] },
  it: { label: 'Italiano', browserPrefixes: ['it'] },
  pt: { label: 'Português', browserPrefixes: ['pt'] },
};

export const LOCALE_LABELS: Record<Locale, string> = Object.fromEntries(
  SUPPORTED_LOCALES.map((code) => [code, LOCALE_META[code].label]),
) as Record<Locale, string>;

export const dictionaries = { es, en, it, pt } as const;

export function isLocale(value: string): value is Locale {
  return (SUPPORTED_LOCALES as readonly string[]).includes(value);
}

export function detectLocaleFromLanguage(lang: string): Locale {
  const normalized = lang.toLowerCase();
  for (const code of LOCALE_DETECT_ORDER) {
    if (LOCALE_META[code].browserPrefixes.some((prefix) => normalized.startsWith(prefix))) {
      return code;
    }
  }
  return DEFAULT_LOCALE;
}
