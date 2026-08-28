#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log() { echo "[ride-app:migrate] $*"; }

if ! docker compose exec -T db pg_isready -U ride -d ride_app >/dev/null 2>&1; then
  log "ERROR: Postgres no disponible (docker compose up -d primero)"
  exit 1
fi

docker compose exec -T db psql -U ride -d ride_app -v ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migrations (
  name TEXT PRIMARY KEY,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
SQL

apply_migration() {
  local file="$1"
  local name
  name="$(basename "$file")"

  if docker compose exec -T db psql -U ride -d ride_app -tAc \
    "SELECT 1 FROM schema_migrations WHERE name = '$name'" | grep -q 1; then
    log "skip $name"
    return 0
  fi

  log "apply $name"
  docker compose exec -T db psql -U ride -d ride_app -v ON_ERROR_STOP=1 <"$file"
  docker compose exec -T db psql -U ride -d ride_app -v ON_ERROR_STOP=1 \
    -c "INSERT INTO schema_migrations (name) VALUES ('$name')"
}

for file in "$ROOT"/db/migrations/*.sql; do
  [[ -f "$file" ]] || continue
  apply_migration "$file"
done

log "migraciones OK"
