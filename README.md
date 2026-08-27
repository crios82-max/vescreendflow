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

## Guía por voz Nav TTS (v0.20)

TextToSpeech (es-VE/ES) con duck de audio:

- Cues por umbral 800/400/150/50 m + intro de ruta + llegada
- Pref `nav_tts` (default ON) · Ajustes → Guía por voz · botón Probar voz
- Chrome muestra última frase TTS

```bash
npm run veplayer:nav-tts-smoke
```

## Guías reverse (v0.21)

Overlay de parking en preview trasera:

- Rieles + bandas rojo/ámbar/verde · curvatura por `steering_angle_deg`
- Auto al gear **R** (y modo Simple trasera) · chip Guías ON/OFF · ancho vías
- Prefs `reverse_guides` / `reverse_guide_track`

```bash
npm run veplayer:reverse-guides-smoke
```

## Export reportes CSV (v0.22)

SenseFlow `/fleet.html` → selector + **Export CSV**:

```
GET /api/fleet/ops/reports/export?kind=devices|commands|alerts|telemetry|summary
```

UTF-8 BOM · `Content-Disposition` attachment · auth session/token (prod estricto).

```bash
npm run veplayer:fleet-csv-smoke
```

## Perfil conductor (v0.23)

Conductores de flota con PIN + destino preferido:

| Código | PIN | Destino |
|--------|-----|---------|
| D001 | 1234 | Altamira |
| D002 | 5678 | Chacao |
| D003 | — | — |

- API: `GET/POST /api/fleet/drivers`, login/logout, ops assign
- Cmd flota `set_driver` `{ "code":"D001" }` o `{ "clear": true }`
- VePlayer Ajustes → Conductor · DriveViz muestra nombre
- CSV `kind=drivers`

```bash
npm run veplayer:driver-smoke
```

## Fase 4 — Alertas voz + inbox (v0.24)

Primer hito fase 4: flota hablada.

- Inbox persistente (ring 40) · TTS en alertas nuevas + cmd `message`
- Frases: geofence / ABS / TPMS / SOC / mensaje
- Ajustes → Flota voz / inbox · DriveViz muestra último aviso
- Prefs `fleet_tts_alerts`, `fleet_tts_messages`, `fleet_alerts`

```bash
npm run veplayer:fleet-inbox-smoke
```

## Trip / shift log (v0.25)

Turnos de conductor con distancia (odómetro o integración):

- `POST /api/fleet/shifts/start|end` · `GET /current` · ops `/api/fleet/ops/shifts`
- Login conductor abre turno · logout / `set_driver clear` cierra
- Heartbeat actualiza `distance_km` vía `odo_km`
- Ajustes: Abrir/Cerrar turno · DriveViz km del turno · CSV `kind=shifts`

```bash
npm run veplayer:shift-smoke
```

## Speed HUD (v0.26)

Badge de límite + color por banda (ok / cerca / exceso):

- DriveViz: dígitos de velocidad colorean · badge circular · TTS exceso sostenido
- Prefs `speed_limit_kmh` (default 50) · Ajustes presets 40/50/60/80
- Cmd flota `set_speed_limit` `{ "kmh": 60 }`

```bash
npm run veplayer:speed-hud-smoke
```

## Crowd en mapa nativo (v0.27)

Actores SenseFlow/visión sobre el canvas OSM:

- `offsetToLatLng` (marco vehículo → geo) · puntos por kind (persona/moto/auto…)
- Chip **Crowd** en mapa · Ajustes toggle · pref `map_crowd`

```bash
npm run veplayer:crowd-map-smoke
```

## Mantenimiento odómetro (v0.28)

Intervalos de servicio por km (aceite, neumáticos, revisión…):

- SenseFlow `fleet_maintenance` · alertas `maint_due` / `maint_warn` en heartbeat
- VePlayer monitor local + TTS/inbox · Ajustes (Hecho / restablecer)
- Cmds `service_done` · `set_maintenance` · CSV `kind=maintenance`

```bash
npm run veplayer:maintenance-smoke
```

## Fuel / Range HUD (v0.29 · Fase 5)

Energía del vehículo (fuel o SOC) + autonomía:

- Bandas ok / near / low · DriveViz coloreado · TTS crítico sostenido
- Prefs `fuel_warn_pct` / `fuel_crit_pct` / `range_warn_km` · Ajustes
- SenseFlow: alertas `fuel_low` · `range_low` (además de `soc_low`)
- Cmd flota `set_fuel_warn` `{ "pct": 15, "range_km": 40 }`

```bash
npm run veplayer:fuel-hud-smoke
```

**Fase 5 (siguiente):** idle alert · panic/SOS · mapa live flota · waypoints.

## Idle / ralentí (v0.30)

Detenido + ignición ON → aviso por tiempo:

- Bandas idle / warn / alert · DriveViz `IDLE · m:ss` · TTS + inbox
- Heartbeat envía `idle_sec` · SenseFlow `idle_warn` / `idle_alert`
- Prefs `idle_warn_sec` (120) · `idle_alert_sec` (300) · cmd `set_idle_warn`

```bash
npm run veplayer:idle-alert-smoke
```

**Fase 5 (siguiente):** panic/SOS · mapa live flota · waypoints.

## Panic / SOS (v0.31)

Botón de emergencia conductor → alerta crítica flota:

- DriveViz: mantener SOS 1.2s · banner activo · TTS
- `POST /api/fleet/panic` · severity `critical` · dedupe 30s
- Heartbeat `panic.open` · cmd `panic_ack` limpia en device + servidor
- Fleet UI: alerta rosa + Ack SOS

```bash
npm run veplayer:panic-sos-smoke
```

**Fase 5 (siguiente):** mapa live flota · waypoints.

## Mapa live flota (v0.32)

Leaflet en `/fleet.html` con unidades, trails y geofences:

- `GET /api/fleet/ops/map` · online/stale · SOS resaltado · fit bounds
- Trails desde `fleet_telemetry` · toggles trails/geofences
- Ajustes VePlayer → **Mapa flota** abre el dashboard

```bash
npm run veplayer:fleet-map-smoke
```

**Fase 5 (siguiente):** waypoints nav.

## Waypoints nav (v0.33)

Ruta multi-parada (vías + destino final):

- SenseFlow `GET /api/nav/route&via=lat,lng;…&via_names=…` · `waypoints` + `legs`
- Mapa nativo: modo **+ Vía** · puntos ámbar · Limpiar vías
- Cmd `nav_dest` acepta `via: [{name,lat,lng}]` · demo flota vía Chacao → Aeropuerto

```bash
npm run veplayer:nav-map-smoke
```

**Fase 5 completa** (fuel · idle · panic · mapa live · waypoints).

## Geofence speed (v0.34 · Fase 6)

Límite de velocidad por zona:

- SenseFlow `fleet_geofences.max_kmh` · heartbeat `speed_zone` · alert `geofence_speed:{id}`
- CRUD/PATCH geofences con límite · mapa flota muestra km/h
- VePlayer: HUD usa límite de zona · TTS entrada/exceso · toggle Ajustes

```bash
npm run veplayer:geofence-speed-smoke
```

**Fase 6 (siguiente):** DTC/OBD · scorecards · OSM prefetch · parking HUD.

## DTC / MIL OBD (v0.35 · Fase 6)

Fallos OBD-II (MIL + códigos):

- Parser Modes `0101` / `03` / `07` / `0A` · `obd_sim` seed P0420/P0301
- Heartbeat `mil` · `dtcs[]` · alertas `mil_on` / `dtc:P0420`
- Cmds flota `seed_dtc` · `read_dtc` · `clear_dtc` · Ajustes Leer/Simular/Limpiar
- DriveViz muestra `MIL · P0420` · TTS inbox

```bash
npm run veplayer:obd-dtc-smoke
```

**Fase 6 (siguiente):** scorecards eco · OSM prefetch · parking HUD.

## Eco scorecards + Phone Link (v0.36 · Fase 6)

### Scorecards eco
Acumuladores en turno (`idle_sec` · `overspeed_sec` · `abs_events` · throttle) → `eco_score` 0–100 / band good|fair|poor.

```bash
npm run veplayer:eco-score-smoke
```

### Phone Link · Android Auto / CarPlay
- BT media + detección paquetes host AA/CarPlay
- Sim demo (Ajustes → Sim AA / Sim CarPlay) · Now Playing `PHONE`
- Heartbeat `phone_link` · flota muestra protocolo
- **Host completo AA/CarPlay = OEM / MFi** (no se finge proyección certificada)

```bash
npm run veplayer:phone-link-smoke
```

**Fase 6 (siguiente):** OSM prefetch · parking HUD.

## OSM offline prefetch (v0.37 · Fase 6)

Prefetch de tiles OSM al disco (`cacheDir/osm_tiles`):

- Ajustes → **Prefetch zona** (GPS/ego ± km) · **Prefetch ruta** (corredor)
- Zoom 12–15 (prefs) · tamaño caché · borrar · progreso en mapa nativo
- Cmd flota `prefetch_tiles` `{ mode: around|route, radius_km }`

```bash
npm run veplayer:osm-prefetch-smoke
```

**Fase 6 (siguiente):** parking distance HUD.

## Parking distance HUD (v0.38 · Fase 6)

PDC / ultrasonidos atrás en reverse:

- Zonas `uss.rear_l/c/r_m` · bands near/warn/crit (~2.5 / 1.5 / 0.6 m)
- Overlay barras L/C/R en cámara reverse · DriveViz `PDC · Xm`
- Sim USS sin sensores · TTS + inbox · alertas flota `parking_near` / `parking_crit`

```bash
npm run veplayer:parking-hud-smoke
```

**Fase 6 completa** (geofence speed · DTC · eco · Phone Link · OSM prefetch · parking HUD).

## Door ajar HUD (v0.39 · Fase 7)

Puerta / baúl / capó abiertos:

- Señales `doors.fl/fr/rl/rr` · trunk · hood
- Bands `ajar` (parado) · `warn` (≥5 km/h) · `alert` (≥20 km/h o reverse)
- DriveViz chip `Puerta · FL` · TTS + inbox · flota `door_ajar` / `door_moving`
- Sim puerta FL en mock (`doorAjarSim`)

```bash
npm run veplayer:door-ajar-smoke
```

**Fase 7 (en curso):** door ajar · shift fatigue · HVAC · cabin overtemp · SOS dashcam.

## Shift fatigue (v0.40 · Fase 7)

Duración de turno abierto → aviso de fatiga:

- Umbrales default **4 h warn** / **8 h alert** (prefs + override `shift_warn_sec` / `shift_alert_sec`)
- DriveViz `Turno · 4h 20m · Xm km` con color por band
- TTS + inbox · flota `shift_warn` / `shift_fatigue`
- Sim horas en Ajustes (demo sin esperar)

```bash
npm run veplayer:shift-fatigue-smoke
```

**Fase 7 (siguiente):** HVAC climate · cabin overtemp · SOS dashcam.

## HVAC climate panel (v0.41 · Fase 7)

Panel clima cabina / objetivo / AC / ventilador:

- Bands `comfort` · `heat` · `cool` (|Δ| ≤ 2.5 °C = confort)
- DriveViz label `24° → 22° · AC · fan 2` + color
- Dock `24° AC` · Ajustes: ± target, AC, fan (override mock/obd_sim)
- Cabina en sim deriva hacia el objetivo

```bash
npm run veplayer:hvac-climate-smoke
```

**Fase 7 (siguiente):** cabin overtemp · SOS dashcam.

## Cabin overtemp (v0.42 · Fase 7)

Aviso de cabina caliente / crítica:

- Umbrales default **32 °C warn** / **38 °C alert**
- DriveViz chip `Cabina · 34°C` · TTS + inbox
- Flota `cabin_warn` / `cabin_overtemp`
- Sim °C en Ajustes (junto a panel HVAC)

```bash
npm run veplayer:cabin-overtemp-smoke
```

**Fase 7 (siguiente):** SOS dashcam clip.

## SOS dashcam clip (v0.43 · Fase 7)

Al disparar SOS se adjunta un frame JPEG de dashcam:

- `POST /api/fleet/panic/clip` (base64) · archivos en `/clips/`
- Payload del panic: `clip_url` · heartbeat `panic.clip_url`
- VePlayer: frame sim branded (CameraX real = hook pendiente) · prefs clip on/sim
- Fleet UI: link «Ver clip SOS»

```bash
npm run veplayer:sos-dashcam-smoke
```

**Fase 7 completa** (door ajar · shift fatigue · HVAC · cabin overtemp · SOS dashcam).

## Seatbelt HUD (v0.44 · Fase 8)

Cinturón conductor desabrochado:

- Bands `unlatched` (parado) · `warn` (≥5 km/h) · `alert` (≥15 km/h o reverse)
- DriveViz chip · TTS + inbox · flota `seatbelt_warn` / `seatbelt_alert`
- Sim en mock (`seatbeltSim`)

```bash
npm run veplayer:seatbelt-smoke
```

**Fase 8 (en curso):** seatbelt · harsh brake · geofence exit · coolant · incident report.

## Harsh brake / accel (v0.45 · Fase 8)

Frenada / aceleración brusca por Δvelocidad (km/h/s):

- Umbrales default freno **12 / 18** · acel **10 / 15** km/h/s
- ABS en desaceleración escala a alert
- DriveViz chip · TTS + inbox · flota `brake_*` / `accel_*`
- Ajustes: «Sim frenada brusca» (one-shot)

```bash
npm run veplayer:harsh-driving-smoke
```

**Fase 8 (siguiente):** geofence exit · coolant · incident report.

## Geofence exit (v0.46 · Fase 8)

Salida de geofence con presencia persistente:

- Tabla `fleet_geofence_presence` · enter solo en transición
- Alerta `geofence_exit:{id}` · TTS «Saliste de la zona»
- Enter ya no se re-dispara mientras sigues dentro

```bash
npm run veplayer:geofence-exit-smoke
```

**Fase 8 (siguiente):** coolant · incident report.

## Coolant overheat (v0.47 · Fase 8)

Refrigerante del motor caliente / crítico:

- Umbrales default **105 °C warn** / **115 °C alert**
- DriveViz chip `Motor · 108°C` · TTS + inbox
- Flota `coolant_warn` / `coolant_overheat` · sim °C en Ajustes

```bash
npm run veplayer:coolant-overheat-smoke
```

**Fase 8 (siguiente):** incident report.

## Incident report (v0.48 · Fase 8)

Reporte manual de incidente (no SOS):

- `POST /api/fleet/incident` · categorías accident / breakdown / traffic / other
- Nota + clip JPEG opcional · soft dedupe 45s
- Accidente = severity critical · resto warn
- Ajustes: categoría · nota · enviar · clip on/off

```bash
npm run veplayer:incident-report-smoke
```

**Fase 8 completa** (seatbelt · harsh · geofence exit · coolant · incident).

## Unauthorized movement / tow (v0.49 · Fase 9)

Movimiento con vehículo «asegurado» (ign off o freno de mano):

- Umbrales default **3s warn / 8s alert** · min **3 km/h**
- DriveViz chip `Remolque · Xm km/h` · TTS + inbox
- Flota `tow_warn` / `tow_alert` · sim en Ajustes

```bash
npm run veplayer:tow-detect-smoke
```

## Sudden fuel drop (v0.50 · Fase 9)

Caída brusca de `fuel_pct` en ventana corta (robo / fuga):

- Umbrales default **−8% warn / −15% alert** · ventana **60s** (pico→actual)
- DriveViz chip `Combustible · −X% · Y%` · TTS + inbox
- Flota `fuel_drop_warn` / `fuel_drop_alert` · sim drop % en Ajustes

```bash
npm run veplayer:fuel-drop-smoke
```

## TPMS por rueda (v0.51 · Fase 9)

Presión FL/FR/RL/RR con umbrales:

- Default **&lt;28 psi warn / &lt;24 psi alert**
- DriveViz detalle `FL · FR · RL · RR` + chip alarma · TTS + inbox
- Flota `tpms_warn` / `tpms_alert` (legacy `tpms_low` si solo flag) · sim FL en Ajustes

```bash
npm run veplayer:tpms-hud-smoke
```

## End-of-shift summary (v0.52 · Fase 9)

Al cerrar turno: digest duración · km · eco · ralentí:

- `POST /api/fleet/shifts/end` → `{ shift, summary }` · alerta flota `shift_summary`
- `GET /api/fleet/shifts/:id/summary`
- DriveViz chip `Resumen · …` · TTS + inbox · toggles en Ajustes

```bash
npm run veplayer:shift-summary-smoke
```

## Message reply / ack (v0.53 · Fase 9)

Conductor confirma o responde mensajes de despacho:

- Cmd `message` crea alerta `message` + `alert_id` en payload
- `POST /api/fleet/message/ack` · `POST /api/fleet/message/reply` (canned: ok / recibido / en_camino / retraso / ayuda)
- Reply → alerta ops `message_reply` · parent se marca acked
- DriveViz chip pendiente · Ajustes Ack + respuestas rápidas · TTS

```bash
npm run veplayer:message-reply-smoke
```

**Fase 9 completa** (tow · fuel drop · TPMS · shift summary · message reply).

## Battery 12V (v0.54 · Fase 10)

Voltaje del módulo / batería 12V (OBD **0142** / CAN):

- Default **&lt;12.0 V warn / &lt;11.5 V alert**
- DriveViz chip `Bat · X.X V` · TTS + inbox
- Flota `battery_warn` / `battery_crit` · sim V en Ajustes

```bash
npm run veplayer:battery-voltage-smoke
```

**Fase 10 (en curso):** battery 12V · impact detect · rest break · route deviation · driver scorecard.

## Impact detect (v0.55 · Fase 10)

Candidato a colisión: decel extrema o yaw spike (sobre umbrales harsh):

- Default decel **28/40 km/h/s** · yaw **80/120 °/s** · min **8 km/h**
- DriveViz chip `Impacto · …` · TTS + inbox
- Flota `impact_warn` / `impact_alert` · sim en Ajustes

```bash
npm run veplayer:impact-detect-smoke
```

## Rest break (v0.56 · Fase 10)

Pausa tras conducción continua (no turno total):

- Default **2 h warn / 2.5 h alert** · reset tras **15 min** parado (≥5 km/h = conduciendo)
- DriveViz chip · TTS + inbox
- Flota `rest_warn` / `rest_break` · sim min en Ajustes

```bash
npm run veplayer:rest-break-smoke
```

## Route deviation (v0.57 · Fase 10)

Desvío respecto a la polyline de nav activa:

- Default **80 m warn / 150 m alert** · hold **8 s** (anti-jitter GPS)
- DriveViz chip `Desvío · Xm` / `Fuera ruta · Xm` · TTS + inbox
- Flota `route_warn` / `route_deviate` · sim m en Ajustes (Navegación)

```bash
npm run veplayer:route-deviation-smoke
```

## Driver scorecard (v0.58 · Fase 10)

Score de **seguridad** del turno (aparte del eco):

- Acumula harsh brake/accel · overspeed · seatbelt · impact · route deviation → **0–100**
- Bands `good` (≥80) / `fair` (≥60) / `poor` · warn **&lt;70** / alert **≤50**
- DriveViz `Score XX · band` · TTS + inbox · flota `score_warn` / `score_alert`
- Sim score en Ajustes → Conductor

```bash
npm run veplayer:driver-scorecard-smoke
```

**Fase 10 completa** (battery 12V · impact · rest break · route deviation · driver scorecard).

## RPM over-rev (v0.59 · Fase 11)

Régimen motor alto (OBD **010C**):

- Default **≥4500 warn / ≥5500 alert**
- DriveViz `RPM · Nnnn` · TTS + inbox
- Flota `rpm_warn` / `rpm_alert` · sim en Ajustes (Clima HVAC)

```bash
npm run veplayer:rpm-overrev-smoke
```

**Fase 11 (en curso):** RPM over-rev · ice/frost outdoor · parking-brake moving · turn stuck · ABS HUD.

## Ice / frost outdoor (v0.60 · Fase 11)

Riesgo de hielo / escarcha (OBD ambient **0146** / `outdoor_temp_c`):

- Default **≤3 °C warn / ≤0 °C alert**
- DriveViz `Ext · N°C` · TTS + inbox
- Flota `ice_warn` / `ice_alert` · sim °C en Ajustes (Clima HVAC)

```bash
npm run veplayer:ice-frost-smoke
```

**Fase 11 (en curso):** RPM over-rev · ice/frost outdoor · parking-brake moving · turn stuck · ABS HUD.

## Parking-brake moving (v0.61 · Fase 11)

EPB / freno de estacionamiento activado en marcha (error de conductor, ≠ remolque):

- Default **≥5 km/h warn / ≥15 km/h alert**
- DriveViz `Freno · N km/h` · TTS + inbox
- Flota `pbrake_warn` / `pbrake_alert` · sim en Ajustes (Remolque)

```bash
npm run veplayer:pbrake-moving-smoke
```

**Fase 11 (en curso):** RPM over-rev · ice/frost · parking-brake moving · turn stuck · ABS HUD.

## Turn signal stuck (v0.62 · Fase 11)

Intermitente LEFT/RIGHT olvidado en marcha (no hazards):

- Default **≥30 s warn / ≥60 s alert** · min **5 km/h** · reset al apagar o viraje fuerte
- DriveViz `Inter · Izq/Der` · TTS + inbox
- Flota `turn_stuck_warn` / `turn_stuck_alert` · sim s en Ajustes (Remolque)

```bash
npm run veplayer:turn-stuck-smoke
```

**Fase 11 (en curso):** RPM · ice · p-brake · turn stuck · ABS HUD.

## ABS HUD (v0.63 · Fase 11)

Intervención ABS / ESC con hold + ráfaga de eventos:

- Default warn **≥0.5 s** activo / alert **≥2 s** o **≥3 eventos / 60 s**
- DriveViz `ABS · …` · TTS + inbox
- Flota `abs_warn` / `abs_alert` (alias legacy `abs`) · sim en Ajustes (Mock)

```bash
npm run veplayer:abs-hud-smoke
```

**Fase 11 completa** (RPM · ice/frost · parking-brake · turn stuck · ABS HUD).

## High throttle (v0.64 · Fase 12)

Acelerador abierto / WOT (OBD **0111**):

- Default **≥70% warn / ≥85% alert** · o ≥70% durante **8 s** · min **20 km/h**
- DriveViz `Acel · N%` · TTS + inbox
- Flota `throttle_warn` / `throttle_alert` · sim % en Ajustes (Clima HVAC)

```bash
npm run veplayer:high-throttle-smoke
```

**Fase 12 (en curso):** high throttle · hazard stuck · gear roll · eco live · engine runtime.

## Hazard stuck (v0.65 · Fase 12)

Luces de emergencia olvidadas en marcha:

- Default **≥45 s warn / ≥90 s alert** · min **5 km/h**
- DriveViz `Hazard · Ns` · TTS + inbox
- Flota `hazard_stuck_warn` / `hazard_stuck_alert` · sim s en Ajustes (Remolque)

```bash
npm run veplayer:hazard-stuck-smoke
```

**Fase 12 (en curso):** high throttle · hazard stuck · gear roll · eco live · engine runtime.

## Gear roll (v0.66 · Fase 12)

Rodando en **P** o **N** (sin marcha engarzada):

- Default **≥5 km/h warn / ≥20 km/h alert**
- DriveViz `N · X km/h` · TTS + inbox
- Flota `gear_roll_warn` / `gear_roll_alert` · sim en Ajustes (Remolque)

```bash
npm run veplayer:gear-roll-smoke
```

**Fase 12 (en curso):** high throttle · hazard stuck · gear roll · eco live · engine runtime.

## Eco live (v0.67 · Fase 12)

Avisos en vivo del **eco_score** del turno (aparte del score de seguridad):

- Warn **&lt;70** / alert **≤50** · bands good/fair/poor
- DriveViz `Eco XX · band` · TTS + inbox
- Flota `eco_warn` / `eco_alert` · sim en Ajustes (Conductor)

```bash
npm run veplayer:eco-live-smoke
```

**Fase 12 (en curso):** high throttle · hazard stuck · gear roll · eco live · engine runtime.

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
