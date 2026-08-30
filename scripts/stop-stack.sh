#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log() { echo "[movify] $*"; }

log "PM2 stop..."
pm2 stop ride-api ride-passenger ride-driver ride-admin 2>/dev/null || true

log "Docker down..."
docker compose down

log "detenido"
