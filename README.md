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

## Demo UI web

```bash
python3 -m http.server 4101 --directory veplayer/web
```
