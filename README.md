# vescreenflow

Clon funcional del producto de digital signage tipo [PosterBooking](https://posterbooking.com/): landing page, pricing, auth y dashboard demo.

## Stack

- React 19 + TypeScript
- Vite
- React Router
- CSS (Poppins, paleta amarillo/navy/rojo)
- PostgreSQL 16 (Docker)
- Express API (`server/`)

## Base de datos (Docker)

```bash
cp .env.example .env   # si aún no existe
docker compose up -d
```

- Host: `localhost:5434`
- User / pass / db: `screenflow` / `screenflow` / `screenflow`
- URL: `postgresql://screenflow:screenflow@localhost:5434/screenflow`
- Schema + seed: `db/init/001_schema.sql`
- Demo user: `demo@vescreenflow.com` / `password123`

Comprobar:

```bash
docker compose ps
docker compose exec db psql -U screenflow -d screenflow -c '\dt'
```

Parar / borrar volumen:

```bash
docker compose down        # para contenedores
docker compose down -v     # también borra datos
```

## API

```bash
npm run dev:api
```

- Health: `GET http://127.0.0.1:4000/api/health`
- Auth: `POST /api/auth/signup` · `POST /api/auth/login`
- Screens: `GET/POST /api/screens`
- Playlists: `GET/POST /api/playlists`

## Arrancar frontend

```bash
npm install
npm run dev
```

## Rutas

| Ruta | Descripción |
|------|-------------|
| `/` | Landing idéntica en estructura |
| `/pricing` | Planes Free / Growth / Enterprise |
| `/login` `/signup` | Auth demo (localStorage) |
| `/dashboard` | Pantallas, playlists y upload |

## Notas

- Marca propia **vescreenflow** (no usa la marca PosterBooking).
- El dashboard es un prototipo front-end; falta backend, players y sync real.

## SenseFlow (nuevo)

Crowdsourced tráfico + personas (Android + API). Ver [`senseflow/README.md`](senseflow/README.md).

```bash
npm run senseflow:seed
npm run senseflow:dev
# mapa: http://127.0.0.1:4100
```

## VePlayer (OS reproductor vehículo)

Launcher kiosk: cámaras, radio, YouTube, tienda Spotify, pantalla, mapa. Ver [`veplayer/README.md`](veplayer/README.md).

```bash
cd veplayer/android && ./gradlew assembleDebug
# demo UI: veplayer/web/index.html
```
