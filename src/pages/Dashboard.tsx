import { useEffect, useRef, useState } from 'react'
import type { DragEvent, FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, clearSession, getUser } from '../lib/api'
import './Dashboard.css'

type Screen = {
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

type Playlist = {
  id: string
  name: string
  items: number
  isActive?: boolean
}

type MediaItem = {
  id: string
  name: string
  mediaType: 'image' | 'video'
  url: string
  durationSec: number
}

type PlaylistItem = {
  id: string
  mediaId: string
  name: string
  mediaType: 'image' | 'video'
  url: string
  durationSec: number
  sortOrder: number
}

type ScreenGroup = {
  id: string
  name: string
  mode: 'group' | 'videowall'
  rows: number
  cols: number
  playlistId: string | null
  playlistName?: string | null
  members: Array<{
    screenId: string
    screenName: string
    code: string
    row: number
    col: number
  }>
}

export function Dashboard() {
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [user] = useState(() => getUser())
  const [tab, setTab] = useState<'screens' | 'playlists' | 'content' | 'groups'>('screens')
  const [screens, setScreens] = useState<Screen[]>([])
  const [playlists, setPlaylists] = useState<Playlist[]>([])
  const [media, setMedia] = useState<MediaItem[]>([])
  const [groups, setGroups] = useState<ScreenGroup[]>([])
  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [pairCode, setPairCode] = useState('')
  const [pairPlaylistId, setPairPlaylistId] = useState('')
  const [uploadPlaylistId, setUploadPlaylistId] = useState('')
  const [selectedPlaylistId, setSelectedPlaylistId] = useState('')
  const [playlistItems, setPlaylistItems] = useState<PlaylistItem[]>([])
  const [playlistBusy, setPlaylistBusy] = useState(false)
  const [savingScreenId, setSavingScreenId] = useState('')
  const [screenSearch, setScreenSearch] = useState('')
  const [showAddScreen, setShowAddScreen] = useState(false)
  const [editingScreenId, setEditingScreenId] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [dragOver, setDragOver] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!user) {
      navigate('/login')
      return
    }

    let cancelled = false
    async function load() {
      setLoading(true)
      setError('')
      try {
        const [screensRes, playlistsRes, mediaRes, groupsRes] = await Promise.all([
          api.getScreens(),
          api.getPlaylists(),
          api.getMedia(),
          api.getGroups(),
        ])
        if (cancelled) return
        setScreens(screensRes.screens)
        setPlaylists(playlistsRes.playlists)
        setMedia(mediaRes.media)
        setGroups(groupsRes.groups)
        const firstPlaylistId = playlistsRes.playlists[0]?.id || ''
        if (!uploadPlaylistId && firstPlaylistId) {
          setUploadPlaylistId(firstPlaylistId)
        }
        if (!pairPlaylistId && firstPlaylistId) {
          setPairPlaylistId(firstPlaylistId)
        }
        if (!selectedPlaylistId && firstPlaylistId) {
          setSelectedPlaylistId(firstPlaylistId)
        }
        if (!selectedGroupId && groupsRes.groups[0]) {
          setSelectedGroupId(groupsRes.groups[0].id)
        }
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'No se pudo cargar el panel')
        if (
          String(err).includes('401') ||
          String(err).includes('No autorizado') ||
          String(err).includes('Unauthorized')
        ) {
          clearSession()
          navigate('/login')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [user, navigate])

  useEffect(() => {
    if (!selectedPlaylistId || !user) {
      setPlaylistItems([])
      return
    }
    let cancelled = false
    async function loadItems() {
      try {
        const res = await api.getPlaylistItems(selectedPlaylistId)
        if (!cancelled) setPlaylistItems(res.items)
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'No se pudo cargar la playlist')
        }
      }
    }
    void loadItems()
    return () => {
      cancelled = true
    }
  }, [selectedPlaylistId, user])

  function logout() {
    clearSession()
    navigate('/')
  }

  async function addScreen(e: FormEvent) {
    e.preventDefault()
    if (pairCode.trim().length < 6) return
    setError('')
    try {
      const { screen } = await api.addScreen({
        code: pairCode.trim(),
        playlistId: pairPlaylistId || undefined,
      })
      setScreens((prev) => [...prev, screen])
      setPairCode('')
      setShowAddScreen(false)
      setTab('screens')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo agregar la pantalla')
    }
  }

  async function changeScreenPlaylist(screenId: string, playlistId: string) {
    const screen = screens.find((s) => s.id === screenId)
    setSavingScreenId(screenId)
    setError('')
    try {
      const { screen: updated } = await api.setScreenPlaylist(screenId, playlistId || null, {
        daypartStart: screen?.daypartStart || null,
        daypartEnd: screen?.daypartEnd || null,
      })
      setScreens((prev) =>
        prev.map((s) =>
          s.id === screenId
            ? {
                ...s,
                playlist: updated.playlist,
                playlistId: updated.playlistId,
                daypartStart: updated.daypartStart ?? null,
                daypartEnd: updated.daypartEnd ?? null,
              }
            : s,
        ),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo asignar la playlist')
    } finally {
      setSavingScreenId('')
    }
  }

  async function changeScreenDaypart(
    screenId: string,
    field: 'daypartStart' | 'daypartEnd',
    value: string,
  ) {
    const screen = screens.find((s) => s.id === screenId)
    if (!screen?.playlistId) return
    const next = {
      daypartStart: field === 'daypartStart' ? value || null : screen.daypartStart || null,
      daypartEnd: field === 'daypartEnd' ? value || null : screen.daypartEnd || null,
    }
    setScreens((prev) =>
      prev.map((s) => (s.id === screenId ? { ...s, ...next } : s)),
    )
    setSavingScreenId(screenId)
    setError('')
    try {
      const { screen: updated } = await api.setScreenPlaylist(screenId, screen.playlistId, next)
      setScreens((prev) =>
        prev.map((s) =>
          s.id === screenId
            ? {
                ...s,
                daypartStart: updated.daypartStart ?? null,
                daypartEnd: updated.daypartEnd ?? null,
              }
            : s,
        ),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo guardar el horario')
    } finally {
      setSavingScreenId('')
    }
  }

  async function changeScreenRotation(screenId: string, rotationDeg: 0 | 90 | 180 | 270) {
    setSavingScreenId(screenId)
    setError('')
    try {
      const { screen } = await api.updateScreen(screenId, { rotationDeg })
      setScreens((prev) =>
        prev.map((s) =>
          s.id === screenId ? { ...s, rotationDeg: screen.rotationDeg ?? rotationDeg } : s,
        ),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo cambiar la rotación')
    } finally {
      setSavingScreenId('')
    }
  }

  function formatLastSeen(iso?: string | null) {
    if (!iso) return 'Nunca'
    const ms = Date.now() - new Date(iso).getTime()
    if (ms < 60_000) return 'Ahora'
    if (ms < 3_600_000) return `Hace ${Math.floor(ms / 60_000)} min`
    if (ms < 86_400_000) return `Hace ${Math.floor(ms / 3_600_000)} h`
    return new Date(iso).toLocaleString('es-VE')
  }

  async function renameScreen(screen: Screen) {
    const name = prompt('Nombre de la pantalla', screen.name)
    if (!name || name.trim() === screen.name) return
    setSavingScreenId(screen.id)
    setError('')
    try {
      const { screen: updated } = await api.updateScreen(screen.id, { name: name.trim() })
      setScreens((prev) =>
        prev.map((s) => (s.id === screen.id ? { ...s, name: updated.name } : s)),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo renombrar')
    } finally {
      setSavingScreenId('')
    }
  }

  async function removeScreen(screen: Screen) {
    if (!confirm(`¿Eliminar "${screen.name}"?`)) return
    setError('')
    try {
      await api.deleteScreen(screen.id)
      setScreens((prev) => prev.filter((s) => s.id !== screen.id))
      if (editingScreenId === screen.id) setEditingScreenId(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo eliminar')
    }
  }

  async function refreshScreens() {
    try {
      const screensRes = await api.getScreens()
      setScreens(screensRes.screens)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo actualizar')
    }
  }

  const filteredScreens = screens.filter((s) => {
    const q = screenSearch.trim().toLowerCase()
    if (!q) return true
    return (
      s.name.toLowerCase().includes(q) ||
      s.code.toLowerCase().includes(q) ||
      (s.playlist || '').toLowerCase().includes(q)
    )
  })

  const selectedGroup = groups.find((g) => g.id === selectedGroupId) || null

  async function refreshGroups() {
    const groupsRes = await api.getGroups()
    setGroups(groupsRes.groups)
    if (selectedGroupId && !groupsRes.groups.some((g) => g.id === selectedGroupId)) {
      setSelectedGroupId(groupsRes.groups[0]?.id || '')
    }
  }

  async function createGroup() {
    const name = prompt('Nombre del grupo / video wall')
    if (!name) return
    setError('')
    try {
      const { group } = await api.createGroup({
        name,
        mode: 'videowall',
        rows: 2,
        cols: 2,
        playlistId: selectedPlaylistId || playlists[0]?.id || null,
      })
      setGroups((prev) => [...prev, { ...group, members: [], playlistName: null }])
      setSelectedGroupId(group.id)
      setTab('groups')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo crear el grupo')
    }
  }

  async function saveGroupMeta(
    patch: Partial<{
      name: string
      mode: 'group' | 'videowall'
      rows: number
      cols: number
      playlistId: string | null
      resetCycle: boolean
    }>,
  ) {
    if (!selectedGroupId) return
    setError('')
    try {
      await api.updateGroup(selectedGroupId, patch)
      await refreshGroups()
      if (patch.playlistId !== undefined) {
        const screensRes = await api.getScreens()
        setScreens(screensRes.screens)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo actualizar el grupo')
    }
  }

  async function assignCell(row: number, col: number, screenId: string) {
    if (!selectedGroup) return
    setError('')
    try {
      const members = selectedGroup.members
        .filter((m) => !(m.row === row && m.col === col) && m.screenId !== screenId)
        .map((m) => ({ screenId: m.screenId, row: m.row, col: m.col }))
      if (screenId) {
        members.push({ screenId, row, col })
      }
      await api.setGroupMembers(selectedGroup.id, members)
      await refreshGroups()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo asignar la celda')
    }
  }

  async function removeGroup() {
    if (!selectedGroup) return
    if (!confirm(`¿Eliminar el grupo "${selectedGroup.name}"?`)) return
    try {
      await api.deleteGroup(selectedGroup.id)
      setGroups((prev) => prev.filter((g) => g.id !== selectedGroup.id))
      setSelectedGroupId('')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo eliminar')
    }
  }

  async function addPlaylist() {
    const name = prompt('Nombre de la playlist')
    if (!name) return
    setError('')
    try {
      const { playlist } = await api.addPlaylist(name)
      setPlaylists((prev) => [...prev, playlist])
      if (!uploadPlaylistId) setUploadPlaylistId(playlist.id)
      setSelectedPlaylistId(playlist.id)
      setPlaylistItems([])
      setTab('playlists')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo crear la playlist')
    }
  }

  async function refreshPlaylistCounts() {
    const playlistsRes = await api.getPlaylists()
    setPlaylists(playlistsRes.playlists)
  }

  async function addMediaToSelectedPlaylist(mediaId: string) {
    if (!selectedPlaylistId) return
    setPlaylistBusy(true)
    setError('')
    try {
      await api.addPlaylistItem(selectedPlaylistId, mediaId)
      const res = await api.getPlaylistItems(selectedPlaylistId)
      setPlaylistItems(res.items)
      await refreshPlaylistCounts()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo agregar a la playlist')
    } finally {
      setPlaylistBusy(false)
    }
  }

  async function removeFromSelectedPlaylist(itemId: string) {
    if (!selectedPlaylistId) return
    setPlaylistBusy(true)
    setError('')
    try {
      await api.removePlaylistItem(selectedPlaylistId, itemId)
      setPlaylistItems((prev) => prev.filter((item) => item.id !== itemId))
      await refreshPlaylistCounts()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo quitar de la playlist')
    } finally {
      setPlaylistBusy(false)
    }
  }

  async function onUpload(files: FileList | File[] | null) {
    if (!files?.length) return
    setUploading(true)
    setError('')
    const maxVideoBytes = 15 * 1024 * 1024
    try {
      for (const file of Array.from(files)) {
        const type = (file.type || '').toLowerCase()
        const name = file.name || ''
        const isImage =
          type.startsWith('image/') || /\.(jpe?g|png|gif|webp|bmp)$/i.test(name)
        const isMp4 =
          type === 'video/mp4' || type === 'application/mp4' || /\.mp4$/i.test(name)

        if (!isImage && !isMp4) {
          throw new Error(
            `"${file.name}" no es válido. Usa imágenes (JPG/PNG/WebP) o video MP4.`,
          )
        }
        if (isMp4 && file.size > maxVideoBytes) {
          throw new Error(`"${file.name}" supera 15 MB. Comprime el MP4 e intenta de nuevo.`)
        }
        if (isImage && file.size > maxVideoBytes) {
          throw new Error(`"${file.name}" supera 15 MB.`)
        }
        const { media: item } = await api.uploadMedia(file, uploadPlaylistId || undefined)
        setMedia((prev) => [item, ...prev])
      }
      if (uploadPlaylistId) {
        const playlistsRes = await api.getPlaylists()
        setPlaylists(playlistsRes.playlists)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Error al subir')
    } finally {
      setUploading(false)
    }
  }

  function onDragOver(e: DragEvent) {
    e.preventDefault()
    e.stopPropagation()
    if (!uploading) setDragOver(true)
  }

  function onDragLeave(e: DragEvent) {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
    if (uploading) return
    void onUpload(e.dataTransfer.files)
  }

  async function removeMedia(id: string) {
    if (!confirm('¿Eliminar este archivo?')) return
    try {
      await api.deleteMedia(id)
      setMedia((prev) => prev.filter((m) => m.id !== id))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo eliminar')
    }
  }

  if (!user) return null

  return (
    <div className="dash">
      <aside className="dash__sidebar">
        <Link to="/" className="dash__brand">
          vescreenflow
        </Link>
        <nav>
          <button
            className={tab === 'screens' ? 'active' : ''}
            onClick={() => setTab('screens')}
          >
            Pantallas
          </button>
          <button
            className={tab === 'groups' ? 'active' : ''}
            onClick={() => setTab('groups')}
          >
            Grupos / Wall
          </button>
          <button
            className={tab === 'playlists' ? 'active' : ''}
            onClick={() => setTab('playlists')}
          >
            Playlists
          </button>
          <button
            className={tab === 'content' ? 'active' : ''}
            onClick={() => setTab('content')}
          >
            Contenido
          </button>
        </nav>
        <Link to="/play" className="dash__play-link">
          Abrir player
        </Link>
        <button className="dash__logout" onClick={logout}>
          Cerrar sesión
        </button>
      </aside>

      <main className="dash__main">
        <header className="dash__top">
          <div>
            <h1>Hola, {user.name}</h1>
            <p>Gestiona pantallas, playlists y contenido a distancia.</p>
          </div>
          <div className="dash__stats">
            <div>
              <strong>{screens.length}</strong>
              <span>Pantallas</span>
            </div>
            <div>
              <strong>{playlists.length}</strong>
              <span>Playlists</span>
            </div>
            <div>
              <strong>{media.length}</strong>
              <span>Archivos</span>
            </div>
          </div>
        </header>

        {error ? <div className="dash-error">{error}</div> : null}
        {loading ? <p className="dash-hint">Cargando…</p> : null}

        {tab === 'screens' && !loading && (
          <section className="dash-panel screens-panel">
            <div className="screens-hero">
              <div>
                <h2>Pantallas</h2>
                <p>Gestiona tus pantallas, asigna playlists y mantén el contenido al día.</p>
                <span className="screens-count">{screens.length} pantallas en total</span>
              </div>
              <div className="screens-actions">
                <button
                  type="button"
                  className="btn btn-add"
                  onClick={() => setShowAddScreen((v) => !v)}
                >
                  + Agregar pantalla
                </button>
                <button type="button" className="btn btn-update" onClick={() => void refreshScreens()}>
                  Actualizar
                </button>
                <Link to="/play" className="btn btn-play">
                  Abrir player
                </Link>
              </div>
            </div>

            {showAddScreen ? (
              <form onSubmit={addScreen} className="screens-add">
                <input
                  value={pairCode}
                  onChange={(e) => setPairCode(e.target.value)}
                  placeholder="Código de 8 dígitos del player"
                  maxLength={8}
                  autoFocus
                />
                <select
                  value={pairPlaylistId}
                  onChange={(e) => setPairPlaylistId(e.target.value)}
                  aria-label="Playlist a reproducir"
                >
                  <option value="">Sin playlist</option>
                  {playlists.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
                <button className="btn btn-navy" type="submit">
                  Emparejar
                </button>
                <button
                  type="button"
                  className="btn btn-ghost"
                  onClick={() => setShowAddScreen(false)}
                >
                  Cancelar
                </button>
              </form>
            ) : null}

            <div className="screens-toolbar">
              <input
                className="screens-search"
                value={screenSearch}
                onChange={(e) => setScreenSearch(e.target.value)}
                placeholder="Buscar por nombre, código o playlist"
              />
            </div>

            <div className="screens-grid">
              {filteredScreens.map((screen) => {
                const editing = editingScreenId === screen.id
                return (
                  <article key={screen.id} className="screen-card">
                    <header className="screen-card__head">
                      <div>
                        <h3>{screen.name}</h3>
                        <code>{screen.code}</code>
                      </div>
                      <span className={`screen-badge screen-badge--${screen.status}`}>
                        {screen.status === 'online' ? 'Activa' : 'Inactiva'}
                      </span>
                    </header>

                    <dl className="screen-card__meta">
                      <div>
                        <dt>Última vez</dt>
                        <dd>{formatLastSeen(screen.lastSeenAt)}</dd>
                      </div>
                      <div>
                        <dt>Playlist</dt>
                        <dd>{screen.playlist || 'Sin asignar'}</dd>
                      </div>
                      <div>
                        <dt>Horario</dt>
                        <dd>
                          {screen.daypartStart && screen.daypartEnd
                            ? `${screen.daypartStart}–${screen.daypartEnd}`
                            : 'Todo el día'}
                        </dd>
                      </div>
                      <div>
                        <dt>Rotación</dt>
                        <dd>{screen.rotationDeg || 0}°</dd>
                      </div>
                    </dl>

                    {editing ? (
                      <div className="screen-edit-panel">
                        <label className="screen-playlist screen-playlist--edit">
                          <span>Playlist</span>
                          <select
                            value={screen.playlistId || ''}
                            disabled={savingScreenId === screen.id}
                            onChange={(e) => void changeScreenPlaylist(screen.id, e.target.value)}
                          >
                            <option value="">Sin asignar</option>
                            {playlists.map((p) => (
                              <option key={p.id} value={p.id}>
                                {p.name}
                              </option>
                            ))}
                          </select>
                        </label>
                        <div className="screen-daypart">
                          <label>
                            Desde
                            <input
                              type="time"
                              value={screen.daypartStart || ''}
                              disabled={!screen.playlistId || savingScreenId === screen.id}
                              onChange={(e) =>
                                void changeScreenDaypart(screen.id, 'daypartStart', e.target.value)
                              }
                            />
                          </label>
                          <label>
                            Hasta
                            <input
                              type="time"
                              value={screen.daypartEnd || ''}
                              disabled={!screen.playlistId || savingScreenId === screen.id}
                              onChange={(e) =>
                                void changeScreenDaypart(screen.id, 'daypartEnd', e.target.value)
                              }
                            />
                          </label>
                        </div>
                        <label className="screen-playlist screen-playlist--edit">
                          <span>Rotación</span>
                          <select
                            value={screen.rotationDeg || 0}
                            disabled={savingScreenId === screen.id}
                            onChange={(e) =>
                              void changeScreenRotation(
                                screen.id,
                                Number(e.target.value) as 0 | 90 | 180 | 270,
                              )
                            }
                          >
                            <option value={0}>0°</option>
                            <option value={90}>90°</option>
                            <option value={180}>180°</option>
                            <option value={270}>270°</option>
                          </select>
                        </label>
                      </div>
                    ) : null}

                    <div className="screen-card__actions">
                      <button
                        type="button"
                        className="btn-card btn-card--edit"
                        disabled={savingScreenId === screen.id}
                        onClick={() => void renameScreen(screen)}
                      >
                        Editar
                      </button>
                      <button
                        type="button"
                        className="btn-card btn-card--playlist"
                        onClick={() => setEditingScreenId(editing ? null : screen.id)}
                      >
                        {editing ? 'Cerrar' : 'Playlist'}
                      </button>
                      <Link to="/play" className="btn-card btn-card--view">
                        Ver player
                      </Link>
                      <button
                        type="button"
                        className="btn-card btn-card--danger"
                        onClick={() => void removeScreen(screen)}
                      >
                        Eliminar
                      </button>
                    </div>
                  </article>
                )
              })}

              <article className="screen-card screen-card--cta">
                <h3>Obtén el player aquí</h3>
                <p>
                  Web kiosk, scripts Windows/Pi, o APK Android (sideload). Compila con el
                  workflow de GitHub Actions en <code>android/</code>.
                </p>
                <div className="screen-card__cta-actions">
                  <Link to="/play" className="btn btn-add">
                    Abrir /play
                  </Link>
                  <a
                    className="btn btn-play"
                    href="https://github.com/vescreenflow/vescreenflow/actions"
                    target="_blank"
                    rel="noreferrer"
                  >
                    APK (CI)
                  </a>
                </div>
              </article>

              {!filteredScreens.length && screens.length ? (
                <p className="dash-hint">No hay pantallas que coincidan con la búsqueda.</p>
              ) : null}
              {!screens.length ? (
                <p className="dash-hint screens-empty">
                  Abre <Link to="/play">/play</Link> en la TV, copia el código y pulsa{' '}
                  <strong>Agregar pantalla</strong>.
                </p>
              ) : null}
            </div>
          </section>
        )}

        {tab === 'groups' && !loading && (
          <section className="dash-panel">
            <div className="dash-panel__head">
              <h2>Grupos / Video Wall</h2>
              <button className="btn btn-navy" type="button" onClick={() => void createGroup()}>
                Nuevo video wall
              </button>
            </div>
            <p className="dash-hint">
              Agrupa pantallas en una grilla. En modo <strong>videowall</strong> cada TV
              muestra una porción del mismo contenido, sincronizada.
            </p>

            <div className="playlist-picker">
              {groups.map((g) => (
                <button
                  key={g.id}
                  type="button"
                  className={`playlist-chip${selectedGroupId === g.id ? ' active' : ''}`}
                  onClick={() => setSelectedGroupId(g.id)}
                >
                  <strong>{g.name}</strong>
                  <span>
                    {g.mode} · {g.rows}x{g.cols} · {g.members.length} pantallas
                  </span>
                </button>
              ))}
              {!groups.length ? (
                <p className="dash-hint">Crea un video wall 2x2 para empezar.</p>
              ) : null}
            </div>

            {selectedGroup ? (
              <div className="group-editor">
                <div className="group-editor__controls">
                  <label>
                    Nombre
                    <input
                      defaultValue={selectedGroup.name}
                      key={selectedGroup.id + '-name'}
                      onBlur={(e) => {
                        if (e.target.value.trim() && e.target.value !== selectedGroup.name) {
                          void saveGroupMeta({ name: e.target.value.trim() })
                        }
                      }}
                    />
                  </label>
                  <label>
                    Modo
                    <select
                      value={selectedGroup.mode}
                      onChange={(e) =>
                        void saveGroupMeta({
                          mode: e.target.value as 'group' | 'videowall',
                          resetCycle: true,
                        })
                      }
                    >
                      <option value="videowall">Video wall (crop sync)</option>
                      <option value="group">Grupo (misma playlist)</option>
                    </select>
                  </label>
                  <label>
                    Filas
                    <select
                      value={selectedGroup.rows}
                      onChange={(e) =>
                        void saveGroupMeta({ rows: Number(e.target.value), resetCycle: true })
                      }
                    >
                      {[1, 2, 3, 4].map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Columnas
                    <select
                      value={selectedGroup.cols}
                      onChange={(e) =>
                        void saveGroupMeta({ cols: Number(e.target.value), resetCycle: true })
                      }
                    >
                      {[1, 2, 3, 4].map((n) => (
                        <option key={n} value={n}>
                          {n}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Playlist
                    <select
                      value={selectedGroup.playlistId || ''}
                      onChange={(e) =>
                        void saveGroupMeta({
                          playlistId: e.target.value || null,
                          resetCycle: true,
                        })
                      }
                    >
                      <option value="">Sin playlist</option>
                      {playlists.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    className="btn btn-update"
                    onClick={() => void saveGroupMeta({ resetCycle: true })}
                  >
                    Reiniciar sync
                  </button>
                  <button
                    type="button"
                    className="btn-card btn-card--danger"
                    onClick={() => void removeGroup()}
                  >
                    Eliminar grupo
                  </button>
                </div>

                <div
                  className="wall-grid"
                  style={{
                    gridTemplateColumns: `repeat(${selectedGroup.cols}, minmax(120px, 1fr))`,
                  }}
                >
                  {Array.from({ length: selectedGroup.rows * selectedGroup.cols }).map(
                    (_, i) => {
                      const row = Math.floor(i / selectedGroup.cols)
                      const col = i % selectedGroup.cols
                      const member = selectedGroup.members.find(
                        (m) => m.row === row && m.col === col,
                      )
                      const used = new Set(selectedGroup.members.map((m) => m.screenId))
                      return (
                        <div key={`${row}-${col}`} className="wall-cell">
                          <span className="wall-cell__pos">
                            {col + 1},{row + 1}
                          </span>
                          <select
                            value={member?.screenId || ''}
                            onChange={(e) => void assignCell(row, col, e.target.value)}
                          >
                            <option value="">Vacía</option>
                            {screens.map((s) => (
                              <option
                                key={s.id}
                                value={s.id}
                                disabled={used.has(s.id) && s.id !== member?.screenId}
                              >
                                {s.name} ({s.code})
                              </option>
                            ))}
                          </select>
                          {member ? (
                            <small>
                              {member.screenName} · {member.code}
                            </small>
                          ) : (
                            <small>Sin pantalla</small>
                          )}
                        </div>
                      )
                    },
                  )}
                </div>
              </div>
            ) : null}
          </section>
        )}

        {tab === 'playlists' && !loading && (
          <section className="dash-panel">
            <div className="dash-panel__head">
              <h2>Playlists</h2>
              <button className="btn btn-navy" onClick={addPlaylist}>
                Nueva playlist
              </button>
            </div>

            <div className="playlist-picker">
              {playlists.map((playlist) => (
                <button
                  key={playlist.id}
                  type="button"
                  className={`playlist-chip${selectedPlaylistId === playlist.id ? ' active' : ''}`}
                  onClick={() => setSelectedPlaylistId(playlist.id)}
                >
                  <strong>{playlist.name}</strong>
                  <span>{playlist.items} archivos</span>
                </button>
              ))}
              {!playlists.length ? (
                <p className="dash-hint">Crea una playlist para empezar a armar el loop.</p>
              ) : null}
            </div>

            {selectedPlaylistId ? (
              <div className="playlist-editor">
                <div className="playlist-editor__col">
                  <h3>En esta playlist</h3>
                  <p className="dash-hint">Estos archivos se reproducen en las pantallas asignadas.</p>
                  <div className="playlist-items">
                    {playlistItems.map((item, index) => (
                      <article key={item.id} className="playlist-item">
                        <span className="playlist-item__order">{index + 1}</span>
                        {item.mediaType === 'video' ? (
                          <video src={item.url} muted playsInline />
                        ) : (
                          <img src={item.url} alt={item.name} />
                        )}
                        <div>
                          <strong>{item.name}</strong>
                          <span>
                            {item.mediaType} · {item.durationSec}s
                          </span>
                        </div>
                        <button
                          type="button"
                          disabled={playlistBusy}
                          onClick={() => void removeFromSelectedPlaylist(item.id)}
                        >
                          Quitar
                        </button>
                      </article>
                    ))}
                    {!playlistItems.length ? (
                      <p className="dash-hint">Vacía. Agrega archivos desde la biblioteca a la derecha.</p>
                    ) : null}
                  </div>
                </div>

                <div className="playlist-editor__col">
                  <h3>Biblioteca</h3>
                  <p className="dash-hint">Elige qué archivos cargar en esta playlist.</p>
                  <div className="playlist-items">
                    {media.map((item) => {
                      const alreadyIn = playlistItems.some((p) => p.mediaId === item.id)
                      return (
                        <article key={item.id} className="playlist-item">
                          {item.mediaType === 'video' ? (
                            <video src={item.url} muted playsInline />
                          ) : (
                            <img src={item.url} alt={item.name} />
                          )}
                          <div>
                            <strong>{item.name}</strong>
                            <span>
                              {item.mediaType} · {item.durationSec}s
                            </span>
                          </div>
                          <button
                            type="button"
                            className={alreadyIn ? 'is-in' : ''}
                            disabled={playlistBusy || alreadyIn}
                            onClick={() => void addMediaToSelectedPlaylist(item.id)}
                          >
                            {alreadyIn ? 'En playlist' : 'Agregar'}
                          </button>
                        </article>
                      )
                    })}
                    {!media.length ? (
                      <p className="dash-hint">
                        No hay archivos. Ve a <button type="button" className="linkish" onClick={() => setTab('content')}>Contenido</button> para subirlos.
                      </p>
                    ) : null}
                  </div>
                </div>
              </div>
            ) : null}
          </section>
        )}

        {tab === 'content' && !loading && (
          <section className="dash-panel">
            <div className="dash-panel__head">
              <h2>Contenido</h2>
              <div className="dash-pair">
                <select
                  value={uploadPlaylistId}
                  onChange={(e) => setUploadPlaylistId(e.target.value)}
                  aria-label="Playlist destino"
                >
                  <option value="">Solo biblioteca</option>
                  {playlists.map((p) => (
                    <option key={p.id} value={p.id}>
                      Agregar a: {p.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="dash-limits" role="note">
              <strong>Límites de carga</strong>
              <ul>
                <li>
                  Videos: <b>solo MP4</b> · máximo <b>15 MB</b> por archivo
                </li>
                <li>
                  Imágenes: JPG, PNG o WebP · máximo <b>15 MB</b>
                </li>
                <li>No se aceptan MOV, WebM, AVI ni otros formatos de video</li>
              </ul>
            </div>

            <div
              className={`dash-upload${uploading ? ' dash-upload--busy' : ''}${dragOver ? ' dash-upload--drag' : ''}`}
              onDragOver={onDragOver}
              onDragEnter={onDragOver}
              onDragLeave={onDragLeave}
              onDrop={onDrop}
            >
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp,.jpg,.jpeg,.png,.gif,.webp,video/mp4,.mp4"
                multiple
                disabled={uploading}
                onChange={(e) => {
                  void onUpload(e.target.files)
                  e.target.value = ''
                }}
              />
              <span className="dash-upload__text">
                {uploading
                  ? 'Subiendo…'
                  : dragOver
                    ? 'Suelta para subir'
                    : 'Suelta fotos o un video MP4 (máx. 15 MB)'}
              </span>
              <button
                type="button"
                className="dash-upload__btn"
                disabled={uploading}
                onClick={() => fileInputRef.current?.click()}
              >
                Elegir archivos
              </button>
            </div>
            <p className="dash-hint">
              Si el video pesa más de 15 MB o no es MP4, la carga se rechaza automáticamente.
            </p>

            <div className="media-grid">
              {media.map((item) => (
                <article key={item.id} className="media-card">
                  {item.mediaType === 'video' ? (
                    <video src={item.url} muted playsInline />
                  ) : (
                    <img src={item.url} alt={item.name} />
                  )}
                  <div className="media-card__meta">
                    <strong>{item.name}</strong>
                    <span>
                      {item.mediaType} · {item.durationSec}s
                    </span>
                    <button type="button" onClick={() => void removeMedia(item.id)}>
                      Eliminar
                    </button>
                  </div>
                </article>
              ))}
              {!media.length ? (
                <p className="dash-hint">Aún no hay archivos. Sube el primero arriba.</p>
              ) : null}
            </div>
          </section>
        )}
      </main>
    </div>
  )
}
