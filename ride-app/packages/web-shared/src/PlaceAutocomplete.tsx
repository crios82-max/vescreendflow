import { useEffect, useRef, useState } from 'react';
import { useMapsLibrary } from '@vis.gl/react-google-maps';
import { hasMapsApiKey } from './mapsConfig.js';

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

function PlainPlaceInput({
  label,
  placeholder,
  defaultValue,
  bias,
  onSelect,
}: PlaceAutocompleteProps) {
  const [value, setValue] = useState(defaultValue ?? '');
  return (
    <label className="place-field">
      <span>{label}</span>
      <input
        className="place-input"
        type="text"
        placeholder={placeholder}
        value={value}
        autoComplete="off"
        onChange={(e) => setValue(e.target.value)}
        onBlur={() => {
          const address = value.trim();
          if (!address) return;
          onSelect({
            lat: bias?.lat ?? 10.4806,
            lng: bias?.lng ?? -66.9036,
            address,
          });
        }}
        onKeyDown={(e) => {
          if (e.key !== 'Enter') return;
          e.preventDefault();
          const address = value.trim();
          if (!address) return;
          onSelect({
            lat: bias?.lat ?? 10.4806,
            lng: bias?.lng ?? -66.9036,
            address,
          });
        }}
      />
    </label>
  );
}

function GooglePlaceInput(props: PlaceAutocompleteProps) {
  const { label, placeholder, defaultValue, bias, onSelect } = props;
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

export function PlaceAutocomplete(props: PlaceAutocompleteProps) {
  if (!hasMapsApiKey()) {
    return <PlainPlaceInput {...props} />;
  }
  return <GooglePlaceInput {...props} />;
}
