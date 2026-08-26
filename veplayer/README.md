# VePlayer — OS de reproductor para vehículos

Launcher kiosk Android para head-units / tablets de flota.

| Módulo | Qué hace |
|--------|----------|
| **Cámaras** | Dual ConcurrentCamera · front/back/USB EXTERNAL (Camera2) · **360 bird’s-eye** |
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

## Campo real (v0.13)

Comisionar head-unit / tablet de flota con APK **release firmado**:

```bash
# 1) Keystore de campo (local, no se sube a git)
veplayer/scripts/gen-field-keystore.sh

# 2) Build (en máquina con Android SDK)
cd veplayer/android
# opcional: SenseFlow de flota
echo 'SENSEFLOW_URL=https://sense.tu-dominio.com' >> local.properties
./gradlew :app:assembleRelease

# 3) Instalar + Device Owner + permisos
../scripts/field-deploy.sh \
  app/build/outputs/apk/release/app-release.apk \
  com.veplayer.app
```

En la unidad: **Ajustes (PIN) → Campo → Diagnóstico** (cámaras, USB, BT, CAN/OBD, SenseFlow, kiosk).

Remoto: cmd `run_diag` desde `/fleet.html` o:

```bash
npm run veplayer:field-smoke
curl -X POST http://127.0.0.1:4100/api/fleet/command \
  -H 'content-type: application/json' \
  -d '{"device_id":"…","command":"run_diag"}'
```

Checklist campo:

1. Sin cuentas Google (o wipe) → Device Owner OK  
2. Release `com.veplayer.app` (no `.debug`)  
3. CAN/OBD/USB visibles en diag  
4. Heartbeat flota + OTA auto  
5. Lock Task tras boot  

## OTA prod (v0.13)

SenseFlow sirve APKs desde `senseflow/ota/` en `/ota/…`:

```bash
# tras assembleRelease
veplayer/scripts/publish-ota.sh \
  veplayer/android/app/build/outputs/apk/release/app-release.apk \
  0.13.0 15 "campo release"

# opcional: encolar OTA silenciosa a unidades desactualizadas
ROLLOUT=1 veplayer/scripts/publish-ota.sh … 0.13.0 15

# o:
curl -X POST http://127.0.0.1:4100/api/fleet/ota/rollout \
  -H 'content-type: application/json' \
  -d '{"version_code":15,"silent":true}'

npm run veplayer:ota-smoke
```

`PUBLIC_BASE` = URL que ven las unidades (túnel Cloudflare / LAN). Default = `SENSEFLOW_URL`.

## DBC real (v0.14)

Decoder CAN carga un **DBC** (BO_/SG_) en vez del mapa hardcode:

- Asset demo: `assets/dbc/veplayer_demo.dbc` (IDs 0x100–0x108 / 256–264)
- Ajustes → **Demo DBC** / **Desde SenseFlow** (`/dbc/veplayer_demo.dbc`)
- Campo OEM: `prefs.dbcSource = file:/…/custom.dbc`
- Flota: cmd `set_dbc` `{ "url": "https://…/oem.dbc" }`

```bash
npm run veplayer:dbc-smoke
curl http://127.0.0.1:4100/dbc/veplayer_demo.dbc | head
```

Aliases de señales: `Speed_Kmh`, `Gear`, `SOC`, `TPMS_FL`, `HVAC_Cabin`, `ABS`, …

## Radio FM hardware (v0.15)

Capa FM aparte del stream IP:

- Backends: **HAL** (`RadioManager` reflection) → fallback **sim**
- Radio screen: tabs **FM** / **IP Stream** · dial ± · seek · presets Caracas
- Dock next/prev = seek FM cuando `MediaSource.FM`
- Ajustes: `fm_backend` auto|hal|sim
- Flota: `fm_tune` `{ "mhz": 95.5 }` o `{ "preset": "fm-955" }`

```bash
npm run veplayer:fm-smoke
```

En HU con chip FM real, `listModules()` no vacío → HAL; si no, sim con RDS fake.

## Mapa nativo (v0.16) + tiles OSM (v0.19)

Cockpit **Compose** (sin WebView por defecto):

- Polyline desde `NavEngine.geometry` · ego chevron (heading) · destino
- Chips de destinos SenseFlow `/api/nav/destinations`
- Chrome ETA / próximo giro
- **Tiles OSM** (Web Mercator) bajo la ruta · cache disco · Ajustes → Tiles OSM
- Fallback WebView: Ajustes → Mapa → WebView (`map_mode=web`)

```bash
npm run veplayer:nav-map-smoke
npm run veplayer:osm-tiles-smoke
```

## Flota ops (v0.17)

Roles + reportes + historial en SenseFlow `/fleet.html`:

| Rol | Token demo | Puede |
|-----|------------|--------|
| admin | `fleet-admin-demo` | todo (incl. wipe) |
| dispatcher | `fleet-dispatch-demo` | cmds salvo wipe |
| viewer | `fleet-viewer-demo` | solo lectura |

```bash
# Header en API
curl -H 'x-fleet-token: fleet-viewer-demo' http://127.0.0.1:4100/api/fleet/ops/me
curl -H 'x-fleet-token: fleet-admin-demo' http://127.0.0.1:4100/api/fleet/ops/reports/summary
curl http://127.0.0.1:4100/api/fleet/ops/commands/history?limit=20
curl http://127.0.0.1:4100/api/fleet/ops/ota/history

npm run veplayer:fleet-ops-smoke
```

Dashboard: selector de rol · cards reporte 24h · historial cmds/OTA · wipe oculto si no admin.

## Auth flota real (v0.18)

Passwords **scrypt**, API tokens **SHA-256** at rest, sesiones 12h.

| Usuario | Clave | Rol |
|---------|-------|-----|
| `admin` | `admin123` | admin |
| `despacho` | `dispatch123` | dispatcher |
| `viewer` | `viewer123` | viewer |

Tokens API demo (hasheados en DB): `fleet-admin-demo` · `fleet-dispatch-demo` · `fleet-viewer-demo`

```bash
curl -X POST http://127.0.0.1:4100/api/fleet/ops/login \
  -H 'content-type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
# → { session }  → header x-fleet-session

# Prod estricto (sin anon):
FLEET_OPEN_MODE=0 npm run start --prefix senseflow/server

npm run veplayer:fleet-auth-smoke
```

`/fleet.html` pide login (usuario/clave o token).

## Tiles OSM nativos (v0.19)

Mapa Compose con rasters OSM alineados (Web Mercator):

- `WebMercator` + `OsmTileStore` (cache disco/memoria, User-Agent VePlayer)
- Overlay ruta / ego / destino encima de tiles + scrim nocturno
- Prefs: `map_tiles` (default ON), `map_tile_url` con `{z}/{x}/{y}`

```bash
npm run veplayer:osm-tiles-smoke
```

## Device Owner (kiosk duro · v0.12)

Playbook en tablet / head-unit **sin cuentas Google** (factory reset si hace falta):

```bash
cd veplayer/android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# package debug suele ser com.veplayer.app (mismo applicationId)
../scripts/enable-device-owner.sh com.veplayer.app
```

Qué aplica el owner:

- Lock Task whitelist (VePlayer + Spotify + YouTube) · sin Home/Overview
- Restricciones: safe boot · add user · factory reset
- Keyguard + status bar off · uninstall block
- Home preferido = MainActivity
- Watchdog cada 20s (Sense + UI + re-Lock) + alarm keep-alive
- **OTA silenciosa** de flota (`auto_ota` ON · Device Owner + PackageInstaller)

En Ajustes (PIN): checklist kiosk · Aplicar políticas · Lock Task · toggle OTA auto.

```bash
npm run veplayer:kiosk-smoke   # SenseFlow cmds lock_task / apply_kiosk / ota silent
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

## Flota pro (v0.11+)

- Geofences (`GET/POST /api/fleet/geofences`)
- Alertas ABS / TPMS / SOC / geofence enter (`GET /api/fleet/alerts`, ack)
- Historial telemetría (`GET /api/fleet/telemetry/:deviceId`)
- Comandos: `set_source` · `reboot_obd` · `nav_dest` · **`lock_task`** · **`apply_kiosk`** · **`ota`** (`silent`, `version_code`)
- Dashboard `/fleet.html` con alertas, fences y spark de velocidad
- Heartbeat incluye `vehicle_signals.kiosk` (owner / lock / OTA status)

```bash
curl -s http://127.0.0.1:4100/api/fleet/alerts
curl -s -X POST http://127.0.0.1:4100/api/fleet/command \
  -H 'content-type: application/json' \
  -d '{"device_id":"…","command":"set_source","payload":{"source":"can"}}'
```

SenseFlow proxy OSRM:

```bash
curl "http://127.0.0.1:4100/api/nav/route?from_lat=10.496&from_lng=-66.898&to_lat=10.4965&to_lng=-66.8492&dest_name=Altamira"
curl http://127.0.0.1:4100/api/nav/destinations
```

- Mapa: selector de destino · polyline · cards ETA / próximo giro
- VePlayer: `NavEngine` + chrome cockpit live · prefs destino
- Fallback haversine si OSRM no responde (`OSRM_URL` opcional)

Modo **360** en Cámaras:
- Grid front / rear (ConcurrentCamera) + placeholders left/right (USB UVC)
- Panel central **bird’s-eye** con FOV wedges (pseudo-stitch) + actores SenseFlow/visión
- Calibración `maxAheadM` / `maxLatM` (prefs) — mismos metros que DriveViz

Simple y Dual siguen disponibles. Al abrir Cámaras se pausa SurroundVision para liberar CameraX.

`VeMediaHub` — una sola sesión Now Playing para:
- **Radio** (ExoPlayer compartido + audio focus)
- **Spotify** App Remote (play/pause/skip + player state)
- **DriveViz** widget (título / artista / play / skip)
- **Dock** play/pause · next · mute · temp HVAC

Radio y Spotify se ceden el foco: al reproducir radio se pausa Spotify y viceversa.

| Fuente (`Ajustes`) | Qué usa |
|--------------------|---------|
| **gps** | Fused Location → velocidad (+ heading) |
| **mock** | Ciclo CAN sintético (velocidad, gear, turn, SOC, RPM…) |
| **can** | Auto: CarProperty → USB SLCAN → SocketCAN JNI → `can_sim` |
| **obd** | ELM327 Bluetooth Classic RFCOMM (SPP) · fallback `obd_sim` |

Señales en `VehicleSignals`: speed, gear P/R/N/D, turn, puertas, parking brake, SOC/fuel, RPM, steering, coolant, outdoor temp, ignition, heading, yaw, odometer, range, **ABS**, **TPMS** (4 ruedas), **HVAC** (cabin/target/AC/fan), throttle.

### CAN real (v0.7)

Backends (`Ajustes` → CAN backend):

| Backend | Qué hace |
|---------|----------|
| **auto** | Prueba Car → USB → Socket → sim |
| **car** | Android Automotive `CarPropertyManager` (reflection) |
| **usb** | USB host **SLCAN** (`tIIILDD…`) |
| **socket** | SocketCAN `can0` vía `libveplayer_can.so` |
| **sim** | Frames demo 0x100–0x108 |

DBC-lite (`CanSignalDecoder`): speed · gear · turn · doors · SOC/fuel · steer/RPM · ABS/flags · TPMS · HVAC.

```bash
npm run veplayer:can-smoke
```

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
- **OTA**: PackageInstaller silent (Device Owner) + auto desde heartbeat
- **Watchdog** kiosk: Sense + UI stale 60s + re-Lock + AlarmManager keep-alive
- **Audio focus** en Radio
- **Reverse mock** → Cámaras · **video lock** en movimiento

```bash
# Comando remoto
curl -s -X POST http://127.0.0.1:4100/api/fleet/command \
  -H 'content-type: application/json' \
  -d '{"device_id":"…","command":"message","payload":{"text":"Hola"}}'
# Dashboard: http://127.0.0.1:4100/fleet.html
```
