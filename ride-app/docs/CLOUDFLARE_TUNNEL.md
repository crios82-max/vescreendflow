# Movify — Cloudflare Tunnel (API + web pasajero)

Expone:

| Hostname | Servicio local |
|----------|----------------|
| `movify-api.vescreenflow.com` | API `:4001` |
| `movify.vescreenflow.com` | Web pasajero `:5174` |

## Opción A — Mismo túnel que vescreenflow (recomendado)

El repo ya tiene túnel `55818726-7a1f-459c-a904-00f5487e6aad`. Ingress en `cloudflared/config.yml`:

```yaml
  - hostname: movify-api.vescreenflow.com
    service: http://127.0.0.1:4001
  - hostname: movify.vescreenflow.com
    service: http://127.0.0.1:5174
```

DNS (Cloudflare → vescreenflow.com):

| Type | Name | Content | Proxy |
|------|------|---------|-------|
| CNAME | `movify-api` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` | Proxied |
| CNAME | `movify` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` | Proxied |

Reinicia cloudflared en el Mac mini (`npm run tunnel` o LaunchAgent del túnel maestro).

## Opción B — LaunchAgent dedicado Ride

```bash
cd ride-app
export RIDE_TUNNEL_ID=55818726-7a1f-459c-a904-00f5487e6aad
chmod +x macmini-stacks/install-ride-tunnel.sh
./macmini-stacks/install-ride-tunnel.sh
```

## `.env` producción (`ride-app/.env`)

```bash
API_PUBLIC_URL=https://movify-api.vescreenflow.com
VITE_API_URL=https://movify-api.vescreenflow.com
EXPO_PUBLIC_API_URL=https://movify-api.vescreenflow.com
PASSENGER_WEB_URL=https://movify.vescreenflow.com
CORS_ORIGINS=https://movify-api.vescreenflow.com,https://movify.vescreenflow.com,http://localhost:5174,http://localhost:5175,http://localhost:5176
STRIPE_CONNECT_REFRESH_URL=http://localhost:5175
STRIPE_CONNECT_RETURN_URL=http://localhost:5175
```

Importante: `VITE_API_URL` se embebe en el build de las webs. Tras cambiarlo:

```bash
npm run build
pm2 restart ride-api ride-passenger --update-env
```

## Verificar

```bash
./scripts/check-prod.sh
# o manual:
curl -sf https://movify-api.vescreenflow.com/health && echo OK
curl -sf -o /dev/null https://movify.vescreenflow.com && echo OK
```

## Twilio Voice

Con `API_PUBLIC_URL` HTTPS:

`POST https://movify-api.vescreenflow.com/webhooks/twilio/voice/connect`

## Móvil en iPhone (fuera de casa)

1. API público arriba
2. EAS secret `EXPO_PUBLIC_API_URL=https://movify-api.vescreenflow.com`
3. O en la app → Configurar servidor API
