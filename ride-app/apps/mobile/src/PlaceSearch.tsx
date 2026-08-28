import { StyleSheet, View } from 'react-native';
import { GooglePlacesAutocomplete } from 'react-native-google-places-autocomplete';
import { googleMapsKey } from './storage';

export interface PlaceResult {
  latitude: number;
  longitude: number;
  address: string;
}

interface Props {
  placeholder: string;
  bias?: { latitude: number; longitude: number } | null;
  onSelect: (place: PlaceResult) => void;
}

export function PlaceSearch({ placeholder, bias, onSelect }: Props) {
  const API_KEY = googleMapsKey();

  if (!API_KEY) {
    return null;
  }

  return (
    <View style={styles.wrapper}>
      <GooglePlacesAutocomplete
        placeholder={placeholder}
        fetchDetails
        enablePoweredByContainer={false}
        minLength={2}
        debounce={300}
        onPress={(_data, details) => {
          if (!details?.geometry?.location) return;
          onSelect({
            latitude: details.geometry.location.lat,
            longitude: details.geometry.location.lng,
            address: details.formatted_address ?? _data.description,
          });
        }}
        query={{
          key: API_KEY,
          language: 'es',
          ...(bias
            ? { location: `${bias.latitude},${bias.longitude}`, radius: 20000 }
            : {}),
        }}
        styles={{
          container: styles.container,
          textInput: styles.input,
          listView: styles.list,
          row: styles.row,
          description: styles.description,
          separator: styles.separator,
        }}
        textInputProps={{
          placeholderTextColor: '#888',
          returnKeyType: 'search',
        }}
        keyboardShouldPersistTaps="handled"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { zIndex: 10, marginBottom: 4 },
  container: { flex: 0 },
  input: {
    backgroundColor: 'rgba(17,17,17,0.95)',
    color: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#333',
    fontSize: 16,
    height: 48,
  },
  list: {
    backgroundColor: '#111',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#333',
    marginTop: 4,
    maxHeight: 180,
  },
  row: { backgroundColor: '#111', padding: 12 },
  description: { color: '#ddd' },
  separator: { backgroundColor: '#222', height: 1 },
});
