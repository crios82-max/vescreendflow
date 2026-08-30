#!/usr/bin/env bash
# Sube secrets EAS desde movify/.env (requiere eas-cli + eas login)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${1:-$ROOT/.env}"
MOBILE_DIR="$ROOT/apps/mobile"

if ! command -v eas >/dev/null 2>&1; then
  echo "Instala: npm install -g eas-cli && eas login"
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "No existe $ENV_FILE"
  exit 1
fi

get_env() {
  grep -E "^${1}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true
}

API_URL="$(get_env EXPO_PUBLIC_API_URL)"
MAPS_KEY="$(get_env EXPO_PUBLIC_GOOGLE_MAPS_API_KEY)"

if [[ -z "$API_URL" ]]; then
  API_URL="https://movify-api.vescreenflow.com"
  echo "EXPO_PUBLIC_API_URL no en .env — usando $API_URL"
fi

cd "$MOBILE_DIR"

echo ">> EAS secrets (proyecto Movify)"
eas secret:create --name EXPO_PUBLIC_API_URL --value "$API_URL" --force 2>/dev/null || \
  eas env:create --name EXPO_PUBLIC_API_URL --value "$API_URL" --environment production --visibility plaintext --force 2>/dev/null || \
  echo "Configura EXPO_PUBLIC_API_URL=$API_URL manualmente en expo.dev"

if [[ -n "$MAPS_KEY" ]]; then
  eas secret:create --name EXPO_PUBLIC_GOOGLE_MAPS_API_KEY --value "$MAPS_KEY" --force 2>/dev/null || \
    eas env:create --name EXPO_PUBLIC_GOOGLE_MAPS_API_KEY --value "$MAPS_KEY" --environment production --visibility plaintext --force 2>/dev/null || \
    echo "Configura EXPO_PUBLIC_GOOGLE_MAPS_API_KEY manualmente en expo.dev"
else
  echo "WARN: EXPO_PUBLIC_GOOGLE_MAPS_API_KEY vacío en .env"
fi

echo ""
echo "Verifica en https://expo.dev → proyecto → Environment variables"
echo "Luego: ./scripts/eas-testflight.sh"
