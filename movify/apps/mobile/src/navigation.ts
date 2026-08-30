import { Linking, Platform } from 'react-native';

export function openTurnByTurnNavigation(
  destLat: number,
  destLng: number,
  label = 'Destino',
  inProgress = false,
) {
  const coords = `${destLat},${destLng}`;
  const url = Platform.select({
    ios: `maps://?daddr=${coords}&dirflg=d`,
    android: `google.navigation:q=${coords}&mode=d`,
    default: `https://www.google.com/maps/dir/?api=1&destination=${coords}&travelmode=driving`,
  })!;

  Linking.canOpenURL(url).then((supported) => {
    if (supported) {
      Linking.openURL(url);
      return;
    }
    Linking.openURL(
      `https://www.google.com/maps/dir/?api=1&destination=${coords}&travelmode=driving&destination_place_id=${encodeURIComponent(label)}`,
    );
  }).catch(() => {
    Linking.openURL(`https://www.google.com/maps/search/?api=1&query=${coords}`);
  });
}
