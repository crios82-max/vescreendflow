#!/usr/bin/env bash
# Verificación LOCAL en Mac mini (sin DNS público) — corre antes del 18 sep o al regresar
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

ok() { echo "  ✓ $*"; }
warn() { echo "  ⚠ $*"; }
fail() { echo "  ✗ $*"; FAIL=1; }

FAIL=0

echo "=============================================="
echo " Movify — prep local (LAN / Mac mini)"
echo "=============================================="
echo ""

echo "1) Docker Postgres"
if docker compose ps --status running 2>/dev/null | grep -q ride; then
  ok "postgres corriendo"
else
  echo "   Iniciando..."
  docker compose up -d
  sleep 3
  if docker compose exec -T db pg_isready -U ride -d ride_app >/dev/null 2>&1; then
    ok "postgres listo"
  else
    fail "postgres no responde"
  fi
fi
echo ""

echo "2) Migraciones"
if ./scripts/migrate.sh; then
  ok "migraciones 002–008"
else
  fail "migrate.sh"
fi
echo ""

echo "3) Build"
if npm run build >/dev/null 2>&1; then
  ok "npm run build"
else
  fail "build falló — revisa logs"
fi
echo ""

echo "4) PM2 stack"
if pm2 jlist 2>/dev/null | grep -q '"name":"ride-api"'; then
  pm2 restart ride-api ride-passenger ride-driver ride-admin --update-env 2>/dev/null || true
  ok "PM2 reiniciado"
else
  warn "PM2 no configurado — corre: pm2 start ecosystem.config.cjs"
fi
echo ""

echo "5) Health local"
for url in "http://localhost:4001/health" "http://localhost:5174" "http://localhost:5175" "http://localhost:5176"; do
  if curl -sf -o /dev/null "$url" 2>/dev/null || curl -sf "$url" >/dev/null 2>&1; then
    ok "$url"
  else
    fail "$url — pm2 list / npm run stack:start"
  fi
done
echo ""

echo "5b) Usuarios demo"
if curl -sf "http://localhost:4001/health" >/dev/null 2>&1; then
  if ./scripts/seed-demo.sh; then
    ok "seed demo (pasajero / conductor / admin)"
  else
    warn "seed-demo falló — puedes correr: npm run seed:demo"
  fi
else
  warn "API down — seed omitido"
fi
echo ""

echo "6) .env (rellenar antes de pruebas reales)"
if [[ -f .env ]]; then
  need=()
  for key in JWT_SECRET GOOGLE_MAPS_API_KEY VITE_GOOGLE_MAPS_API_KEY EXPO_PUBLIC_GOOGLE_MAPS_API_KEY; do
    val=$(grep "^${key}=" .env 2>/dev/null | cut -d= -f2- || true)
    if [[ -z "$val" ]]; then need+=("$key"); fi
  done
  if grep -qE '^JWT_SECRET=(change-me|$)' .env 2>/dev/null; then need+=("JWT_SECRET (cambiar default)"); fi
  if [[ ${#need[@]} -eq 0 ]]; then
    ok "vars críticas presentes"
  else
    warn "falta: ${need[*]}"
    echo "     Bloque prod: npm run setup:prod"
  fi
else
  fail "no hay .env — cp .env.example .env"
fi
echo ""

echo "7) Listo para el 18 sep (público)"
echo "   Solo falta (cuando regreses):"
echo "   • Cloudflare CNAME movify-api + movify"
echo "   • .env prod (API_PUBLIC_URL, VITE_API_URL, CORS)"
echo "   • Reiniciar cloudflared"
echo "   • npm run go-live"
echo "   • TestFlight: docs/REGRESO-18SEP.md"
echo ""

if [[ "${FAIL:-0}" -eq 0 ]]; then
  echo "=============================================="
  echo " Stack LOCAL listo — prueba en LAN:"
  echo "   Pasajero  http://localhost:5174  (pasajero@movify.demo / movify123)"
  echo "   Conductor http://localhost:5175  (conductor@movify.demo / movify123)"
  echo "   Admin     http://localhost:5176  (admin@movify.demo / movify123)"
  echo "   Móvil     npm run dev:mobile (misma WiFi)"
  echo "   Re-seed   npm run seed:demo"
  echo "=============================================="
  exit 0
fi
echo "=============================================="
echo " Corrige los ✗ arriba"
echo "=============================================="
exit 1
