import { APIProvider, Map, Marker, useMap } from '@vis.gl/react-google-maps';
import { useEffect } from 'react';

const DEFAULT_CENTER = { lat: 10.4806, lng: -66.9036 }; // Caracas

interface MapViewProps {
  pickup?: { lat: number; lng: number } | null;
  dropoff?: { lat: number; lng: number } | null;
  driver?: { lat: number; lng: number } | null;
  onMapClick?: (lat: number, lng: number) => void;
  follow?: { lat: number; lng: number } | null;
}

function MapController({ follow }: { follow?: { lat: number; lng: number } | null }) {
  const map = useMap();
  useEffect(() => {
    if (map && follow) {
      map.panTo(follow);
    }
  }, [map, follow?.lat, follow?.lng]);
  return null;
}

function ClickHandler({ onMapClick }: { onMapClick?: (lat: number, lng: number) => void }) {
  const map = useMap();
  useEffect(() => {
    if (!map || !onMapClick) return;
    const listener = map.addListener('click', (e: google.maps.MapMouseEvent) => {
      if (e.latLng) onMapClick(e.latLng.lat(), e.latLng.lng());
    });
    return () => listener.remove();
  }, [map, onMapClick]);
  return null;
}

export function MapView({ pickup, dropoff, driver, onMapClick, follow }: MapViewProps) {
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
  const center = pickup ?? follow ?? DEFAULT_CENTER;

  if (!apiKey) {
    return (
      <div className="map-canvas" style={{ display: 'grid', placeItems: 'center', background: '#1a1a1a' }}>
        <p>Configura VITE_GOOGLE_MAPS_API_KEY en .env</p>
      </div>
    );
  }

  return (
    <APIProvider apiKey={apiKey}>
      <Map
        className="map-canvas"
        defaultCenter={center}
        defaultZoom={14}
        gestureHandling="greedy"
        disableDefaultUI
        colorScheme="DARK"
      >
        <MapController follow={follow} />
        <ClickHandler onMapClick={onMapClick} />
        {pickup && <Marker position={pickup} label="A" />}
        {dropoff && <Marker position={dropoff} label="B" />}
        {driver && <Marker position={driver} label="🚗" />}
      </Map>
    </APIProvider>
  );
}
