#!/usr/bin/env bash
# Imprime bloque .env producción y checklist (no sobrescribe .env)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${1:-$ROOT/.env}"

cat <<'EOF'
# === Ride App — bloque producción (copia a ride-app/.env) ===

API_PUBLIC_URL=https://ride-api.vescreenflow.com
VITE_API_URL=https://ride-api.vescreenflow.com
EXPO_PUBLIC_API_URL=https://ride-api.vescreenflow.com
PASSENGER_WEB_URL=https://ride.vescreenflow.com
CORS_ORIGINS=https://ride-api.vescreenflow.com,https://ride.vescreenflow.com,http://localhost:5174,http://localhost:5175,http://localhost:5176

JWT_SECRET=<openssl rand -hex 32>

# Google Maps (misma key con restricciones HTTP referrer + iOS bundle)
GOOGLE_MAPS_API_KEY=
VITE_GOOGLE_MAPS_API_KEY=
EXPO_PUBLIC_GOOGLE_MAPS_API_KEY=

# Stripe LIVE — ver docs/STRIPE_LIVE.md
STRIPE_SECRET_KEY=sk_live_...
VITE_STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Twilio
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_PHONE_NUMBER=

# SMTP
SMTP_HOST=
SMTP_USER=
SMTP_PASS=
SMTP_FROM=ride@vescreenflow.com

REQUIRE_PHONE_VERIFY=true

EOF

echo ""
echo "DNS Cloudflare (Proxied CNAME → 55818726-7a1f-459c-a904-00f5487e6aad.cfargotunnel.com):"
echo "  ride-api"
echo "  ride"
echo ""
echo "Stripe webhook: https://ride-api.vescreenflow.com/webhooks/stripe"
echo "Twilio voice:   https://ride-api.vescreenflow.com/webhooks/twilio/voice/connect"
echo ""
echo "Después de editar .env:"
echo "  npm run build"
echo "  pm2 restart ride-api ride-passenger ride-driver ride-admin --update-env"
echo "  npm run check:prod"
echo ""

if [[ -f "$ENV_FILE" ]]; then
  echo "Estado actual ($ENV_FILE):"
  for key in JWT_SECRET VITE_API_URL API_PUBLIC_URL PASSENGER_WEB_URL STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET TWILIO_ACCOUNT_SID; do
    if grep -q "^${key}=" "$ENV_FILE" 2>/dev/null; then
      val=$(grep "^${key}=" "$ENV_FILE" | cut -d= -f2-)
      if [[ -z "$val" ]]; then
        echo "  MISSING $key"
      elif [[ "$key" == "JWT_SECRET" && "$val" == change-me* ]]; then
        echo "  WARN  $key (default)"
      elif [[ "$key" == "STRIPE_SECRET_KEY" && "$val" == sk_test_* ]]; then
        echo "  WARN  $key (test mode)"
      elif [[ "$key" == "STRIPE_SECRET_KEY" && "$val" == sk_live_* ]]; then
        echo "  OK    $key (live)"
      elif [[ "$key" == "VITE_API_URL" && "$val" == *localhost* ]]; then
        echo "  WARN  $key=$val"
      else
        echo "  OK    $key"
      fi
    else
      echo "  MISSING $key"
    fi
  done
fi
