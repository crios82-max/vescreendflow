# SenseFlow

Crowdsourced **tráfico + personas** (estilo Google Maps, MVP): app Android envía pings anónimos; API agrega por geohash; mapa web muestra ambas capas a la vez.

## Estructura

```
senseflow/
  server/     API Express + SQLite (pings, traffic, crowd)
  web/        Mapa Leaflet (servido por la API)
  android/    App Kotlin + Compose (foreground location)
  data/       SQLite local (gitignored)
```

## API (puerto 4100)

```bash
cd senseflow/server
npm install
npm run seed    # datos demo Caracas
npm run dev
```

| Endpoint | Uso |
|----------|-----|
| `GET /api/health` | health |
| `POST /api/pings` | batch de pings `{ pings: [...] }` |
| `GET /api/traffic?bbox=minLng,minLat,maxLng,maxLat` | GeoJSON tráfico |
| `GET /api/crowd?bbox=...` | GeoJSON personas |
| `GET /api/stats` | conteos ventana 15 min |
| `GET /` | mapa live |

### Ping

```json
{
  "pings": [{
    "lat": 10.496,
    "lng": -66.898,
    "accuracy_m": 12,
    "speed_mps": 8.2,
    "activity": "IN_VEHICLE",
    "device_bucket": "abc123def456...",
    "ts": 1730000000
  }]
}
```

`activity`: `IN_VEHICLE` | `ON_FOOT` | `STILL` | `UNKNOWN`  
`device_bucket`: hash diario rotativo (no user id).

## Android

```bash
cd senseflow/android
# Requiere Android SDK + JDK 17
./gradlew assembleDebug
```

1. Arranca la API en tu PC (`:4100`).
2. Emulador: `API_BASE_URL` default `http://10.0.2.2:4100`.
3. Teléfono físico: pon la IP LAN del PC en el campo de la app.
4. Activa **Compartir sensores** y acepta permisos de ubicación.

La app abre el mapa web de SenseFlow embebido y manda pings en foreground service.

## Privacidad (MVP)

- Opt-in explícito.
- Sin login en pings.
- `device_bucket` rota cada día.
- Agregación por geohash (~150 m) antes de pintar capas.

## Próximos pasos

- Activity Recognition transitions (además de heurística por velocidad).
- Snap a grafo vial (OSRM) en vez de solo geohash.
- `MIN_DEVICES` más alto + differential privacy en producción.
