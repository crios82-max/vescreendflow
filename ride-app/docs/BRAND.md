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

| Archivo | Uso |
|---------|-----|
| `apps/mobile/assets/icon.png` | App Store / Play (1024×1024) |
| `apps/mobile/assets/adaptive-icon.png` | Android adaptive |
| `apps/*/public/favicon.png` | Web pasajero / conductor / admin |

Reemplaza los PNG cuando tengas logo final de diseño.

## Colores

- Primario: `#A3E635` (lima)
- Fondo: `#000000`
