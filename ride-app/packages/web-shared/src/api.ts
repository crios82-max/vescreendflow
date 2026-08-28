import type { AuthResponse, Ride, RideEstimate, User } from '@ride-app/shared';

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:4001';

class ApiClient {
  private token: string | null = null;

  setToken(token: string | null) {
    this.token = token;
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(options.headers as Record<string, string>),
    };
    if (this.token) headers.Authorization = `Bearer ${this.token}`;

    const res = await fetch(`${API_URL}${path}`, { ...options, headers });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error ?? 'Error de red');
    return data as T;
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

  getMe() {
    return this.request<{ user: User }>('/users/me');
  }

  estimateRide(body: object) {
    return this.request<RideEstimate>('/rides/estimate', {
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

  updateRideStatus(id: string, status: string) {
    return this.request<Ride>(`/rides/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
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
      pickupLat: number;
      pickupLng: number;
      dropoffAddress: string;
      estimatedPrice: number;
      distanceKm: number;
      vehicleType: string;
    }> }>('/drivers/pending-rides');
  }

  payRide(id: string, body?: { tipAmount?: number; useWallet?: boolean }) {
    return this.request<{ payment: { id: string; amount: number; cardLast4: string; tipAmount?: number }; ride: Ride }>(
      `/rides/${id}/pay`,
      { method: 'POST', body: JSON.stringify(body ?? {}) },
    );
  }

  getRideEta(id: string) {
    return this.request<{ etaPickupMin: number | null; etaDropoffMin: number | null }>(`/rides/${id}/eta`);
  }

  shareRide(id: string) {
    return this.request<{ shareUrl: string; shareToken: string }>(`/rides/${id}/share`, { method: 'POST' });
  }

  getReceipt(id: string) {
    return this.request<{ ok: boolean }>(`/rides/${id}/receipt`);
  }

  validatePromo(code: string, subtotal: number) {
    return this.request<{ discount: number; code: string }>('/promos/validate', {
      method: 'POST',
      body: JSON.stringify({ code, subtotal }),
    });
  }

  getSavedPlaces() {
    return this.request<{ places: Array<{ id: string; label: string; name: string; address: string; lat: number; lng: number }> }>('/places');
  }

  savePlace(body: { label: string; name: string; address: string; lat: number; lng: number }) {
    return this.request('/places', { method: 'POST', body: JSON.stringify(body) });
  }

  getWalletBalance() {
    return this.request<{ balance: number }>('/wallet/balance');
  }

  topupWallet(amount: number) {
    return this.request<{ balance: number }>('/wallet/topup', { method: 'POST', body: JSON.stringify({ amount }) });
  }

  getChatMessages(rideId: string) {
    return this.request<{ messages: Array<{ id: string; senderName?: string; message: string; createdAt: string }> }>(`/chat/${rideId}`);
  }

  sendChatMessage(rideId: string, message: string) {
    return this.request(`/chat/${rideId}`, { method: 'POST', body: JSON.stringify({ message }) });
  }

  triggerSos(rideId: string, lat?: number, lng?: number) {
    return this.request('/sos', { method: 'POST', body: JSON.stringify({ rideId, lat, lng }) });
  }

  getDriverEarnings() {
    return this.request<{ totalEarnings: number; today: { total: number; rides: number }; week: { total: number; rides: number } }>('/onboarding/earnings');
  }

  getOnboardingStatus() {
    return this.request<{ approvalStatus: string; rejectionReason: string | null }>('/onboarding/status');
  }

  submitDriverDocs(body: { licenseUrl?: string; idUrl?: string; vehiclePhotoUrl?: string }) {
    return this.request('/onboarding/documents', { method: 'POST', body: JSON.stringify(body) });
  }

  getAdminStats() {
    return this.request<{ users: number; drivers: number; rides: number; revenue: number; pendingDrivers: number; sosLast24h: number }>('/admin/stats');
  }

  getPendingDrivers() {
    return this.request<{ drivers: Array<{ userId: string; name: string; email: string }> }>('/admin/drivers/pending');
  }

  approveDriver(userId: string) {
    return this.request(`/admin/drivers/${userId}/approve`, { method: 'POST' });
  }

  banUser(userId: string) {
    return this.request(`/admin/users/${userId}/ban`, { method: 'POST' });
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

  getAdminRides() {
    return this.request<{ rides: Array<Ride & { passengerName?: string; driverName?: string }> }>('/admin/rides');
  }

  getAdminUsers() {
    return this.request<{ users: Array<User & { isAdmin: boolean; createdAt: string }> }>('/admin/users');
  }

  registerPushToken(token: string, platform?: string) {
    return this.request<{ ok: boolean }>('/push/register', {
      method: 'POST',
      body: JSON.stringify({ token, platform }),
    });
  }
}

export const api = new ApiClient();
