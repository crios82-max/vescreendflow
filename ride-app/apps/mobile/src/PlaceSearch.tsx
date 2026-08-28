import { StyleSheet, View } from 'react-native';
import { GooglePlacesAutocomplete } from 'react-native-google-places-autocomplete';
import { googleMapsKey } from './storage';
import { colors, placeholderColor } from './theme';

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
          placeholderTextColor: placeholderColor,
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
    backgroundColor: 'rgba(10,10,10,0.95)',
    color: colors.text,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    fontSize: 16,
    height: 48,
  },
  list: {
    backgroundColor: colors.surfaceRaised,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: colors.borderStrong,
    marginTop: 4,
    maxHeight: 180,
  },
  row: { backgroundColor: colors.surfaceRaised, padding: 12 },
  description: { color: colors.textMuted },
  separator: { backgroundColor: colors.border, height: 1 },
});
