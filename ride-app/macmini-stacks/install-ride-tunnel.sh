#!/usr/bin/env bash
# Instala LaunchAgent cloudflared para Ride App API (Mac mini)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RIDE_APP_DIR="${1:-${RIDE_APP_DIR:-$REPO_ROOT/ride-app}}"
CONFIG_SRC="${RIDE_APP_DIR}/cloudflared/config.example.yml"
CONFIG_DST="${HOME}/.cloudflared/ride-app-config.yml"
PLIST_DST="${HOME}/Library/LaunchAgents/com.rideapp.cloudflared.plist"
TUNNEL_ID="${RIDE_TUNNEL_ID:-}"

log() { echo "[ride-tunnel] $*"; }

if [[ ! -f "$CONFIG_SRC" ]]; then
  log "ERROR: no existe $CONFIG_SRC"
  exit 1
fi

if [[ -z "$TUNNEL_ID" ]]; then
  log "Usa el mismo túnel de vescreenflow o crea uno nuevo:"
  log "  cloudflared tunnel create ride-app"
  log "  export RIDE_TUNNEL_ID=<uuid>"
  log "  $0"
  exit 1
fi

CREDS="${HOME}/.cloudflared/${TUNNEL_ID}.json"
if [[ ! -f "$CREDS" ]]; then
  log "ERROR: falta credentials $CREDS"
  exit 1
fi

mkdir -p "${HOME}/.cloudflared"
sed \
  -e "s|YOUR_TUNNEL_ID|${TUNNEL_ID}|g" \
  -e "s|/Users/server|${HOME}|g" \
  "$CONFIG_SRC" >"$CONFIG_DST"

cat >"$PLIST_DST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>com.rideapp.cloudflared</string>
  <key>ProgramArguments</key>
  <array>
    <string>$(which cloudflared)</string>
    <string>tunnel</string>
    <string>--config</string>
    <string>${CONFIG_DST}</string>
    <string>run</string>
  </array>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>${HOME}/Library/Logs/ride-app-cloudflared.log</string>
  <key>StandardErrorPath</key>
  <string>${HOME}/Library/Logs/ride-app-cloudflared.log</string>
</dict>
</plist>
EOF

launchctl bootout "gui/$(id -u)" "$PLIST_DST" 2>/dev/null || true
launchctl bootstrap "gui/$(id -u)" "$PLIST_DST"

log "OK LaunchAgent com.rideapp.cloudflared"
log ""
log "DNS en Cloudflare (zona vescreenflow.com):"
log "  CNAME movi-api -> ${TUNNEL_ID}.cfargotunnel.com (Proxied)"
log "  CNAME movi     -> ${TUNNEL_ID}.cfargotunnel.com (Proxied)"
log ""
log "En ride-app/.env:"
log "  API_PUBLIC_URL=https://movi-api.vescreenflow.com"
log "  VITE_API_URL=https://movi-api.vescreenflow.com"
log "  EXPO_PUBLIC_API_URL=https://movi-api.vescreenflow.com"
log "  PASSENGER_WEB_URL=https://movi.vescreenflow.com"
log "  CORS_ORIGINS=https://movi-api.vescreenflow.com,https://movi.vescreenflow.com,..."
log ""
log "Tras editar .env: npm run build && pm2 restart ride-api ride-passenger --update-env"
log ""
log "Verificar:"
log "  ./scripts/check-prod.sh"
log "  curl -sf https://movi-api.vescreenflow.com/health"
log "  curl -sf -o /dev/null https://movi.vescreenflow.com"
log "  tail -f ${HOME}/Library/Logs/ride-app-cloudflared.log"
