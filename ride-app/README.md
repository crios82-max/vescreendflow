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
- **Google Maps API Key** con Maps JavaScript API + Maps SDK (Android/iOS)

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

## Flujo demo

1. Registra un **pasajero** en http://localhost:5174
2. Registra un **conductor** en http://localhost:5175
3. Conductor → **Ir online**
4. Pasajero → elige origen/destino en mapa → **Pedir Ride**
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
- Geocoding con Google Places
- Push notifications
- Panel admin
