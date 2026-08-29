import { Map, Marker, useMap } from '@vis.gl/react-google-maps';
import { useEffect, useMemo } from 'react';
import { decodePolyline } from './polyline.js';
import { hasMapsApiKey } from './mapsConfig.js';

const DEFAULT_CENTER = { lat: 10.4806, lng: -66.9036 };

interface MapViewProps {
  pickup?: { lat: number; lng: number } | null;
  dropoff?: { lat: number; lng: number } | null;
  driver?: { lat: number; lng: number } | null;
  routePolyline?: string | null;
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

function RoutePolyline({ encoded }: { encoded: string }) {
  const map = useMap();
  const path = useMemo(() => decodePolyline(encoded), [encoded]);

  useEffect(() => {
    if (!map || path.length === 0) return;
    const line = new google.maps.Polyline({
      path,
      strokeColor: '#A3E635',
      strokeWeight: 5,
      strokeOpacity: 0.85,
    });
    line.setMap(map);
    return () => line.setMap(null);
  }, [map, path]);

  return null;
}

export function MapView({ pickup, dropoff, driver, routePolyline, onMapClick, follow }: MapViewProps) {
  const center = pickup ?? follow ?? DEFAULT_CENTER;

  if (!hasMapsApiKey()) {
    return (
      <div
        className="map-canvas"
        style={{
          background: 'radial-gradient(ellipse at 35% 25%, #1a2e1a 0%, #0a0a0a 60%)',
          minHeight: '100%',
          position: 'relative',
        }}
      >
        {pickup && (
          <span style={{ position: 'absolute', left: '30%', top: '40%', color: '#fff', fontWeight: 700 }}>A</span>
        )}
        {dropoff && (
          <span style={{ position: 'absolute', left: '60%', top: '55%', color: '#A3E635', fontWeight: 700 }}>B</span>
        )}
        {driver && (
          <span style={{ position: 'absolute', left: '45%', top: '48%' }}>🚗</span>
        )}
      </div>
    );
  }

  return (
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
      {routePolyline && <RoutePolyline encoded={routePolyline} />}
      {pickup && <Marker position={pickup} label="A" />}
      {dropoff && <Marker position={dropoff} label="B" />}
      {driver && <Marker position={driver} label="🚗" />}
    </Map>
  );
}
