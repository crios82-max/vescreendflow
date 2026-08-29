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

  payRide(id: string, body?: { tipAmount?: number; useWallet?: boolean; paymentIntentId?: string }) {
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

  getConnectStatus() {
    return this.request<{ connected: boolean; onboarded: boolean; chargesEnabled: boolean }>('/connect/status');
  }

  startConnectOnboarding() {
    return this.request<{ url?: string; mock?: boolean; message?: string }>('/connect/onboard', { method: 'POST' });
  }

  getPhoneVerifyStatus() {
    return this.request<{ phone: string | null; verified: boolean }>('/verify/phone/status');
  }

  sendPhoneOtp(phone: string) {
    return this.request<{ sent: boolean; mock?: boolean; devHint?: string }>('/verify/phone/send', {
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

  splitFare(rideId: string, emails: string[]) {
    return this.request<{ perPerson: number; yourShare: number; participants: number; invites?: Array<{ email: string; payUrl: string }> }>(
      `/split/ride/${rideId}`,
      { method: 'POST', body: JSON.stringify({ emails }) },
    );
  }

  getPaymentMethods() {
    return this.request<{ methods: Array<{ id: string; brand: string; last4: string }> }>('/payments/methods');
  }

  createSetupIntent() {
    return this.request<{ clientSecret?: string; mock?: boolean }>('/payments/setup-intent', { method: 'POST' });
  }

  deletePaymentMethod(id: string) {
    return this.request(`/payments/methods/${id}`, { method: 'DELETE' });
  }

  getShareTrip(token: string) {
    return this.request<{
      ride: { status: string; pickupAddress: string; dropoffAddress: string; etaPickupMin: number | null };
      driverLocation: { lat: number; lng: number } | null;
    }>(`/share/${token}`);
  }

  createPaymentIntent(rideId: string, tipAmount = 0) {
    return this.request<{ clientSecret?: string; mock?: boolean; amount?: number }>(
      `/rides/${rideId}/payment-intent`,
      { method: 'POST', body: JSON.stringify({ tipAmount }) },
    );
  }

  getAdminStats() {
    return this.request<{ users: number; drivers: number; rides: number; revenue: number; pendingDrivers: number; sosLast24h: number }>('/admin/stats');
  }

  getAdminRides() {
    return this.request<{ rides: Array<Ride & { passengerName?: string; driverName?: string }> }>('/admin/rides');
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

  getAdminUsers() {
    return this.request<{ users: Array<User & { isAdmin: boolean; banned?: boolean; createdAt: string }> }>('/admin/users');
  }

  rejectDriver(userId: string, reason?: string) {
    return this.request(`/admin/drivers/${userId}/reject`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  }

  unbanUser(userId: string) {
    return this.request(`/admin/users/${userId}/unban`, { method: 'POST' });
  }

  refundRide(rideId: string) {
    return this.request<{ ok: boolean }>(`/admin/rides/${rideId}/refund`, { method: 'POST' });
  }

  getAdminSos() {
    return this.request<{ events: Array<{ id: string; user_id: string; ride_id: string; name: string; pickup_address: string; lat: number | null; lng: number | null; created_at: string }> }>('/admin/sos');
  }

  getAdminPromos() {
    return this.request<{ promos: Array<{ code: string; discount_type: string; discount_value: number; max_uses: number | null; uses_count: number; active: boolean }> }>('/admin/promos');
  }

  createPromo(body: { code: string; discountType: 'percent' | 'fixed'; discountValue: number; maxUses?: number }) {
    return this.request('/admin/promos', { method: 'POST', body: JSON.stringify(body) });
  }

  getPendingDrivers() {
    return this.request<{ drivers: Array<{ userId: string; name: string; email: string; vehicleType?: string; licenseUrl?: string; idUrl?: string; vehiclePhotoUrl?: string }> }>('/admin/drivers/pending');
  }

  getRideContact(rideId: string) {
    return this.request<{ name: string; mode: string; masked: boolean; dialUrl?: string; hint?: string }>(`/contact/rides/${rideId}`);
  }

  initiateMaskedCall(rideId: string) {
    return this.request<{ initiated?: boolean; masked?: boolean; message?: string; mock?: boolean; dialUrl?: string; hint?: string }>(
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

  resetPassword(token: string, password: string) {
    return this.request<{ ok: boolean }>('/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, password }),
    });
  }

  registerPushToken(token: string, platform?: string) {
    return this.request<{ ok: boolean }>('/push/register', {
      method: 'POST',
      body: JSON.stringify({ token, platform }),
    });
  }

  setPreferredLocale(locale: string) {
    return this.request<{ ok: boolean }>('/users/me/locale', {
      method: 'PATCH',
      body: JSON.stringify({ locale }),
    });
  }
}

export const api = new ApiClient();
