import { useEffect, useState } from 'react';
import { api } from './api';
import { useI18n } from './I18nProvider';

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
  const { t, te } = useI18n();
  const [places, setPlaces] = useState<Place[]>([]);
  const [error, setError] = useState('');

  const load = () =>
    api.getSavedPlaces()
      .then((r) => {
        setPlaces(r.places);
        setError('');
      })
      .catch((err) => setError(te(err instanceof Error ? err.message : t('common.loadFailed'))));

  useEffect(() => {
    load();
  }, []);

  const saveCurrent = async (label: string) => {
    if (!currentDropoff) return;
    setError('');
    try {
      await api.savePlace({
        label,
        name: label,
        address: currentDropoff.address,
        lat: currentDropoff.lat,
        lng: currentDropoff.lng,
      });
      await load();
    } catch (err) {
      setError(te(err instanceof Error ? err.message : t('common.error')));
    }
  };

  return (
    <div className="saved-places">
      {error && <p className="error-text">{error}</p>}
      <div className="tab-row">
        {places.map((p) => (
          <button key={p.id} type="button" className="tab-btn" onClick={() => onSelect({ lat: p.lat, lng: p.lng, address: p.address })}>
            {p.label}
          </button>
        ))}
        {currentDropoff && (
          <>
            <button type="button" className="tab-btn" onClick={() => saveCurrent(t('places.home'))}>{t('places.addHome')}</button>
            <button type="button" className="tab-btn" onClick={() => saveCurrent(t('places.work'))}>{t('places.addWork')}</button>
          </>
        )}
      </div>
    </div>
  );
}
