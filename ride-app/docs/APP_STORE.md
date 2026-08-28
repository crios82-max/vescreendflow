# Ride App — App Store / Play Store

## Requisitos previos

| Plataforma | Necesitas |
|------------|-----------|
| iOS | Apple Developer ($99/año), App Store Connect app creada |
| Android | Google Play Console ($25 una vez), service account JSON |

## 1. Configura `eas.json`

Edita `apps/mobile/eas.json`:

```json
"submit": {
  "production": {
    "ios": {
      "appleId": "tu@email.com",
      "ascAppId": "1234567890",
      "appleTeamId": "ABCDE12345"
    },
    "android": {
      "serviceAccountKeyPath": "./google-play-service-account.json",
      "track": "internal"
    }
  }
}
```

## 2. Variables EAS (secrets)

En [expo.dev](https://expo.dev) → tu proyecto → Secrets:

| Secret | Valor |
|--------|-------|
| `EXPO_PUBLIC_API_URL` | `https://ride-api.tudominio.com` |
| `EXPO_PUBLIC_GOOGLE_MAPS_API_KEY` | Tu Google Maps key |

## 3. Build

```bash
cd ride-app/apps/mobile
npm install -g eas-cli
eas login
eas build --profile production --platform ios
eas build --profile production --platform android
```

## 4. Submit

```bash
eas submit --platform ios --profile production
eas submit --platform android --profile production
```

## 5. Metadata App Store (manual en App Store Connect)

- **Nombre:** Ride
- **Categoría:** Travel
- **Descripción:** Pide viajes, rastrea conductores, paga con tarjeta
- **Privacy Policy URL:** tu dominio `/privacy` (web pasajero)
- **Screenshots:** iPhone 6.7" y 6.5" (mínimo 3)

## 6. Google Play

- Data safety: ubicación, identificadores, pagos
- Privacy policy URL igual que iOS
- Internal testing track primero, luego production

## 7. Twilio Voice (llamadas enmascaradas)

En producción configura en el API:

```
API_PUBLIC_URL=https://ride-api.tudominio.com
TWILIO_ACCOUNT_SID=...
TWILIO_AUTH_TOKEN=...
TWILIO_PHONE_NUMBER=+1...
```

Webhook Twilio apunta a: `POST https://ride-api.tudominio.com/webhooks/twilio/voice/connect`

## Checklist pre-submit

- [ ] API en producción con HTTPS
- [ ] `EXPO_PUBLIC_API_URL` apunta al API público
- [ ] Google Maps key con restricciones iOS/Android bundle ID
- [ ] Stripe en modo live (si aplica)
- [ ] Probar login + pedir ride + pago en build TestFlight/Internal
