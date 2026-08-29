import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  type Locale,
  type TranslationKey,
  type TranslationParams,
  translate,
  setStoredLocale,
  LOCALE_STORAGE_KEY,
  LOCALE_MANUAL_KEY,
  LOCALES,
  rideStatusLabel,
  vehicleLabel,
  brandTagline,
  isLocale,
} from '@ride-app/shared';
import { detectLocaleMobile } from './detectLocaleMobile';

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

async function readStorage(key: string): Promise<string | null> {
  try {
    return await AsyncStorage.getItem(key);
  } catch {
    return null;
  }
}

export function MobileI18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>('es');
  const [ready, setReady] = useState(false);

  useEffect(() => {
    (async () => {
      const manual = await readStorage(LOCALE_MANUAL_KEY);
      if (manual === '1') {
        const stored = await readStorage(LOCALE_STORAGE_KEY);
        if (stored && isLocale(stored)) {
          setLocaleState(stored);
          setReady(true);
          return;
        }
      }

      const detected = await detectLocaleMobile();
      setLocaleState(detected);
      setReady(true);
    })();
  }, []);

  const setLocale = useCallback(async (next: Locale) => {
    setStoredLocale((key, value) => { void AsyncStorage.setItem(key, value); }, next, true);
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
