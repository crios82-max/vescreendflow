# Deploy vescreenflow.com (Pages + Tunnel)

## Ya hecho

- Túnel `vescreenflow-api` corriendo → ID `55818726-7a1f-459c-a904-00f5487e6aad`
- Frontend desplegado en Pages: https://vescreenflow.pages.dev  
  Preview: https://871e5d62.vescreenflow.pages.dev
- Scripts: `npm run deploy:pages` · `npm run tunnel`

## Pega estos DNS en la zona **vescreenflow.com**

Cloudflare → **vescreenflow.com** → **DNS** → **Records**:

| Type | Name | Content | Proxy |
|------|------|---------|-------|
| CNAME | `@` | `vescreenflow.pages.dev` | Proxied |
| CNAME | `www` | `vescreenflow.pages.dev` | Proxied |
| CNAME | `api` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` | Proxied |

También en **Pages → vescreenflow → Custom domains** añade `vescreenflow.com` y `www.vescreenflow.com` (valida el dominio).

SSL/TLS → **Full**

## Si ves un registro basura

Borra en **miticket24.com** (si existe) el CNAME `api.vescreenflow.com` — se creó por error al enrutar el túnel.

## Mantener API pública

Mientras quieras `api.vescreenflow.com` vivo, deja corriendo en esta máquina:

```bash
npm run db:up
npm run dev:api
npm run tunnel
```

## Player kiosk (gratis)

URL: https://vescreenflow.com/play

1. Abre `/play` en Chrome (Windows, Raspberry Pi, Android, etc.)
2. Verás un código de 8 dígitos
3. En el panel (`/login`) → Pantallas → Agregar pantalla → pega el código
4. La pantalla empieza a reproducir contenido (demo si la playlist está vacía)

Atajos: `F` pantalla completa · `R` nuevo código

Para que la API pública funcione, deja corriendo en esta máquina:

```bash
npm run db:up
npm run dev:api   # o server: npm run start --prefix server
npm run tunnel
```
