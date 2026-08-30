import { APIProvider } from '@vis.gl/react-google-maps';
import type { ReactNode } from 'react';
import { useI18n } from './I18nProvider';

function MapsMissingKey() {
  const { t } = useI18n();
  return (
    <div
      className="map-canvas"
      style={{ display: 'grid', placeItems: 'center', background: '#1a1a1a', minHeight: '100vh' }}
    >
      <p>{t('common.mapsKeyMissing')}</p>
    </div>
  );
}

export function GoogleMapsProvider({ children }: { children: ReactNode }) {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

  if (!apiKey) {
    return <MapsMissingKey />;
  }

  return (
    <APIProvider apiKey={apiKey} libraries={['places']}>
      {children}
    </APIProvider>
  );
}
