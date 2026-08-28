# Movify — Marca

Nombre comercial: **Movify** · Tagline: *Muévete fácil.*

## Cambiar marca

Un solo archivo controla nombre, dominios y colores:

```
ride-app/packages/shared/src/brand.ts
```

Tras editar:

```bash
cd ride-app
npm run build
pm2 restart ride-api ride-passenger ride-driver ride-admin --update-env
```

## Dominios (Cloudflare)

| CNAME | Target |
|-------|--------|
| `movify-api` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` |
| `movify` | mismo |

## Bundle ID (antes de TestFlight)

- iOS: `com.movify.app`
- Android: `com.movify.app`

## Assets

Fuente de diseño en `ride-app/brand/`:

| Archivo | Uso |
|---------|-----|
| `brand/movify-icon.png` | Ícono app (master 1024×1024) |
| `brand/movify-wordmark.png` | Logo horizontal (web, marketing) |
| `apps/mobile/assets/icon.png` | App Store / Play |
| `apps/mobile/assets/adaptive-icon.png` | Android adaptive |
| `apps/*/public/favicon.png` | Favicon web |
| `apps/*/public/logo.png` | Wordmark en pantallas login |

## Colores

- Primario: `#A3E635` (lima)
- Fondo: `#000000`
