# VePlayer — OS de reproductor para vehículos

Launcher kiosk Android para head-units / tablets de flota:

| Módulo | Qué hace |
|--------|----------|
| **Cámaras** | Delantera (retrovisor digital) + trasera (CameraX) |
| **Radio** | Streaming IP (SomaFM / Radio Paradise…); UI listo para FM hardware |
| **YouTube** | WebView oficial `m.youtube.com` |
| **Tienda** | Instalar/abrir Spotify + guía Connect (sin redistribuir APKs) |
| **Pantalla** | vescreenflow player |
| **Mapa** | SenseFlow (tráfico + personas) |
| **Sense** | Pings anónimos en background |
| **Kiosk** | Immersive + lock-task + boot receiver |

## Build

```bash
cd veplayer/android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Config

En `app/build.gradle.kts`:

- `SENSEFLOW_URL` — default `http://10.0.2.2:4100` (emulador)
- `PLAYER_URL` — `https://vescreenflow.com/play`

En dispositivo físico: cambia SenseFlow a la IP LAN del servidor.

## Spotify (legal)

La tienda **abre Play Store / app oficial** y documenta Spotify Connect.  
No se embebe ni se piratea Spotify.

## Device Owner (kiosk duro)

```bash
adb shell dpm set-device-owner com.veplayer.app/.kiosk.VeDeviceAdmin
```

(Admin receiver se puede añadir en un siguiente paso; hoy soft lock-task + HOME launcher.)

## Demo UI web

Abre `veplayer/web/index.html` para ver el shell de navegación sin APK.
