#!/usr/bin/env bash
# vescreenflow — kiosk en Raspberry Pi / Linux
# Uso: chmod +x kiosk-pi.sh && ./kiosk-pi.sh

set -euo pipefail

URL="${VESCREENFLOW_URL:-https://vescreenflow.com/play}"
BROWSER=""

for candidate in chromium-browser chromium google-chrome google-chrome-stable; do
  if command -v "$candidate" >/dev/null 2>&1; then
    BROWSER="$candidate"
    break
  fi
done

if [[ -z "$BROWSER" ]]; then
  echo "No se encontró Chromium/Chrome."
  echo "En Raspberry Pi OS:"
  echo "  sudo apt update && sudo apt install -y chromium-browser unclutter"
  exit 1
fi

# Oculta el cursor (opcional)
if command -v unclutter >/dev/null 2>&1; then
  unclutter -idle 0.5 -root &
fi

# Evita sleep de pantalla (mejor esfuerzo)
if command -v xset >/dev/null 2>&1; then
  xset s off || true
  xset -dpms || true
  xset s noblank || true
fi

exec "$BROWSER" \
  --kiosk \
  --fullscreen \
  --noerrdialogs \
  --disable-infobars \
  --disable-session-crashed-bubble \
  --disable-restore-session-state \
  --autoplay-policy=no-user-gesture-required \
  --check-for-update-interval=31536000 \
  "$URL"
