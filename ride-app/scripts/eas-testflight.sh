#!/usr/bin/env bash
# Build iOS para TestFlight y submit (requiere eas-cli + Apple Developer)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/apps/mobile"

if ! command -v eas >/dev/null 2>&1; then
  echo "Instala: npm install -g eas-cli && eas login"
  exit 1
fi

PROFILE="${1:-testflight}"
PLATFORM="${2:-ios}"

echo ">> EAS build profile=$PROFILE platform=$PLATFORM"
eas build --profile "$PROFILE" --platform "$PLATFORM" --non-interactive

echo ">> EAS submit (TestFlight)"
eas submit --platform "$PLATFORM" --profile "$PROFILE" --latest --non-interactive

echo ">> Listo — revisa App Store Connect → TestFlight"
