#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_URL="${DATABASE_URL:-postgres://ride:ride_secret@localhost:5432/ride_app_test}"

echo ">> Setup test DB"
psql "$DB_URL" -f "$ROOT/db/init.sql"
for f in "$ROOT"/db/migrations/*.sql; do
  echo ">> $(basename "$f")"
  psql "$DB_URL" -f "$f"
done
echo ">> Test DB ready"
