import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  type Locale,
  type TranslationKey,
  type TranslationParams,
  translate,
  detectLocaleFromBrowser,
  getManualLocale,
  hasManualLocale,
  setStoredLocale,
  LOCALE_STORAGE_KEY,
  LOCALES,
  LOCALE_LABELS,
  rideStatusLabel,
  vehicleLabel,
  brandTagline,
  translateApiError,
} from '@ride-app/shared';
import { detectLocaleWeb } from './detectLocaleWeb';

interface I18nState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, params?: TranslationParams) => string;
  rideStatus: (status: Parameters<typeof rideStatusLabel>[1]) => string;
  vehicle: (type: Parameters<typeof vehicleLabel>[1]) => string;
  tagline: string;
  te: (message: string) => string;
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
  const [locale, setLocaleState] = useState<Locale>(
    () => getManualLocale(readStorage) ?? detectLocaleFromBrowser(),
  );

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  useEffect(() => {
    if (hasManualLocale(readStorage)) return;
    let cancelled = false;
    detectLocaleWeb().then((detected) => {
      if (!cancelled) {
        setLocaleState(detected);
        document.documentElement.lang = detected;
      }
    });
    return () => { cancelled = true; };
  }, []);

  const setLocale = useCallback((next: Locale) => {
    setStoredLocale(writeStorage, next, true);
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
      te: (message) => translateApiError(locale, message),
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

export { LOCALE_STORAGE_KEY, LOCALES, LOCALE_LABELS, detectLocaleFromBrowser as detectLocale, translate };
