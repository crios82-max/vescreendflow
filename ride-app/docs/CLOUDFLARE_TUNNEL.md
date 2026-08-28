# Ride App — Cloudflare Tunnel (API público)

Expone el API local `:4001` como `https://ride-api.vescreenflow.com` (ajusta hostname).

## Opción A — Mismo túnel que vescreenflow (recomendado)

El repo ya tiene túnel `55818726-7a1f-459c-a904-00f5487e6aad`. Añade ingress en `cloudflared/config.yml` del repo:

```yaml
  - hostname: ride-api.vescreenflow.com
    service: http://127.0.0.1:4001
```

DNS (Cloudflare → vescreenflow.com):

| Type | Name | Content | Proxy |
|------|------|---------|-------|
| CNAME | `ride-api` | `55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com` | Proxied |

Reinicia el túnel en el Mac mini:

```bash
# Si corre con npm run tunnel, reinicia ese proceso
# O con LaunchAgent del túnel maestro
```

## Opción B — LaunchAgent dedicado Ride

```bash
cd ride-app
export RIDE_TUNNEL_ID=55818726-7a1f-459c-a904-00f5487e6aad   # o tu tunnel nuevo
chmod +x macmini-stacks/install-ride-tunnel.sh
./macmini-stacks/install-ride-tunnel.sh
```

## `.env` producción (`ride-app/.env`)

```bash
API_PUBLIC_URL=https://ride-api.vescreenflow.com
EXPO_PUBLIC_API_URL=https://ride-api.vescreenflow.com
CORS_ORIGINS=https://ride-api.vescreenflow.com,http://localhost:5174,http://localhost:5175,http://localhost:5176
PASSENGER_WEB_URL=http://localhost:5174
STRIPE_CONNECT_REFRESH_URL=http://localhost:5175
STRIPE_CONNECT_RETURN_URL=http://localhost:5175
```

Luego:

```bash
pm2 restart ride-api --update-env
```

## Verificar

```bash
curl -sf https://ride-api.vescreenflow.com/health && echo OK
```

## Twilio Voice

Con `API_PUBLIC_URL` HTTPS, Twilio puede llamar:

`POST https://ride-api.vescreenflow.com/webhooks/twilio/voice/connect`

## Móvil en iPhone (fuera de casa)

1. API público arriba
2. EAS secret `EXPO_PUBLIC_API_URL=https://ride-api.vescreenflow.com`
3. O en la app → Configurar servidor API
