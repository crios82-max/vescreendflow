export type Locale = 'es' | 'en';

export const LOCALES: Locale[] = ['es', 'en'];

export const LOCALE_LABELS: Record<Locale, string> = {
  es: 'Español',
  en: 'English',
};

export type TranslationParams = Record<string, string | number>;
