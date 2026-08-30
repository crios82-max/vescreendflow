# Movify — Go Live (DNS + Mac mini + TestFlight)

**Regresas el 18 sep?** → [REGRESO-18SEP.md](./REGRESO-18SEP.md) (checklist corto).

Orden recomendado: **DNS → deploy → API público → TestFlight**.

## Paso 1 — Cloudflare DNS (5 min)

Dashboard → **vescreenflow.com** → **DNS** → **Add record**:

| Type | Name | Target | Proxy |
|------|------|--------|-------|
| CNAME | `movify-api` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` | Proxied |
| CNAME | `movify` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` | Proxied |

El túnel ya tiene ingress en `cloudflared/config.yml` del repo.

## Paso 2 — Mac mini deploy

```bash
cd /Users/server/Documents/vescreendflow   # tu ruta
git pull origin main
./macmini-stacks/bootstrap-ride-app.sh
```

Edita `movify/.env` (bloque de ayuda):

```bash
cd movify
npm run setup:prod
# pega vars, genera JWT: openssl rand -hex 32
npm run build
pm2 restart ride-api ride-passenger ride-driver ride-admin --update-env
```

Reinicia **cloudflared** (túnel maestro o `npm run tunnel`).

## Paso 3 — Verificar

```bash
cd movify
npm run go-live
# o manual:
curl -sf https://movify-api.vescreenflow.com/health && echo OK
curl -sf -o /dev/null https://movify.vescreenflow.com && echo OK
```

## Paso 4 — TestFlight

### 4a. App Store Connect

1. [appstoreconnect.apple.com](https://appstoreconnect.apple.com) → **Apps** → **+** → New App
2. Nombre: **Movify**
3. Bundle ID: `com.movify.app` (debe existir en Apple Developer → Identifiers)

### 4b. `eas.json`

Edita `apps/mobile/eas.json`:

```json
"appleId": "tu@email.com",
"ascAppId": "1234567890",
"appleTeamId": "ABCDE12345"
```

(`ascAppId` = Apple ID numérico de la app en App Store Connect)

### 4c. EAS secrets

```bash
cd movify
# Con EXPO_PUBLIC_* en .env:
./scripts/setup-eas-secrets.sh
```

O manual en [expo.dev](https://expo.dev):

| Variable | Valor |
|----------|-------|
| `EXPO_PUBLIC_API_URL` | `https://movify-api.vescreenflow.com` |
| `EXPO_PUBLIC_GOOGLE_MAPS_API_KEY` | Tu Google Maps key (iOS + APIs habilitadas) |

### 4d. Build + submit

```bash
cd movify
npm install -g eas-cli
eas login
./scripts/eas-testflight.sh
```

En App Store Connect → **TestFlight** → añade testers internos → instala en iPhone.

### 4e. Metadata App Store (cuando publiques)

- **Privacy:** `https://movify.vescreenflow.com/privacy`
- **Terms:** `https://movify.vescreenflow.com/terms`
- **Categoría:** Travel

## Paso 5 — Stripe / Twilio (opcional pero prod)

- [STRIPE_LIVE.md](./STRIPE_LIVE.md) — webhook `https://movify-api.vescreenflow.com/webhooks/stripe`
- Twilio Voice: `API_PUBLIC_URL` HTTPS + webhook voice

## Troubleshooting

| Síntoma | Fix |
|---------|-----|
| `health` falla público | CNAME + cloudflared reiniciado + PM2 ride-api |
| Web 404 / error | `npm run build` + `pm2 restart ride-passenger` |
| App no conecta API | EAS secret `EXPO_PUBLIC_API_URL` + rebuild TestFlight |
| Maps vacío móvil | `EXPO_PUBLIC_GOOGLE_MAPS_API_KEY` + Maps SDK iOS en Google Cloud |

Más: [CLOUDFLARE_TUNNEL.md](./CLOUDFLARE_TUNNEL.md) · [APP_STORE.md](./APP_STORE.md) · [BRAND.md](./BRAND.md)
