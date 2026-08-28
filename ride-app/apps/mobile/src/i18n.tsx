import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
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
  rideStatusLabel,
  vehicleLabel,
  brandTagline,
} from '@ride-app/shared';

interface MobileI18nState {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: TranslationKey, params?: TranslationParams) => string;
  rideStatus: (status: Parameters<typeof rideStatusLabel>[1]) => string;
  vehicle: (type: Parameters<typeof vehicleLabel>[1]) => string;
  tagline: string;
  ready: boolean;
}

const MobileI18nContext = createContext<MobileI18nState | null>(null);

async function readLocale(): Promise<Locale> {
  try {
    const stored = await AsyncStorage.getItem(LOCALE_STORAGE_KEY);
    return getStoredLocale(() => stored);
  } catch {
    return detectLocale();
  }
}

export function MobileI18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>('es');
  const [ready, setReady] = useState(false);

  useEffect(() => {
    readLocale().then((value) => {
      setLocaleState(value);
      setReady(true);
    });
  }, []);

  const setLocale = useCallback(async (next: Locale) => {
    setStoredLocale((key, value) => { void AsyncStorage.setItem(key, value); }, next);
    setLocaleState(next);
  }, []);

  const t = useCallback(
    (key: TranslationKey, params?: TranslationParams) => translate(locale, key, params),
    [locale],
  );

  const value = useMemo<MobileI18nState>(
    () => ({
      locale,
      setLocale,
      t,
      rideStatus: (status) => rideStatusLabel(locale, status),
      vehicle: (type) => vehicleLabel(locale, type),
      tagline: brandTagline(locale),
      ready,
    }),
    [locale, setLocale, t, ready],
  );

  return <MobileI18nContext.Provider value={value}>{children}</MobileI18nContext.Provider>;
}

export function useMobileI18n() {
  const ctx = useContext(MobileI18nContext);
  if (!ctx) throw new Error('useMobileI18n outside provider');
  return ctx;
}

export { LOCALES };
