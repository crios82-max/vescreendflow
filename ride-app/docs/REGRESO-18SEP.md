# Movify — Checklist regreso 18 septiembre 2026

Todo el **código** ya está en `main`. Al volver solo ejecutas esto en orden (~1–2 h si tienes las API keys).

---

## Ya está hecho (no toques)

- App pasajero / conductor / admin / móvil
- Marca Movify + logo
- Tema negro + lima
- Multi-idioma ES · EN · IT · PT (auto-detect por ubicación)
- Pulido pre-prod: Share/Split i18n, docs conductor, admin, tunnel driver/admin
- UX pack: toast global, te() en auth, push/recibos i18n, móvil forgot password
- Paridad móvil: wallet, split, lugares, schedule, fare breakdown, docs/Stripe conductor
- Túnel configurado en `cloudflared/config.yml` (`movify` + `movify-api` + driver/admin)
- Scripts: `prep-local`, `go-live`, `check-prod`, TestFlight
- CI + tests API

---

## Día 1 al regresar (orden fijo)

### A. Mac mini — stack local (15 min)

```bash
cd /Users/server/Documents/vescreendflow   # ajusta ruta
git pull origin main
./macmini-stacks/bootstrap-ride-app.sh

cd ride-app
npm run prep:local
```

Prueba en el navegador del Mac:
- http://localhost:5174 — login pasajero
- http://localhost:5175 — conductor
- http://localhost:5176 — admin

Crea usuarios de prueba (pasajero + conductor, mismo `vehicleType`) — o:

```bash
npm run seed:demo
```

| Rol | Email | Password |
|-----|-------|----------|
| Pasajero | `pasajero@movify.demo` | `movify123` |
| Conductor | `conductor@movify.demo` | `movify123` |
| Admin | `admin@movify.demo` | `movify123` |

(Conductor ya viene **approved** + teléfonos verificados.)

### B. `.env` producción (20 min)

```bash
cd ride-app
npm run setup:prod
nano .env   # o tu editor
```

Mínimo para pruebas reales:

```bash
JWT_SECRET=<openssl rand -hex 32>
GOOGLE_MAPS_API_KEY=...
VITE_GOOGLE_MAPS_API_KEY=...
EXPO_PUBLIC_GOOGLE_MAPS_API_KEY=...

# Cuando actives DNS (paso C):
API_PUBLIC_URL=https://movify-api.vescreenflow.com
VITE_API_URL=https://movify-api.vescreenflow.com
EXPO_PUBLIC_API_URL=https://movify-api.vescreenflow.com
PASSENGER_WEB_URL=https://movify.vescreenflow.com
CORS_ORIGINS=https://movify-api.vescreenflow.com,https://movify.vescreenflow.com,http://localhost:5174,http://localhost:5175,http://localhost:5176
```

```bash
npm run build
pm2 restart ride-api ride-passenger ride-driver ride-admin --update-env
```

### C. Cloudflare DNS (5 min)

Dashboard → **vescreenflow.com** → DNS:

| Type | Name | Target | Proxy |
|------|------|--------|-------|
| CNAME | `movify-api` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` | Proxied |
| CNAME | `movify` | mismo | Proxied |
| CNAME | `movify-driver` | mismo | Proxied |
| CNAME | `movify-admin` | mismo | Proxied |

Reinicia cloudflared en el Mac mini.

```bash
cd ride-app
npm run go-live
```

Debe pasar:
- `https://movify-api.vescreenflow.com/health`
- `https://movify.vescreenflow.com`
- `https://movify-driver.vescreenflow.com`
- `https://movify-admin.vescreenflow.com`

### D. Prueba end-to-end web (30 min)

1. Abre https://movify.vescreenflow.com en el iPhone (datos, no solo WiFi)
2. Registro / login pasajero
3. OTP teléfono (Twilio o mock `123456` si `TWILIO_*` vacío en dev)
4. Pedir viaje → conductor en :5175 o móvil conductor
5. Completar + pago (Stripe test keys primero)

### E. TestFlight (1 h + espera Apple)

1. App Store Connect → app **Movify** bundle `com.movify.app`
2. Edita `ride-app/apps/mobile/eas.json` → Apple IDs
3. `./scripts/setup-eas-secrets.sh`
4. `eas login && ./scripts/eas-testflight.sh`
5. Instala en iPhone desde TestFlight → pedir ride contra API público

### F. Opcional después

- Stripe live → [STRIPE_LIVE.md](./STRIPE_LIVE.md)
- Twilio SMS + Voice
- SMTP recibos

---

## Móvil en casa (sin DNS, antes del 18)

Si quieres probar en LAN antes de viajar o al regresar sin DNS aún:

```bash
cd ride-app
npm run dev:mobile
```

En la app → **Configurar servidor API** → `http://IP_DEL_MAC:4001` (misma WiFi).

---

## Comandos rápidos

| Qué | Comando |
|-----|---------|
| Prep local | `npm run prep:local` |
| Usuarios demo | `npm run seed:demo` |
| Check prod | `npm run go-live` |
| Health LAN | `npm run health` |
| Logs | `npm run pm2:logs` |
| Bloque .env | `npm run setup:prod` |

---

## Si algo falla

| Síntoma | Fix |
|---------|-----|
| `prep:local` falla build | `npm install --legacy-peer-deps` |
| Maps vacío | Google Maps API key + APIs habilitadas |
| Móvil no conecta | IP Mac:4001 o esperar paso C (DNS) |
| `go-live` falla DNS | CNAME en Cloudflare + reiniciar tunnel |
| TestFlight sin API | `EXPO_PUBLIC_API_URL` en EAS + rebuild |

Más detalle: [GO_LIVE.md](./GO_LIVE.md)
