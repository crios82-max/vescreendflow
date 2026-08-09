import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import './Player.css'

type PlayerItem = {
  id: string
  name: string
  mediaType: 'image' | 'video'
  url: string
  durationSec: number
}

type WallInfo = {
  groupId: string
  mode: 'group' | 'videowall'
  rows: number
  cols: number
  row: number
  col: number
  serverTime: number
  cycleEpoch: number
}

type PairState =
  | { status: 'waiting'; code: string }
  | { status: 'playing'; code: string; screenName: string; rotationDeg: number }
  | { status: 'error'; code: string; message: string }

const API_URL = import.meta.env.VITE_API_URL || 'http://127.0.0.1:4000/api'
const CODE_KEY = 'vescreenflow_pair_code'

function generateCode() {
  return String(Math.floor(10000000 + Math.random() * 90000000))
}

function getOrCreateCode() {
  const existing = localStorage.getItem(CODE_KEY)
  if (existing && /^\d{6,8}$/.test(existing)) return existing
  const code = generateCode()
  localStorage.setItem(CODE_KEY, code)
  return code
}

function itemsSignature(items: PlayerItem[]) {
  return items.map((i) => `${i.id}:${i.url}:${i.durationSec}:${i.mediaType}`).join('|')
}

function itemDurationMs(item: PlayerItem) {
  return Math.max(3, Number(item.durationSec) || 8) * 1000
}

function cycleLengthMs(items: PlayerItem[]) {
  return items.reduce((sum, item) => sum + itemDurationMs(item), 0)
}

function computeSyncedIndex(items: PlayerItem[], cycleEpoch: number, now: number) {
  if (!items.length) return 0
  const total = cycleLengthMs(items)
  if (total <= 0) return 0
  let t = ((now - cycleEpoch) % total + total) % total
  for (let i = 0; i < items.length; i++) {
    const d = itemDurationMs(items[i])
    if (t < d) return i
    t -= d
  }
  return 0
}

function pickFitMode(mediaW: number, mediaH: number, viewW: number, viewH: number) {
  if (!mediaW || !mediaH || !viewW || !viewH) return 'contain' as const
  const mediaRatio = mediaW / mediaH
  const viewRatio = viewW / viewH
  const diff = Math.abs(mediaRatio - viewRatio) / viewRatio
  return diff > 0.35 ? ('contain' as const) : ('cover' as const)
}

export function Player() {
  const code = useMemo(() => getOrCreateCode(), [])
  const [pair, setPair] = useState<PairState>({ status: 'waiting', code })
  const [items, setItems] = useState<PlayerItem[]>([])
  const [index, setIndex] = useState(0)
  const [wall, setWall] = useState<WallInfo | null>(null)
  const [clockOffset, setClockOffset] = useState(0)
  const [fitMode, setFitMode] = useState<'cover' | 'contain'>('cover')
  const itemsRef = useRef<PlayerItem[]>([])
  const wallRef = useRef<WallInfo | null>(null)
  const sigRef = useRef('')
  const stageRef = useRef<HTMLDivElement>(null)
  const mediaRef = useRef<HTMLImageElement | HTMLVideoElement | null>(null)

  const isVideoWall = wall?.mode === 'videowall'

  const applyAutoFit = useCallback(() => {
    if (wallRef.current?.mode === 'videowall') return
    const stage = stageRef.current
    const media = mediaRef.current
    if (!stage || !media) return

    const viewW = stage.clientWidth || window.innerWidth
    const viewH = stage.clientHeight || window.innerHeight

    let mediaW = 0
    let mediaH = 0
    if (media instanceof HTMLVideoElement) {
      mediaW = media.videoWidth
      mediaH = media.videoHeight
    } else {
      mediaW = media.naturalWidth
      mediaH = media.naturalHeight
    }
    if (!mediaW || !mediaH) return

    const mode = pickFitMode(mediaW, mediaH, viewW, viewH)
    setFitMode(mode)

    const scale =
      mode === 'cover'
        ? Math.max(viewW / mediaW, viewH / mediaH)
        : Math.min(viewW / mediaW, viewH / mediaH)

    media.style.width = `${Math.round(mediaW * scale)}px`
    media.style.height = `${Math.round(mediaH * scale)}px`
    media.style.maxWidth = 'none'
    media.style.maxHeight = 'none'
    media.style.objectFit = mode
  }, [])

  const refresh = useCallback(async () => {
    try {
      const res = await fetch(`${API_URL}/player/${code}`)
      const data = await res.json()
      if (!res.ok) {
        setPair({ status: 'error', code, message: data.error || 'Error de conexión' })
        return
      }
      if (!data.paired) {
        setPair({ status: 'waiting', code })
        setItems([])
        setWall(null)
        itemsRef.current = []
        wallRef.current = null
        sigRef.current = ''
        return
      }

      if (typeof data.serverTime === 'number') {
        setClockOffset(data.serverTime - Date.now())
      }

      const nextItems: PlayerItem[] = Array.isArray(data.items) ? data.items : []
      const nextWall: WallInfo | null = data.wall || null
      const nextSig =
        itemsSignature(nextItems) +
        '|' +
        (nextWall
          ? `${nextWall.groupId}:${nextWall.mode}:${nextWall.rows}x${nextWall.cols}:${nextWall.row},${nextWall.col}:${nextWall.cycleEpoch}`
          : 'none')

      setPair({
        status: 'playing',
        code,
        screenName: data.screen?.name || 'Pantalla',
        rotationDeg: Number(data.screen?.rotationDeg) || 0,
      })

      if (nextSig !== sigRef.current) {
        sigRef.current = nextSig
        itemsRef.current = nextItems
        wallRef.current = nextWall
        setItems(nextItems)
        setWall(nextWall)
        const now = (data.serverTime as number) || Date.now()
        const epoch = nextWall?.cycleEpoch || now
        setIndex(computeSyncedIndex(nextItems, epoch, now))
      }
    } catch {
      setPair({
        status: 'error',
        code,
        message: 'No se pudo conectar con la API. Revisa la red.',
      })
    }
  }, [code])

  useEffect(() => {
    void refresh()
    const id = window.setInterval(() => void refresh(), 4000)
    return () => window.clearInterval(id)
  }, [refresh])

  useEffect(() => {
    document.documentElement.classList.add('player-active')
    document.body.classList.add('player-active')
    return () => {
      document.documentElement.classList.remove('player-active')
      document.body.classList.remove('player-active')
    }
  }, [])

  // Shared clock sync for playlist index (group + videowall + solo)
  useEffect(() => {
    if (pair.status !== 'playing' || items.length === 0) return

    const tick = () => {
      const list = itemsRef.current
      if (!list.length) return
      const w = wallRef.current
      const now = Date.now() + clockOffset
      const epoch = w?.cycleEpoch || now - (now % cycleLengthMs(list))
      const next = computeSyncedIndex(list, epoch, now)
      setIndex((prev) => (prev === next ? prev : next))
    }

    tick()
    const id = window.setInterval(tick, 250)
    return () => window.clearInterval(id)
  }, [pair.status, items, clockOffset, wall])

  useEffect(() => {
    applyAutoFit()
    const onResize = () => applyAutoFit()
    window.addEventListener('resize', onResize)
    window.visualViewport?.addEventListener('resize', onResize)
    return () => {
      window.removeEventListener('resize', onResize)
      window.visualViewport?.removeEventListener('resize', onResize)
    }
  }, [applyAutoFit, index, items, pair.status, isVideoWall])

  useEffect(() => {
    if (pair.status !== 'playing') return
    const tryFullscreen = () => {
      if (!document.fullscreenElement) {
        void document.documentElement.requestFullscreen?.().catch(() => undefined)
      }
    }
    tryFullscreen()
    const onFirstGesture = () => {
      tryFullscreen()
      window.setTimeout(applyAutoFit, 50)
      window.removeEventListener('pointerdown', onFirstGesture)
      window.removeEventListener('keydown', onFirstGesture)
    }
    window.addEventListener('pointerdown', onFirstGesture, { once: true })
    window.addEventListener('keydown', onFirstGesture, { once: true })
    return () => {
      window.removeEventListener('pointerdown', onFirstGesture)
      window.removeEventListener('keydown', onFirstGesture)
    }
  }, [pair.status, applyAutoFit])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() === 'f') {
        if (!document.fullscreenElement) {
          void document.documentElement.requestFullscreen?.()
        } else {
          void document.exitFullscreen?.()
        }
      }
      if (e.key.toLowerCase() === 'r') {
        const next = generateCode()
        localStorage.setItem(CODE_KEY, next)
        window.location.reload()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  async function enterFullscreen() {
    try {
      await document.documentElement.requestFullscreen?.()
    } catch {
      // gesture may be required
    }
  }

  if (pair.status === 'waiting' || pair.status === 'error') {
    return (
      <div className="player player--pair">
        <div className="player__brand">vescreenflow</div>
        <h1>Empareja esta pantalla</h1>
        <p className="player__hint">
          En el panel, ve a <strong>Pantallas → Agregar pantalla</strong> e ingresa este
          código:
        </p>
        <div className="player__code" aria-live="polite">
          {code}
        </div>
        {pair.status === 'error' ? (
          <p className="player__error">{pair.message}</p>
        ) : (
          <p className="player__wait">Esperando emparejamiento…</p>
        )}
        <div className="player__actions">
          <button type="button" className="btn btn-navy" onClick={enterFullscreen}>
            Pantalla completa
          </button>
          <button
            type="button"
            className="btn player__ghost"
            onClick={() => {
              localStorage.setItem(CODE_KEY, generateCode())
              window.location.reload()
            }}
          >
            Nuevo código
          </button>
        </div>
        <p className="player__keys">Atajos: F = pantalla completa · R = nuevo código</p>
      </div>
    )
  }

  if (items.length === 0) {
    return (
      <div className="player player--pair">
        <h1>{pair.screenName}</h1>
        <p>Emparejada, pero la playlist no tiene archivos todavía.</p>
      </div>
    )
  }

  const safeIndex = index % items.length
  const item = items[safeIndex]
  const wallStyle =
    isVideoWall && wall
      ? {
          width: `${wall.cols * 100}%`,
          height: `${wall.rows * 100}%`,
          transform: `translate(${(-wall.col / wall.cols) * 100}%, ${(-wall.row / wall.rows) * 100}%)`,
        }
      : undefined

  return (
    <div
      className={`player player--show${pair.rotationDeg ? ` player--rot-${pair.rotationDeg}` : ''}`}
      onDoubleClick={enterFullscreen}
    >
      <div className="player__stage" ref={stageRef}>
        <div
          className={`player__wall${isVideoWall ? ' player__wall--active' : ''}`}
          style={wallStyle}
        >
          {item.mediaType === 'video' ? (
            <video
              key={`${item.id}-${safeIndex}`}
              ref={(el) => {
                mediaRef.current = el
              }}
              className={`player__media${isVideoWall ? ' player__media--wall' : ''} player__media--${isVideoWall ? 'cover' : fitMode}`}
              src={item.url}
              autoPlay
              muted
              playsInline
              loop={false}
              onLoadedMetadata={applyAutoFit}
              onLoadedData={applyAutoFit}
            />
          ) : (
            <img
              key={`${item.id}-${safeIndex}`}
              ref={(el) => {
                mediaRef.current = el
              }}
              className={`player__media${isVideoWall ? ' player__media--wall' : ''} player__media--${isVideoWall ? 'cover' : fitMode}`}
              src={item.url}
              alt={item.name}
              draggable={false}
              onLoad={applyAutoFit}
            />
          )}
        </div>
      </div>
      <div className="player__chrome">
        <span>
          {pair.screenName}
          {isVideoWall && wall
            ? ` · wall ${wall.col + 1},${wall.row + 1} / ${wall.cols}x${wall.rows}`
            : ''}
        </span>
        <span>
          {safeIndex + 1}/{items.length} · {item.name}
        </span>
      </div>
    </div>
  )
}
