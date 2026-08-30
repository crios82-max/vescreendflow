# Movify — app de viajes (tipo Uber)

Monorepo con backend Node/Express, apps web (pasajero + conductor + admin), app móvil Expo, Google Directions, matching automático, calificaciones, push y pagos Stripe/mock.

Marca y dominios: [docs/BRAND.md](docs/BRAND.md) · Go live: [docs/REGRESO-18SEP.md](docs/REGRESO-18SEP.md)

## Stack

| Capa | Tecnología |
|------|------------|
| API | Node + Express + Socket.io + PostgreSQL |
| Web pasajero | React + Vite + Google Maps (`:5174`) |
| Web conductor | React + Vite + Google Maps (`:5175`) |
| Web admin | React + Vite (`:5176`) |
| Móvil | Expo + React Native Maps (Google) |
| Pagos | Stripe test (`pm_card_visa`) o mock `•••• 4242` |
| Push | Expo push notifications |

## Features

- Google Places autocomplete (web + móvil)
- Google Directions: ruta en mapa + ETA real (fallback haversine sin API key)
- **ETA en vivo** al pickup/destino (socket + polling)
- Matching automático inteligente (distancia + rating)
- Historial de viajes + calificaciones (1–5 estrellas)
- Tipos de vehículo: Standard, Confort, XL, Vans
- **Surge pricing** dinámico por demanda
- **Propinas**, **promos** (`BIENVENIDA`, `RIDE5`), **wallet**
- **Viajes programados**, paradas múltiples, viaje para otro
- **Lugares guardados** (Casa, Trabajo)
- **Compartir viaje** (link público `/share/:token`)
- **Chat** pasajero ↔ conductor
- **Botón SOS** + alertas admin
- **Onboarding conductor** con aprobación admin
- **Cancelaciones** con fee + re-asignación si cancela conductor
- Recibos (mock email log)
- Stripe / mock + PaymentIntent para móvil
- Panel admin: stats, viajes, aprobar conductores, ban, reembolsos, SOS

## Requisitos

- Node 20+
- Docker (PostgreSQL)
- **Google Maps API Key** con Maps JavaScript API, **Places API**, **Directions API** y Maps SDK (Android/iOS)

## Setup rápido

```bash
cd movify
cp .env.example .env
# Edita .env: JWT_SECRET, VITE_GOOGLE_MAPS_API_KEY, GOOGLE_MAPS_API_KEY

docker compose up -d
npm install --legacy-peer-deps
npm run build -w @ride-app/shared
npm run dev
```

Servicios:
- API: http://localhost:4001
- Pasajero web: http://localhost:5174
- Conductor web: http://localhost:5175
- Admin: http://localhost:5176

### Admin

Marca un usuario como admin en la DB:

```sql
UPDATE users SET is_admin = true WHERE email = 'tu@email.com';
```

Luego entra en http://localhost:5176 con ese usuario.

### Móvil

```bash
npm run dev:mobile
# o con tunnel para iPhone en viaje:
npm run dev:mobile:tunnel
```

> En dispositivo físico usa la IP de tu máquina en `EXPO_PUBLIC_API_URL` (ej. `http://192.168.1.10:4001`).

### Migraciones

Aplica todas las migraciones pendientes (idempotente, trackea en `schema_migrations`):

```bash
docker compose up -d
./scripts/migrate.sh
```

Incluye migraciones hasta `008_phase3.sql` (Twilio Voice, call sessions).

### Producción (Stripe Connect, SMTP, OTP)

En `.env`:

| Variable | Para qué |
|----------|----------|
| `STRIPE_SECRET_KEY` | Pagos + Connect payouts |
| `SMTP_*` | Recibos por email |
| `TWILIO_*` | OTP SMS (sin esto: mock `123456` en dev) |
| `PLATFORM_FEE_PERCENT` | Comisión plataforma (default 25%) |

Conductores: web `:5175` → **Configurar cobros (Stripe)** → onboarding Express.

## Producción con PM2 (Mac mini)

Desde la raíz del repo (`vescreendflow`):

```bash
git pull origin main
chmod +x macmini-stacks/bootstrap-ride-app.sh
./macmini-stacks/bootstrap-ride-app.sh
```

O manual desde `movify/`:

```bash
npm install --legacy-peer-deps
docker compose up -d
./scripts/migrate.sh
npm run start:prod
npm run health
```

| Proceso PM2 | Puerto |
|-------------|--------|
| `ride-api` | 4001 |
| `ride-passenger` | 5174 |
| `ride-driver` | 5175 |
| `ride-admin` | 5176 |
| Postgres (Docker) | 5436 |

### Autostart Mac mini

```bash
git pull origin main
chmod +x macmini-stacks/bootstrap-ride-app.sh
./macmini-stacks/bootstrap-ride-app.sh
```

Ver `docs/macmini-autostart.md` y `../macmini-stacks/README.md`.

## Flujo demo

```bash
npm run seed:demo   # o viene incluido en prep:local
```

| Rol | Email | Password |
|-----|-------|----------|
| Pasajero | pasajero@movify.demo | movify123 |
| Conductor | conductor@movify.demo | movify123 |
| Admin | admin@movify.demo | movify123 |

1. Conductor (:5175) → login → **Ir online**
2. Pasajero (:5174) → origen/destino → **Pedir ride** (mismo tipo Standard)
3. Conductor → estados hasta **Completar**
4. Pasajero → **Pagar** → **Calificar**
5. Admin (:5176) → stats e historial

## Estructura

```
movify/
├── apps/
│   ├── api/          # Backend REST + WebSockets
│   ├── passenger/    # Web pasajero
│   ├── driver/       # Web conductor
│   ├── admin/        # Panel admin
│   └── mobile/       # Expo (ambos roles)
├── packages/
│   ├── shared/       # Tipos y utilidades
│   └── web-shared/   # Auth, API client, MapView, ratings
└── db/init.sql
```
