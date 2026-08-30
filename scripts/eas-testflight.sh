#!/usr/bin/env bash
# Build iOS TestFlight (preflight API + eas build + submit)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/apps/mobile"

API_URL="${EXPO_PUBLIC_API_URL:-https://movify-api.vescreenflow.com}"

if ! command -v eas >/dev/null 2>&1; then
  echo "Instala: npm install -g eas-cli && eas login"
  exit 1
fi

echo ">> Preflight API público"
if curl -sf --max-time 15 "${API_URL}/health" >/dev/null 2>&1; then
  echo "   OK ${API_URL}/health"
else
  echo "   WARN: ${API_URL}/health no responde"
  echo "   TestFlight funcionará pero la app no conectará fuera de LAN hasta DNS + tunnel."
  echo "   Ver: docs/GO_LIVE.md"
  read -r -p "   ¿Continuar build? [y/N] " ans
  [[ "${ans:-}" == "y" || "${ans:-}" == "Y" ]] || exit 1
fi

if grep -q 'YOUR_APPLE_ID' eas.json 2>/dev/null; then
  echo "ERROR: edita eas.json → appleId, ascAppId, appleTeamId"
  exit 1
fi

PROFILE="${1:-testflight}"
PLATFORM="${2:-ios}"

echo ">> EAS build profile=$PROFILE platform=$PLATFORM"
eas build --profile "$PROFILE" --platform "$PLATFORM" --non-interactive

echo ">> EAS submit (TestFlight)"
eas submit --platform "$PLATFORM" --profile "$PROFILE" --latest --non-interactive

echo ">> Listo — App Store Connect → TestFlight → testers"
