#!/usr/bin/env bash
# VePlayer deploy en Mac mini. SenseFlow sigue en vescreendflow (hermano o SENSEFLOW_DIR).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SENSEFLOW_DIR="${SENSEFLOW_DIR:-$ROOT/../vescreendflow}"
cd "$ROOT"

echo "▶ git pull main"
git pull origin main

if [[ -d "$SENSEFLOW_DIR" ]]; then
  echo "▶ pm2 restart senseflow (desde $SENSEFLOW_DIR)"
  (cd "$SENSEFLOW_DIR" && pm2 restart senseflow) || pm2 restart senseflow || true
else
  echo "▶ pm2 restart senseflow"
  pm2 restart senseflow || true
fi

echo "▶ health SenseFlow"
curl -sf http://127.0.0.1:4100/api/health | head -c 200
echo

echo "▶ build release APK"
cd "$ROOT/android"
./gradlew assembleRelease --no-daemon

APK="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK no encontrado: $APK" >&2
  exit 1
fi

echo "▶ adb install"
adb install -r "$APK"

echo "✓ deploy-mini OK"
