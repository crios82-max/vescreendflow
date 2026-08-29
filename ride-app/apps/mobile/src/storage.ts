import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import Constants from 'expo-constants';

const API_URL_KEY = 'ride_api_url';
const TOKEN_KEY = 'ride_auth_token';

export function defaultApiUrl(): string {
  return (
    Constants.expoConfig?.extra?.apiUrl ??
    process.env.EXPO_PUBLIC_API_URL ??
    'http://localhost:4001'
  );
}

export async function getApiUrl(): Promise<string> {
  const saved = await AsyncStorage.getItem(API_URL_KEY);
  return saved?.trim() || defaultApiUrl();
}

export async function setApiUrl(url: string): Promise<void> {
  await AsyncStorage.setItem(API_URL_KEY, url.trim());
}

export async function getToken(): Promise<string | null> {
  return SecureStore.getItemAsync(TOKEN_KEY);
}

export async function setToken(token: string | null): Promise<void> {
  if (token) await SecureStore.setItemAsync(TOKEN_KEY, token);
  else await SecureStore.deleteItemAsync(TOKEN_KEY);
}

export function googleMapsKey(): string {
  return Constants.expoConfig?.extra?.googleMapsApiKey ?? process.env.EXPO_PUBLIC_GOOGLE_MAPS_API_KEY ?? '';
}

export function passengerWebUrl(): string {
  return (
    Constants.expoConfig?.extra?.passengerWebUrl ??
    process.env.EXPO_PUBLIC_PASSENGER_WEB_URL ??
    'http://localhost:5174'
  );
}
