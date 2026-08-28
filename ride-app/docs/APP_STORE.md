# Ride App — App Store / Play Store / TestFlight

Ver también: [CLOUDFLARE_TUNNEL.md](./CLOUDFLARE_TUNNEL.md) (API público obligatorio para móvil fuera de LAN).

## Requisitos previos

| Plataforma | Necesitas |
|------------|-----------|
| iOS | Apple Developer ($99/año), App Store Connect app creada |
| Android | Google Play Console ($25 una vez), service account JSON |

## 0. API público (antes de TestFlight)

```bash
# Mac mini — DNS CNAME ride-api → tu túnel (ver CLOUDFLARE_TUNNEL.md)
curl -sf https://ride-api.vescreenflow.com/health && echo OK
```

En `ride-app/.env`:

```
API_PUBLIC_URL=https://ride-api.vescreenflow.com
EXPO_PUBLIC_API_URL=https://ride-api.vescreenflow.com
```

## 1. Configura `eas.json`

Edita `apps/mobile/eas.json` → `submit.production.ios`:

```json
"appleId": "tu@email.com",
"ascAppId": "1234567890",
"appleTeamId": "ABCDE12345"
```

## 2. Variables EAS (secrets)

En [expo.dev](https://expo.dev) → proyecto `ride-app` → **Secrets**:

| Secret | Valor |
|--------|-------|
| `EXPO_PUBLIC_API_URL` | `https://ride-api.vescreenflow.com` |
| `EXPO_PUBLIC_GOOGLE_MAPS_API_KEY` | Tu Google Maps key |

## 3. TestFlight (un comando)

```bash
cd ride-app
chmod +x scripts/eas-testflight.sh
./scripts/eas-testflight.sh testflight ios
```

O manual:

```bash
cd ride-app/apps/mobile
eas login
npm run testflight
```

## 4. Build producción

```bash
cd ride-app/apps/mobile
eas build --profile production --platform ios
eas build --profile production --platform android
```

## 5. Submit stores

```bash
eas submit --platform ios --profile production
eas submit --platform android --profile production
```

## 6. Metadata App Store Connect

- **Nombre:** Ride
- **Categoría:** Travel
- **Privacy Policy URL:** `http://TU_IP:5174/privacy` o dominio futuro
- **Screenshots:** iPhone 6.7" y 6.5" (mín. 3)

## 7. Twilio Voice

```
API_PUBLIC_URL=https://ride-api.vescreenflow.com
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_PHONE_NUMBER=+1...
```

## Checklist

- [ ] `curl https://ride-api.vescreenflow.com/health`
- [ ] PM2 ride-api corriendo en Mac mini
- [ ] EAS secrets configurados
- [ ] TestFlight build instalado y login OK
- [ ] Pedir ride end-to-end desde iPhone
