export default ({ config }) => ({
  ...config,
  expo: {
    name: 'Ride',
    slug: 'ride-app',
    version: '1.0.0',
    orientation: 'portrait',
    userInterfaceStyle: 'dark',
    scheme: 'rideapp',
    ios: {
      supportsTablet: false,
      bundleIdentifier: 'com.rideapp.mobile',
      buildNumber: '1',
      config: {
        googleMapsApiKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_API_KEY ?? 'YOUR_IOS_GOOGLE_MAPS_KEY',
      },
      infoPlist: {
        NSLocationWhenInUseUsageDescription:
          'Ride usa tu ubicación para pedir viajes y mostrarte en el mapa.',
        ITSAppUsesNonExemptEncryption: false,
      },
      privacyManifests: {
        NSPrivacyAccessedAPITypes: [],
      },
    },
    android: {
      package: 'com.rideapp.mobile',
      versionCode: 1,
      permissions: ['ACCESS_FINE_LOCATION', 'ACCESS_COARSE_LOCATION'],
      config: {
        googleMaps: {
          apiKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_API_KEY ?? 'YOUR_ANDROID_GOOGLE_MAPS_KEY',
        },
      },
    },
    plugins: [
      [
        'expo-location',
        {
          locationAlwaysAndWhenInUsePermission:
            'Ride necesita tu ubicación para pedir y completar viajes.',
        },
      ],
      [
        'expo-notifications',
        {
          color: '#ffffff',
        },
      ],
    ],
    extra: {
      apiUrl: process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:4001',
      googleMapsApiKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_API_KEY ?? '',
      eas: {
        projectId: process.env.EAS_PROJECT_ID ?? 'your-eas-project-id',
      },
    },
    owner: 'your-expo-account',
  },
});
