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

## Logo oficial

**Variante 05 — wordmark blanco + acento lima** (ícono M + “Movi” blanco + “fy” lima).

| Archivo | Contenido |
|---------|-----------|
| `brand/movify-wordmark.png` | Logo horizontal oficial |
| `brand/movify-icon.png` | Ícono M recortado para app/favicon |

Otras opciones en `brand/variants/` (ver tabla abajo).

## Assets desplegados

| Archivo | Uso |
|---------|-----|
| `brand/movify-icon.png` | Ícono app (master 1024×1024) |
| `brand/movify-wordmark.png` | Logo horizontal (web, marketing) |
| `apps/mobile/assets/icon.png` | App Store / Play |
| `apps/mobile/assets/adaptive-icon.png` | Android adaptive |
| `apps/*/public/favicon.png` | Favicon web |
| `apps/*/public/logo.png` | Wordmark en pantallas login |

## Variantes de logo (`brand/variants/`)

| Archivo | Estilo | Mejor para |
|---------|--------|------------|
| `00-default-icon.png` | Ícono M oficial (recortado del wordmark) | App, favicon |
| `00-default-wordmark.png` | *(ver movify-wordmark.png)* | — |
| `01-pin-marker.png` | Pin de mapa + M | App más “maps” |
| `02-swoosh-m.png` | M con trazo dinámico | App energética |
| `03-light-bg.png` | M negro/lima en blanco | Fondos claros, print |
| `04-wordmark-lime.png` | Solo texto lima | Merch, banners |
| `05-wordmark-white.png` | **Oficial** — texto blanco + fy lima | Login web |
| `06-mono-white.png` | M blanco monocromo | Watermark, grayscale |

Para cambiar el logo activo, reemplaza `brand/movify-icon.png` y `brand/movify-wordmark.png` por la variante elegida y copia a `apps/*/public/` y `apps/mobile/assets/`.


- Primario: `#A3E635` (lima)
- Fondo: `#000000`
