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

  payRide(id: string) {
    return this.request<{ payment: { id: string; amount: number; cardLast4: string }; ride: Ride }>(
      `/rides/${id}/pay`,
      { method: 'POST' },
    );
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

  getAdminStats() {
    return this.request<{ users: number; drivers: number; rides: number; revenue: number }>('/admin/stats');
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
