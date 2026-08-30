import { resolveLocaleFromPlace } from '@ride-app/shared';

/** Obtiene país vía GPS + Google Geocoding (si hay API key) o zona horaria */
export async function detectCountryCodeWeb(): Promise<string | null> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return null;
  }

  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const { latitude, longitude } = pos.coords;
        const key = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined;
        if (key) {
          try {
            const url = `https://maps.googleapis.com/maps/api/geocode/json?latlng=${latitude},${longitude}&key=${key}&result_type=country`;
            const res = await fetch(url);
            const data = await res.json() as {
              results?: Array<{ address_components?: Array<{ short_name: string; types: string[] }> }>;
            };
            const country = data.results?.[0]?.address_components?.find((c) => c.types.includes('country'))?.short_name;
            resolve(country ?? null);
            return;
          } catch {
            /* fallback below */
          }
        }
        resolve(null);
      },
      () => resolve(null),
      { enableHighAccuracy: false, timeout: 6000, maximumAge: 300_000 },
    );
  });
}

export async function detectLocaleWeb(): Promise<import('@ride-app/shared').Locale> {
  const languages = typeof navigator !== 'undefined'
    ? (navigator.languages?.length ? [...navigator.languages] : [navigator.language])
    : [];
  const timeZone = typeof Intl !== 'undefined' ? Intl.DateTimeFormat().resolvedOptions().timeZone : undefined;

  const countryCode = await detectCountryCodeWeb();

  return resolveLocaleFromPlace({
    countryCode,
    languages: languages.filter(Boolean),
    timeZone,
  });
}
