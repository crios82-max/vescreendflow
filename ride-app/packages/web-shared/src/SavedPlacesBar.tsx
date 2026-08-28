import { useEffect, useState } from 'react';
import { api } from './api';

interface Place {
  id: string;
  label: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
}

interface Props {
  onSelect: (place: { lat: number; lng: number; address: string }) => void;
  currentDropoff?: { lat: number; lng: number; address: string } | null;
}

export function SavedPlacesBar({ onSelect, currentDropoff }: Props) {
  const [places, setPlaces] = useState<Place[]>([]);

  useEffect(() => {
    api.getSavedPlaces().then((r) => setPlaces(r.places)).catch(() => {});
  }, []);

  const saveCurrent = async (label: string) => {
    if (!currentDropoff) return;
    await api.savePlace({
      label,
      name: label,
      address: currentDropoff.address,
      lat: currentDropoff.lat,
      lng: currentDropoff.lng,
    });
    const updated = await api.getSavedPlaces();
    setPlaces(updated.places);
  };

  return (
    <div className="saved-places">
      <div className="tab-row">
        {places.map((p) => (
          <button key={p.id} type="button" className="tab-btn" onClick={() => onSelect({ lat: p.lat, lng: p.lng, address: p.address })}>
            {p.label}
          </button>
        ))}
        {currentDropoff && (
          <>
            <button type="button" className="tab-btn" onClick={() => saveCurrent('Casa')}>+ Casa</button>
            <button type="button" className="tab-btn" onClick={() => saveCurrent('Trabajo')}>+ Trabajo</button>
          </>
        )}
      </div>
    </div>
  );
}
