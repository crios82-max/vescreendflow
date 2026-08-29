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
    if (!res.ok) throw new Error(data.errorCode ?? data.error ?? `Error ${res.status}`);
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

  payRideOptions(id: string, body: { tipAmount?: number; useWallet?: boolean; paymentIntentId?: string }) {
    return this.request<{ ride: Ride }>(`/rides/${id}/pay`, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  createPaymentIntent(rideId: string, tipAmount = 0) {
    return this.request<{ clientSecret?: string; mock?: boolean; amount?: number; paymentIntentId?: string }>(
      `/rides/${rideId}/payment-intent`,
      { method: 'POST', body: JSON.stringify({ tipAmount }) },
    );
  }

  getSavedPlaces() {
    return this.request<{ places: Array<{ id: string; label: string; name: string; address: string; lat: number; lng: number }> }>('/places');
  }

  savePlace(body: { label: string; name: string; address: string; lat: number; lng: number }) {
    return this.request('/places', { method: 'POST', body: JSON.stringify(body) });
  }

  splitFare(rideId: string, emails: string[]) {
    return this.request<{ perPerson: number; yourShare: number; participants: number; invites?: Array<{ email: string; payUrl: string }> }>(
      `/split/ride/${rideId}`,
      { method: 'POST', body: JSON.stringify({ emails }) },
    );
  }

  getMe() {
    return this.request<{ user: User }>('/users/me');
  }

  getDriverEarnings() {
    return this.request<{ totalEarnings: number; today: { total: number; rides: number } }>('/onboarding/earnings');
  }

  submitDriverDocs(body: { licenseUrl?: string; idUrl?: string; vehiclePhotoUrl?: string }) {
    return this.request('/onboarding/documents', { method: 'POST', body: JSON.stringify(body) });
  }

  getOnboardingStatus() {
    return this.request<{ approvalStatus: string; rejectionReason: string | null }>('/onboarding/status');
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
    return this.request<{ url?: string; message?: string; errorCode?: string }>('/connect/onboard', { method: 'POST' });
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

  getChatMessages(rideId: string) {
    return this.request<{ messages: Array<{ id: string; senderName?: string; message: string }> }>(`/chat/${rideId}`);
  }

  sendChatMessage(rideId: string, message: string) {
    return this.request(`/chat/${rideId}`, { method: 'POST', body: JSON.stringify({ message }) });
  }

  validatePromo(code: string, subtotal: number) {
    return this.request<{ valid: boolean; discount: number; code?: string }>('/promos/validate', {
      method: 'POST',
      body: JSON.stringify({ code, subtotal }),
    });
  }

  getWalletBalance() {
    return this.request<{ balance: number }>('/wallet/balance');
  }

  topupWallet(amount: number) {
    return this.request<{ balance: number }>('/wallet/topup', {
      method: 'POST',
      body: JSON.stringify({ amount }),
    });
  }

  getRideContact(rideId: string) {
    return this.request<{ name: string; mode: string; dialUrl?: string; hint?: string }>(`/contact/rides/${rideId}`);
  }

  initiateMaskedCall(rideId: string) {
    return this.request<{ initiated?: boolean; message?: string; dialUrl?: string; hint?: string }>(
      `/contact/rides/${rideId}/call`,
      { method: 'POST' },
    );
  }

  forgotPassword(email: string) {
    return this.request<{ ok: boolean; devResetUrl?: string }>('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    });
  }

  setPreferredLocale(locale: string) {
    return this.request<{ ok: boolean }>('/users/me/locale', {
      method: 'PATCH',
      body: JSON.stringify({ locale }),
    });
  }
}

export const mobileApi = new MobileApi();
export type { User, Ride };
