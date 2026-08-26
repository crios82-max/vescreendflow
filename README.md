# VePlayer — OS de reproductor para vehículos

Launcher kiosk Android para head-units / tablets de flota.

| Módulo | Qué hace |
|--------|----------|
| **Cámaras** | Dual ConcurrentCamera · front/back/USB EXTERNAL (Camera2) |
| **Radio** | Streaming IP (ExoPlayer); UI listo para FM hardware |
| **YouTube** | WebView oficial |
| **Tienda** | Play Store + **Spotify App Remote SDK** (enlazar dispositivo) |
| **Pantalla** | vescreenflow |
| **Mapa** | SenseFlow |
| **Kiosk duro** | Device Owner + Lock Task + boot |
| **Sense** | Pings anónimos |

## Build

```bash
cd veplayer/android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Device Owner (kiosk duro)

Tras instalar el APK debug (sin cuentas Google en el device de prueba):

```bash
./scripts/enable-device-owner.sh com.veplayer.app.debug
# o release:
./scripts/enable-device-owner.sh com.veplayer.app
```

Whitelistea VePlayer + Spotify + YouTube en Lock Task.

## Spotify App Remote

1. Crea una app en [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Redirect URI: `veplayer://callback`
3. En `veplayer/android/local.properties`:

```properties
SPOTIFY_CLIENT_ID=tu_client_id
SPOTIFY_REDIRECT_URI=veplayer://callback
```

4. Instala Spotify, inicia sesión, en **Tienda → Enlazar dispositivo**.

AARs oficiales en `app/libs/` (no redistribuimos el cliente Spotify).

## Cámaras USB

Si el kernel expone UVC como Camera2 `LENS_FACING_EXTERNAL`, aparecen en el selector Dual A/B.  
ConcurrentCamera requiere SoC compatible; si no, cae a cámara simple.

## VePlayer OS MVP (v0.3)

- **Ajustes + PIN** (default `1234`)
- **Flota**: register / heartbeat / pair / devices
- **OTA**: latest release via heartbeat
- **Reverse mock** → fuerza Cámaras
- **Bloqueo video** YouTube/Player si velocidad ≥ umbral o reverse

```bash
# API flota
curl -s -X POST http://127.0.0.1:4100/api/fleet/register \
  -H 'content-type: application/json' \
  -d '{"device_id":"testdevice001","name":"Unidad 1","version_code":3,"app_version":"0.3.0"}'
# Dashboard flota: http://127.0.0.1:4100/fleet.html
```
