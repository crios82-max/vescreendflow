# Ride App — MVP tipo Uber

Monorepo con backend Node/Express, apps web (pasajero + conductor), app móvil Expo y pagos mock.

## Stack

| Capa | Tecnología |
|------|------------|
| API | Node + Express + Socket.io + PostgreSQL |
| Web pasajero | React + Vite + Google Maps (`:5174`) |
| Web conductor | React + Vite + Google Maps (`:5175`) |
| Móvil | Expo + React Native Maps (Google) |
| Pagos | Mock (tarjeta `•••• 4242`, siempre aprueba) |

## Requisitos

- Node 20+
- Docker (PostgreSQL)
- **Google Maps API Key** con Maps JavaScript API, **Places API** y Maps SDK (Android/iOS)

## Setup rápido

```bash
cd ride-app
cp .env.example .env
# Edita .env con JWT_SECRET y VITE_GOOGLE_MAPS_API_KEY

docker compose up -d
npm install
npm run build -w @ride-app/shared
npm run dev
```

Servicios:
- API: http://localhost:4001
- Pasajero web: http://localhost:5174
- Conductor web: http://localhost:5175

### Móvil

```bash
cd apps/mobile
# En .env raíz ya tienes EXPO_PUBLIC_API_URL y EXPO_PUBLIC_GOOGLE_MAPS_API_KEY
# Actualiza app.json con las API keys nativas de Google
npm run start
```

> En dispositivo físico usa la IP de tu máquina en `EXPO_PUBLIC_API_URL` (ej. `http://192.168.1.10:4001`).

## Producción con PM2 (Mac mini)

```bash
cd ride-app
cp .env.example .env   # JWT_SECRET, Google Maps key, CORS si accedes por IP/LAN
docker compose up -d
npm install --legacy-peer-deps
npm run start:prod     # build + pm2 start

# o si ya hiciste build:
npm run pm2:start
npm run health
```

| Proceso PM2 | Puerto |
|-------------|--------|
| `ride-api` | 4001 |
| `ride-passenger` | 5174 |
| `ride-driver` | 5175 |
| Postgres (Docker) | 5436 |

```bash
pm2 list
npm run pm2:logs
npm run pm2:restart
npm run pm2:stop
```

Si accedes desde otra máquina en la red, agrega las URLs en `CORS_ORIGINS` del `.env`.

### Autostart Mac mini (un comando)

```bash
# desde la raíz del repo, en el Mac mini:
chmod +x macmini-stacks/install-ride-app.sh
./macmini-stacks/install-ride-app.sh
```

Instala el bloque en `com.macmini.stacks.autostart` y levanta el stack. Detalle: [`docs/macmini-autostart.md`](docs/macmini-autostart.md) y [`../macmini-stacks/README.md`](../macmini-stacks/README.md).

## Flujo demo

1. Registra un **pasajero** en http://localhost:5174
2. Registra un **conductor** en http://localhost:5175
3. Conductor → **Ir online**
4. Pasajero → busca origen/destino con **Google Places** → **Pedir Ride**
5. Conductor → **Aceptar** → actualiza estados hasta **Completar**
6. Pasajero → **Pagar con tarjeta mock**

## Estructura

```
ride-app/
├── apps/
│   ├── api/          # Backend REST + WebSockets
│   ├── passenger/    # Web pasajero
│   ├── driver/       # Web conductor
│   └── mobile/       # Expo (ambos roles)
├── packages/
│   ├── shared/       # Tipos y utilidades
│   └── web-shared/   # Auth, API client, MapView
└── db/init.sql
```

## Próximos pasos

- Stripe real (reemplazar `/rides/:id/pay`)
- Push notifications
- Panel admin
