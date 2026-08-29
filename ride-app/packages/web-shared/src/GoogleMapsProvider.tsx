import { APIProvider } from '@vis.gl/react-google-maps';
import type { ReactNode } from 'react';
import { useI18n } from './I18nProvider';

/** When Maps key is missing, still render app chrome so UI is usable for demos/screenshots. */
function MapsFallbackShell({ children }: { children: ReactNode }) {
  const { t } = useI18n();
  return (
    <div style={{ position: 'relative', minHeight: '100vh', background: '#0a0a0a' }}>
      <div
        className="map-canvas"
        style={{
          position: 'absolute',
          inset: 0,
          display: 'grid',
          placeItems: 'center',
          background: 'radial-gradient(ellipse at 30% 20%, #1a2a1a 0%, #0a0a0a 55%)',
          pointerEvents: 'none',
          zIndex: 0,
        }}
        aria-hidden
      >
        <p className="muted-text" style={{ opacity: 0.5 }}>{t('common.mapsKeyMissing')}</p>
      </div>
      <div style={{ position: 'relative', zIndex: 1 }}>{children}</div>
    </div>
  );
}

export function GoogleMapsProvider({ children }: { children: ReactNode }) {
  const apiKey = (import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined)?.trim();

  if (!apiKey) {
    return <MapsFallbackShell>{children}</MapsFallbackShell>;
  }

  return (
    <APIProvider apiKey={apiKey} libraries={['places']}>
      {children}
    </APIProvider>
  );
}
