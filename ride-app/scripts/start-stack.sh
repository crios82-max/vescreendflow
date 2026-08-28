#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log() { echo "[ride-app] $*"; }

if [[ ! -f .env ]]; then
  log "SKIP: no .env (cp .env.example .env y configura)"
  exit 0
fi

log "DB docker compose up..."
docker compose up -d

log "Esperando Postgres..."
for i in {1..30}; do
  if docker compose exec -T db pg_isready -U ride -d ride_app >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! docker compose exec -T db pg_isready -U ride -d ride_app >/dev/null 2>&1; then
  log "ERROR: Postgres no respondió"
  exit 1
fi

if [[ ! -f apps/api/dist/index.js ]]; then
  log "Build inicial..."
  npm run build
fi

if pm2 jlist 2>/dev/null | grep -q '"name":"ride-api"'; then
  log "PM2 restart..."
  pm2 restart ride-api ride-passenger ride-driver --update-env
else
  log "PM2 start..."
  pm2 start ecosystem.config.cjs
fi

pm2 save >/dev/null 2>&1 || true

if curl -sf http://localhost:4001/health >/dev/null; then
  log "OK api :4001"
else
  log "WARN api health fail"
fi

if curl -sf -o /dev/null http://localhost:5174; then
  log "OK passenger :5174"
else
  log "WARN passenger fail"
fi

if curl -sf -o /dev/null http://localhost:5175; then
  log "OK driver :5175"
else
  log "WARN driver fail"
fi

log "listo"
