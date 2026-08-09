const API_URL = import.meta.env.VITE_API_URL || 'http://127.0.0.1:4000/api'

export type AuthSession = {
  token: string
  user: { id: string; email: string; name: string }
}

const TOKEN_KEY = 'screenflow_token'
const USER_KEY = 'screenflow_user'

export function saveSession(session: AuthSession) {
  localStorage.setItem(TOKEN_KEY, session.token)
  localStorage.setItem(USER_KEY, JSON.stringify(session.user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser(): AuthSession['user'] | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const res = await fetch(`${API_URL}${path}`, { ...options, headers })
  if (res.status === 204) return undefined as T

  const data = await res.json().catch(() => ({}))
  if (!res.ok) {
    throw new Error(data.error || `Request failed (${res.status})`)
  }
  return data as T
}

export const api = {
  signup: (body: { name: string; email: string; password: string }) =>
    request<AuthSession>('/auth/signup', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  login: (body: { email: string; password: string }) =>
    request<AuthSession>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  getScreens: () =>
    request<{
      screens: Array<{
        id: string
        name: string
        code: string
        status: 'online' | 'offline'
        playlist: string
        playlistId: string | null
        lastSeenAt?: string | null
        rotationDeg?: number
        daypartStart?: string | null
        daypartEnd?: string | null
      }>
    }>('/screens'),
  addScreen: (body: { code: string; name?: string; playlistId?: string }) =>
    request<{
      screen: {
        id: string
        name: string
        code: string
        status: 'online' | 'offline'
        playlist: string
        playlistId: string | null
        lastSeenAt?: string | null
        rotationDeg?: number
        daypartStart?: string | null
        daypartEnd?: string | null
      }
    }>('/screens', { method: 'POST', body: JSON.stringify(body) }),
  setScreenPlaylist: (
    screenId: string,
    playlistId: string | null,
    daypart?: { daypartStart?: string | null; daypartEnd?: string | null },
  ) =>
    request<{
      screen: {
        id: string
        playlist: string
        playlistId: string | null
        daypartStart?: string | null
        daypartEnd?: string | null
      }
    }>(`/screens/${screenId}/playlist`, {
      method: 'PUT',
      body: JSON.stringify({
        playlistId,
        daypartStart: daypart?.daypartStart ?? null,
        daypartEnd: daypart?.daypartEnd ?? null,
      }),
    }),
  updateScreen: (
    screenId: string,
    body: { name?: string; location?: string | null; rotationDeg?: 0 | 90 | 180 | 270 },
  ) =>
    request<{
      screen: {
        id: string
        name: string
        code: string
        status: 'online' | 'offline'
        location: string | null
        lastSeenAt?: string | null
        rotationDeg?: number
      }
    }>(`/screens/${screenId}`, {
      method: 'PATCH',
      body: JSON.stringify(body),
    }),
  deleteScreen: (screenId: string) =>
    request<void>(`/screens/${screenId}`, { method: 'DELETE' }),
  getPlaylists: () =>
    request<{
      playlists: Array<{
        id: string
        name: string
        items: number
        isActive: boolean
      }>
    }>('/playlists'),
  addPlaylist: (name: string) =>
    request<{
      playlist: { id: string; name: string; items: number; isActive: boolean }
    }>('/playlists', { method: 'POST', body: JSON.stringify({ name }) }),
  getPlaylistItems: (playlistId: string) =>
    request<{
      items: Array<{
        id: string
        mediaId: string
        name: string
        mediaType: 'image' | 'video'
        url: string
        durationSec: number
        sortOrder: number
      }>
    }>(`/playlists/${playlistId}/items`),
  addPlaylistItem: (playlistId: string, mediaId: string) =>
    request<{
      item: { id: string; mediaId: string; durationSec: number; sortOrder: number }
    }>(`/playlists/${playlistId}/items`, {
      method: 'POST',
      body: JSON.stringify({ mediaId }),
    }),
  removePlaylistItem: (playlistId: string, itemId: string) =>
    request<void>(`/playlists/${playlistId}/items/${itemId}`, { method: 'DELETE' }),
  getMedia: () =>
    request<{
      media: Array<{
        id: string
        name: string
        mediaType: 'image' | 'video'
        url: string
        durationSec: number
      }>
    }>('/media'),
  uploadMedia: async (file: File, playlistId?: string) => {
    const token = getToken()
    const body = new FormData()
    body.append('file', file)
    body.append('name', file.name)
    if (playlistId) body.append('playlistId', playlistId)
    let res: Response
    try {
      res = await fetch(`${API_URL}/media`, {
        method: 'POST',
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
        body,
      })
    } catch {
      throw new Error('No se pudo conectar con la API. Revisa la red e intenta de nuevo.')
    }
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.error || `Error al subir (${res.status})`)
    return data as {
      media: {
        id: string
        name: string
        mediaType: 'image' | 'video'
        url: string
        durationSec: number
      }
    }
  },
  addMediaToPlaylist: (mediaId: string, playlistId: string) =>
    request<{ ok: boolean }>(`/media/${mediaId}/add-to-playlist`, {
      method: 'POST',
      body: JSON.stringify({ playlistId }),
    }),
  deleteMedia: (id: string) =>
    request<void>(`/media/${id}`, { method: 'DELETE' }),
  getGroups: () =>
    request<{
      groups: Array<{
        id: string
        name: string
        mode: 'group' | 'videowall'
        rows: number
        cols: number
        playlistId: string | null
        playlistName?: string | null
        cycleEpoch?: string
        members: Array<{
          screenId: string
          screenName: string
          code: string
          row: number
          col: number
        }>
      }>
    }>('/groups'),
  createGroup: (body: {
    name: string
    mode?: 'group' | 'videowall'
    rows?: number
    cols?: number
    playlistId?: string | null
  }) =>
    request<{
      group: {
        id: string
        name: string
        mode: 'group' | 'videowall'
        rows: number
        cols: number
        playlistId: string | null
        members: []
      }
    }>('/groups', { method: 'POST', body: JSON.stringify(body) }),
  updateGroup: (
    id: string,
    body: {
      name?: string
      mode?: 'group' | 'videowall'
      rows?: number
      cols?: number
      playlistId?: string | null
      resetCycle?: boolean
    },
  ) =>
    request<{
      group: {
        id: string
        name: string
        mode: 'group' | 'videowall'
        rows: number
        cols: number
        playlistId: string | null
      }
    }>(`/groups/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  setGroupMembers: (
    id: string,
    members: Array<{ screenId: string; row: number; col: number }>,
  ) =>
    request<{ ok: boolean; count: number }>(`/groups/${id}/members`, {
      method: 'PUT',
      body: JSON.stringify({ members }),
    }),
  deleteGroup: (id: string) =>
    request<void>(`/groups/${id}`, { method: 'DELETE' }),
}
