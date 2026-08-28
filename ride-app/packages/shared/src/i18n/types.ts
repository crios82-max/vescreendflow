export type Locale = 'es' | 'en' | 'it';

export const LOCALES: Locale[] = ['es', 'en', 'it'];

export const LOCALE_LABELS: Record<Locale, string> = {
  es: 'Español',
  en: 'English',
  it: 'Italiano',
};

export type TranslationParams = Record<string, string | number>;
