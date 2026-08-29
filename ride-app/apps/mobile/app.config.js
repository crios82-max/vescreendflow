export default ({ config }) => ({
  ...config,
  expo: {
    name: 'Movify',
    slug: 'movify',
    version: '1.0.0',
    orientation: 'portrait',
    icon: './assets/icon.png',
    userInterfaceStyle: 'dark',
    scheme: 'movify',
    splash: {
      image: './assets/splash-icon.png',
      resizeMode: 'contain',
      backgroundColor: '#0a0a0a',
    },
    ios: {
      supportsTablet: false,
      bundleIdentifier: 'com.movify.app',
      buildNumber: '1',
      config: {
        googleMapsApiKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_API_KEY ?? 'YOUR_IOS_GOOGLE_MAPS_KEY',
      },
      infoPlist: {
        NSLocationWhenInUseUsageDescription:
          'Movify usa tu ubicación para pedir viajes y mostrarte en el mapa.',
        ITSAppUsesNonExemptEncryption: false,
      },
      privacyManifests: {
        NSPrivacyAccessedAPITypes: [],
      },
    },
    android: {
      package: 'com.movify.app',
      versionCode: 1,
      adaptiveIcon: {
        foregroundImage: './assets/adaptive-icon.png',
        backgroundColor: '#0a0a0a',
      },
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
            'Movify necesita tu ubicación para pedir y completar viajes.',
        },
      ],
      [
        'expo-notifications',
        {
          color: '#A3E635',
        },
      ],
    ],
    extra: {
      apiUrl: process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:4001',
      googleMapsApiKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_API_KEY ?? '',
      passengerWebUrl: process.env.EXPO_PUBLIC_PASSENGER_WEB_URL ?? 'http://localhost:5174',
      eas: {
        projectId: process.env.EAS_PROJECT_ID ?? 'your-eas-project-id',
      },
    },
    owner: 'your-expo-account',
  },
});
