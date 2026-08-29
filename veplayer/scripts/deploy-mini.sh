#!/usr/bin/env bash
# VePlayer + SenseFlow deploy en Mac mini (post-merge fases 41–45).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "▶ git pull main"
git pull origin main

echo "▶ pm2 restart senseflow"
pm2 restart senseflow

echo "▶ health SenseFlow"
curl -sf http://127.0.0.1:4100/api/health | head -c 200
echo

echo "▶ build release APK"
cd veplayer/android
./gradlew assembleRelease --no-daemon

APK="app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$APK" ]]; then
  echo "APK no encontrado: $APK" >&2
  exit 1
fi

echo "▶ adb install"
adb install -r "$APK"

echo "✓ deploy-mini OK"
