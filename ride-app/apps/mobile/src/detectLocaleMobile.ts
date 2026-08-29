import * as Location from 'expo-location';
import { resolveLocaleFromPlace } from '@ride-app/shared';

export async function detectCountryCodeMobile(): Promise<string | null> {
  try {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') return null;

    const position = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });

    const [place] = await Location.reverseGeocodeAsync({
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
    });

    return place?.isoCountryCode ?? null;
  } catch {
    return null;
  }
}

export async function detectLocaleMobile(): Promise<ReturnType<typeof resolveLocaleFromPlace>> {
  const countryCode = await detectCountryCodeMobile();
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  return resolveLocaleFromPlace({
    countryCode,
    timeZone,
    languages: typeof navigator !== 'undefined' ? [...(navigator.languages ?? [])] : [],
  });
}
