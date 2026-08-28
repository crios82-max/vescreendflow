import type { AuthResponse, Ride, RideEstimate, User, VehicleType } from '@ride-app/shared';
import { getApiUrl, getToken, setToken as persistToken } from './storage';

class MobileApi {
  private token: string | null = null;
  private baseUrl = '';

  async init() {
    this.baseUrl = await getApiUrl();
    this.token = await getToken();
  }

  async setApiUrl(url: string) {
    const { setApiUrl } = await import('./storage');
    await setApiUrl(url);
    this.baseUrl = url.trim();
  }

  getBaseUrl() {
    return this.baseUrl;
  }

  setToken(token: string | null) {
    this.token = token;
    persistToken(token).catch(() => {});
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    if (!this.baseUrl) await this.init();
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };
    if (this.token) headers.Authorization = `Bearer ${this.token}`;

    const res = await fetch(`${this.baseUrl}${path}`, { ...options, headers });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error ?? `Error ${res.status}`);
    return data as T;
  }

  async health(): Promise<{ ok: boolean }> {
    return this.request('/health');
  }

  login(email: string, password: string) {
    return this.request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  }

  register(body: Record<string, string>) {
    return this.request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  createRide(body: object) {
    return this.request<Ride>('/rides', { method: 'POST', body: JSON.stringify(body) });
  }

  getActiveRide() {
    return this.request<{ ride: Ride | null }>('/rides/active');
  }

  estimateRide(body: object) {
    return this.request<RideEstimate>('/rides/estimate', {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  updateRideStatus(id: string, status: string) {
    return this.request<Ride>(`/rides/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    });
  }

  payRide(id: string, tipAmount = 0) {
    return this.request<{ ride: Ride }>(`/rides/${id}/pay`, {
      method: 'POST',
      body: JSON.stringify({ tipAmount }),
    });
  }

  acceptRide(id: string) {
    return this.request<Ride>(`/rides/${id}/accept`, { method: 'POST' });
  }

  goOnline(lat: number, lng: number) {
    return this.request('/drivers/online', {
      method: 'POST',
      body: JSON.stringify({ lat, lng }),
    });
  }

  goOffline() {
    return this.request('/drivers/offline', { method: 'POST' });
  }

  sendLocation(lat: number, lng: number) {
    return this.request('/drivers/location', {
      method: 'POST',
      body: JSON.stringify({ lat, lng }),
    });
  }

  getPendingRides() {
    return this.request<{ rides: Array<{
      id: string;
      pickupAddress: string;
      estimatedPrice: number;
      distanceKm: number;
      vehicleType: string;
    }> }>('/drivers/pending-rides');
  }

  getHistory() {
    return this.request<{ rides: Ride[] }>('/rides/history');
  }

  rateRide(id: string, stars: number, comment?: string) {
    return this.request(`/rides/${id}/rate`, {
      method: 'POST',
      body: JSON.stringify({ stars, comment }),
    });
  }

  registerPushToken(token: string, platform?: string) {
    return this.request<{ ok: boolean }>('/push/register', {
      method: 'POST',
      body: JSON.stringify({ token, platform }),
    });
  }

  getRideEta(id: string) {
    return this.request<{ etaPickupMin: number | null; etaDropoffMin: number | null }>(`/rides/${id}/eta`);
  }

  shareRide(id: string) {
    return this.request<{ shareUrl: string; shareToken: string }>(`/rides/${id}/share`, { method: 'POST' });
  }

  triggerSos(rideId: string, lat?: number, lng?: number) {
    return this.request('/sos', { method: 'POST', body: JSON.stringify({ rideId, lat, lng }) });
  }

  getConnectStatus() {
    return this.request<{ onboarded: boolean }>('/connect/status');
  }

  startConnectOnboarding() {
    return this.request<{ url?: string; message?: string }>('/connect/onboard', { method: 'POST' });
  }

  sendPhoneOtp(phone: string) {
    return this.request<{ sent: boolean; devHint?: string }>('/verify/phone/send', {
      method: 'POST',
      body: JSON.stringify({ phone }),
    });
  }

  confirmPhoneOtp(phone: string, code: string) {
    return this.request<{ verified: boolean }>('/verify/phone/confirm', {
      method: 'POST',
      body: JSON.stringify({ phone, code }),
    });
  }

  getPhoneVerifyStatus() {
    return this.request<{ phone: string | null; verified: boolean }>('/verify/phone/status');
  }
}

export const mobileApi = new MobileApi();
export type { User, Ride };
