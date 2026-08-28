# Ride — App móvil (iPhone)

Desarrollo desde iPhone con **Expo Go** mientras estás de viaje. Pruebas completas con API en el Mac mini al regresar.

## Tema Movify

Colores en `src/theme.ts` (negro + lima `#A3E635`), alineado con la web.

- iPhone con **Expo Go** ([App Store](https://apps.apple.com/app/expo-go/id982107779))
- Mac con Node 20+ (puede estar en casa; ver tunnel abajo)
- Google Maps API key con **Places API**

## Setup rápido

```bash
cd ride-app
cp .env.example .env
# EXPO_PUBLIC_API_URL=http://IP_DE_TU_MAC:4001
# EXPO_PUBLIC_GOOGLE_MAPS_API_KEY=tu_key

npm install --legacy-peer-deps
npm run dev:mobile
# o: cd apps/mobile && npx expo start
```

Escanea el QR con la cámara del iPhone → abre en Expo Go.

## Desde iPhone (sin Mac a mano)

Si el Mac mini está en casa y encendido:

1. **Tunnel al API** (en el Mac): Cloudflare tunnel o similar → `https://ride-api.tudominio.com`
2. En el iPhone, en la pantalla de login → **Configurar servidor API** → pega la URL pública
3. Metro bundler: en el Mac `npx expo start --tunnel` para cargar JS desde cualquier red

Sin API solo puedes ver UI/auth; los viajes requieren backend.

## En la app

| Pantalla | Qué hace |
|----------|----------|
| Login | Pasajero o conductor |
| Pasajero | Places origen/destino, pedir ride, tracking, pago mock |
| Conductor | Online/offline, aceptar viajes, estados |

La sesión se guarda en el iPhone (SecureStore). La URL del API se guarda en ajustes.

## Build para App Store / Play Store

```bash
cd apps/mobile
npm install -g eas-cli
eas login
eas build:configure   # ya hay eas.json

# Preview APK/IPA (test interno)
npm run build:preview

# Producción
npm run build:production
npm run submit:ios      # requiere Apple Developer + ascAppId en eas.json
npm run submit:android  # requiere google-play-service-account.json
```

Edita `eas.json` con tu Apple ID, Team ID y cuenta de Google Play antes de submit.

## Variables

| Variable | Uso |
|----------|-----|
| `EXPO_PUBLIC_API_URL` | Backend (default `http://localhost:4001`) |
| `EXPO_PUBLIC_GOOGLE_MAPS_API_KEY` | Mapa + Places |

También edita `app.config.js` / keys nativas en iOS si haces build standalone.

## Al regresar (prueba full)

```bash
# Mac mini — desde la raíz del repo
./macmini-stacks/bootstrap-ride-app.sh

# iPhone: API → http://IP_LOCAL_MAC:4001
```

Ver guía completa: `docs/APP_STORE.md`

## Comandos

```bash
npm run dev:mobile          # desde ride-app/
npx expo start --tunnel     # QR funciona fuera de la misma WiFi
npx expo start --ios        # simulador (solo Mac)
```
