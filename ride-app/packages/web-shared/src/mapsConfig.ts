/** True when a Google Maps JS API key is configured for Vite. */
export function hasMapsApiKey(): boolean {
  return Boolean((import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined)?.trim());
}
