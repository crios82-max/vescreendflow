import { useEffect, useRef } from 'react';
import { useMapsLibrary } from '@vis.gl/react-google-maps';

export interface PlaceResult {
  lat: number;
  lng: number;
  address: string;
}

interface PlaceAutocompleteProps {
  label: string;
  placeholder?: string;
  defaultValue?: string;
  bias?: { lat: number; lng: number } | null;
  onSelect: (place: PlaceResult) => void;
}

export function PlaceAutocomplete({
  label,
  placeholder,
  defaultValue,
  bias,
  onSelect,
}: PlaceAutocompleteProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const places = useMapsLibrary('places');

  useEffect(() => {
    if (!places || !inputRef.current) return;

    const options: google.maps.places.AutocompleteOptions = {
      fields: ['geometry', 'formatted_address', 'name'],
    };

    if (bias) {
      const delta = 0.15;
      options.bounds = new google.maps.LatLngBounds(
        { lat: bias.lat - delta, lng: bias.lng - delta },
        { lat: bias.lat + delta, lng: bias.lng + delta },
      );
      options.strictBounds = false;
    }

    const autocomplete = new places.Autocomplete(inputRef.current, options);

    const listener = autocomplete.addListener('place_changed', () => {
      const place = autocomplete.getPlace();
      const loc = place.geometry?.location;
      if (!loc) return;

      onSelect({
        lat: loc.lat(),
        lng: loc.lng(),
        address: place.formatted_address ?? place.name ?? '',
      });
    });

    return () => {
      google.maps.event.removeListener(listener);
    };
  }, [places, bias?.lat, bias?.lng, onSelect]);

  return (
    <label className="place-field">
      <span>{label}</span>
      <input
        ref={inputRef}
        className="place-input"
        type="text"
        placeholder={placeholder}
        defaultValue={defaultValue}
        autoComplete="off"
      />
    </label>
  );
}
