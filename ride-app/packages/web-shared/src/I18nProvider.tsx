import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import {
  type Locale,
  type TranslationKey,
  type TranslationParams,
  translate,
  detectLocale,
  getStoredLocale,
  setStoredLocale,
  LOCALE_STORAGE_KEY,
  LOCALES,
  LOCALE_LABELS,
  rideStatusLabel,
  vehicleLabel,
  brandTagline,
} from '@ride-app/shared';

interface I18nState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, params?: TranslationParams) => string;
  rideStatus: (status: Parameters<typeof rideStatusLabel>[1]) => string;
  vehicle: (type: Parameters<typeof vehicleLabel>[1]) => string;
  tagline: string;
}

const I18nContext = createContext<I18nState | null>(null);

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    /* ignore */
  }
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => getStoredLocale(readStorage));

  const setLocale = useCallback((next: Locale) => {
    setStoredLocale(writeStorage, next);
    setLocaleState(next);
    document.documentElement.lang = next;
  }, []);

  const t = useCallback(
    (key: TranslationKey, params?: TranslationParams) => translate(locale, key, params),
    [locale],
  );

  const value = useMemo<I18nState>(
    () => ({
      locale,
      setLocale,
      t,
      rideStatus: (status) => rideStatusLabel(locale, status),
      vehicle: (type) => vehicleLabel(locale, type),
      tagline: brandTagline(locale),
    }),
    [locale, setLocale, t],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n outside provider');
  return ctx;
}

export function LanguageSwitcher({ className = '' }: { className?: string }) {
  const { locale, setLocale } = useI18n();
  return (
    <div className={`lang-switcher ${className}`.trim()} role="group" aria-label="Language">
      {LOCALES.map((code) => (
        <button
          key={code}
          type="button"
          className={`lang-switcher__btn${locale === code ? ' lang-switcher__btn--active' : ''}`}
          onClick={() => setLocale(code)}
          aria-pressed={locale === code}
        >
          {code.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

export { LOCALE_STORAGE_KEY, LOCALES, LOCALE_LABELS, detectLocale, translate };
