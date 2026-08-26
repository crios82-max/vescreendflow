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

## Señales vehículo (CAN / OBD / GPS)

| Fuente (`Ajustes`) | Qué usa |
|--------------------|---------|
| **gps** | Fused Location → velocidad (+ heading) |
| **mock** | Ciclo CAN sintético (velocidad, gear, turn, SOC, RPM…) |
| **can** | Stub SocketCAN/USB-CAN / CarPropertyManager → hoy `can_stub` demo |
| **obd** | ELM327 Bluetooth Classic RFCOMM (SPP) · fallback `obd_sim` |

Señales en `VehicleSignals`: speed, gear P/R/N/D, turn, puertas, parking brake, SOC/fuel, RPM, steering, coolant, outdoor temp, ignition, heading, yaw, odometer, range, **ABS**, **TPMS** (4 ruedas), **HVAC** (cabin/target/AC/fan), throttle.

### OBD ELM327

1. Emparejá el dongle en Ajustes del sistema Android (Bluetooth Classic).
2. En VePlayer → Ajustes (PIN) → fuente **OBD** → tocá el dispositivo emparejado (o pegá la MAC).
3. PIDs: `010D` speed · `010C` RPM · `0105` coolant · `012F` fuel · `0146` ambient · `0111` throttle.
4. Sin dongle / fallo BT → simula PIDs + ABS/TPMS/HVAC demo (`obd_sim`).

Permisos: `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` (API 31+).

Heartbeat flota manda `vehicle_signals` → dashboard `/fleet.html`.

```bash
curl -s -X POST http://127.0.0.1:4100/api/fleet/heartbeat \
  -H 'content-type: application/json' \
  -d '{"device_id":"…","vehicle_signals":{"speed_mps":12.5,"gear":"D","turn":"left","battery_soc_pct":71,"source":"mock"}}'
```

## Surround live (panel izquierdo)

Pipeline:
1. **Cámara** (MediaPipe EfficientDet) → personas / motos / autos / buses / trucks  
2. **SenseFlow** `GET /api/surround?lat=&lng=` → pings cercanos en metros relativos  
3. **SurroundEngine** fusiona visión (cerca) + SenseFlow (lejos)  
4. **DriveVizPanel** dibuja actores en bird’s-eye

```bash
curl "http://127.0.0.1:4100/api/surround?lat=10.496&lng=-66.898&radius_m=120"
```

- **Ajustes + PIN** (default `1234`)
- **Flota**: register / heartbeat / pair / devices / **commands**
- **OTA**: PackageInstaller (+ heartbeat `update_available`)
- **Watchdog** auto-recover Sense + UI
- **Audio focus** en Radio
- **Reverse mock** → Cámaras · **video lock** en movimiento

```bash
# Comando remoto
curl -s -X POST http://127.0.0.1:4100/api/fleet/command \
  -H 'content-type: application/json' \
  -d '{"device_id":"…","command":"message","payload":{"text":"Hola"}}'
# Dashboard: http://127.0.0.1:4100/fleet.html
```
