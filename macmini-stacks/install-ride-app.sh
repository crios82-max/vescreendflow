#!/usr/bin/env bash
set -euo pipefail

# Instala Ride App en el autostart maestro del Mac mini.
# Uso: ./macmini-stacks/install-ride-app.sh [/ruta/al/ride-app]

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AUTOSTART_DIR="${HOME}/Library/Application Support/macmini-stacks"
AUTOSTART="${AUTOSTART_DIR}/autostart.sh"
MARKER="# --- Ride App"
RIDE_APP_DIR="${1:-${RIDE_APP_DIR:-/Users/server/Documents/vescreendflow/ride-app}}"

echo "== Ride App autostart install =="
echo "Repo:       $REPO_ROOT"
echo "Ride app:   $RIDE_APP_DIR"
echo "Autostart:  $AUTOSTART"

if [[ ! -d "$RIDE_APP_DIR" ]]; then
  echo "ERROR: no existe $RIDE_APP_DIR"
  echo "Pásale la ruta: $0 /Users/server/tu/ruta/ride-app"
  exit 1
fi

if [[ ! -x "$RIDE_APP_DIR/scripts/start-stack.sh" ]]; then
  chmod +x "$RIDE_APP_DIR/scripts/start-stack.sh" "$RIDE_APP_DIR/scripts/stop-stack.sh" 2>/dev/null || true
fi

mkdir -p "$AUTOSTART_DIR"

if [[ -f "$AUTOSTART" ]] && grep -q "$MARKER" "$AUTOSTART"; then
  echo "OK: Ride App ya está en autostart"
else
  if [[ ! -f "$AUTOSTART" ]]; then
    cat >"$AUTOSTART" <<'HEADER'
#!/usr/bin/env bash
set -euo pipefail

LOG="${HOME}/Library/Application Support/macmini-stacks/autostart.log"
mkdir -p "$(dirname "$LOG")"

log() {
  local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
  echo "$msg" | tee -a "$LOG"
}

log "autostart begin"

# Esperar Docker Desktop
for i in {1..60}; do
  docker info >/dev/null 2>&1 && break
  sleep 2
done

HEADER
    chmod +x "$AUTOSTART"
    echo "Creado autostart.sh base"
  fi

  cat >>"$AUTOSTART" <<EOF

$MARKER (:4001 / :5174 / :5175 / db :5436) ---
RIDE_APP_DIR="${RIDE_APP_DIR}"
if [[ -x "\$RIDE_APP_DIR/scripts/start-stack.sh" ]]; then
  log "Ride App"
  "\$RIDE_APP_DIR/scripts/start-stack.sh" >>"\$LOG" 2>&1 || log "Ride App FAIL"
else
  log "Ride App SKIP (no script: \$RIDE_APP_DIR/scripts/start-stack.sh)"
fi
EOF
  echo "OK: bloque Ride App agregado a autostart.sh"
fi

# LaunchAgent
PLIST_SRC="$REPO_ROOT/macmini-stacks/com.macmini.stacks.autostart.plist"
PLIST_DST="${HOME}/Library/LaunchAgents/com.macmini.stacks.autostart.plist"
if [[ -f "$PLIST_SRC" ]]; then
  sed \
    -e "s|__AUTOSTART_SH__|${AUTOSTART}|g" \
    -e "s|__AUTOSTART_LOG__|${AUTOSTART_DIR}/autostart.log|g" \
    "$PLIST_SRC" >"$PLIST_DST"
  launchctl bootout "gui/$(id -u)" "$PLIST_DST" 2>/dev/null || true
  launchctl bootstrap "gui/$(id -u)" "$PLIST_DST"
  echo "OK: LaunchAgent cargado"
fi

# Primera subida del stack
if [[ -f "$RIDE_APP_DIR/.env" ]]; then
  echo "Levantando Ride App..."
  "$RIDE_APP_DIR/scripts/start-stack.sh"
else
  echo "WARN: crea $RIDE_APP_DIR/.env antes del primer boot (cp .env.example .env)"
fi

echo ""
echo "Verificar:"
echo "  tail -20 \"$AUTOSTART_DIR/autostart.log\""
echo "  curl -sf http://localhost:4001/health && echo OK"
echo "  pm2 list | grep ride-"
