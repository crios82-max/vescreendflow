#!/usr/bin/env bash
set -euo pipefail

# Setup / update Ride App en Mac mini: pull, deps, migrate, build, autostart, PM2.
# Uso desde la raíz del repo:
#   ./macmini-stacks/bootstrap-ride-app.sh
#   ./macmini-stacks/bootstrap-ride-app.sh /ruta/custom/ride-app

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RIDE_APP_DIR="${1:-${RIDE_APP_DIR:-$REPO_ROOT/ride-app}}"

echo "========================================"
echo " Ride App — bootstrap Mac mini"
echo "========================================"
echo "Repo:     $REPO_ROOT"
echo "Ride app: $RIDE_APP_DIR"
echo ""

if [[ ! -d "$RIDE_APP_DIR" ]]; then
  echo "ERROR: no existe $RIDE_APP_DIR"
  exit 1
fi

if git -C "$REPO_ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo ">> git pull..."
  git -C "$REPO_ROOT" pull --ff-only origin main || true
fi

cd "$RIDE_APP_DIR"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo ">> Creado .env — edita JWT_SECRET y VITE_GOOGLE_MAPS_API_KEY antes de producción"
fi

if grep -qE 'JWT_SECRET=(change-me|$)' .env 2>/dev/null; then
  echo ">> WARN: JWT_SECRET sigue en default — cámbialo en .env"
fi

echo ">> npm install..."
npm install --legacy-peer-deps

echo ">> docker + migraciones..."
docker compose up -d
chmod +x scripts/migrate.sh scripts/start-stack.sh scripts/stop-stack.sh
./scripts/migrate.sh

echo ">> build..."
npm run build

echo ">> autostart..."
"$REPO_ROOT/macmini-stacks/install-ride-app.sh" "$RIDE_APP_DIR"

echo ""
echo "========================================"
echo " Listo"
echo "========================================"
echo "  Pasajero:  http://localhost:5174"
echo "  Conductor: http://localhost:5175"
echo "  Admin:     http://localhost:5176"
echo "  API:       http://localhost:4001/health"
echo ""
npm run health 2>/dev/null || curl -sf http://localhost:4001/health && echo " API ok"
