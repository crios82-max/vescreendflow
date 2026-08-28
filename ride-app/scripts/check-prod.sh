#!/usr/bin/env bash
# Verifica stack local + endpoints públicos Ride (Mac mini / prod)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

API_LOCAL="${API_LOCAL:-http://localhost:4001}"
API_PUBLIC="${API_PUBLIC:-https://movify-api.vescreenflow.com}"
WEB_PUBLIC="${WEB_PUBLIC:-https://movify.vescreenflow.com}"

pass() { echo "  OK  $*"; }
fail() { echo "  FAIL $*"; FAIL=1; }

FAIL=0

echo "== Movify — check producción =="
echo ""

echo "Local (PM2 + docker):"
if curl -sf "${API_LOCAL}/health" >/dev/null 2>&1; then
  pass "API ${API_LOCAL}/health"
else
  fail "API ${API_LOCAL}/health"
fi

for port in 5174 5175 5176; do
  if curl -sf -o /dev/null "http://localhost:${port}" 2>/dev/null; then
    pass "web :${port}"
  else
    fail "web :${port}"
  fi
done

if docker compose ps --status running 2>/dev/null | grep -q ride; then
  pass "postgres docker"
else
  fail "postgres docker (docker compose up -d)"
fi

echo ""
echo "Público (Cloudflare DNS + tunnel):"

if curl -sf "${API_PUBLIC}/health" >/dev/null 2>&1; then
  pass "${API_PUBLIC}/health"
else
  fail "${API_PUBLIC}/health — ¿CNAME movify-api + cloudflared reiniciado?"
fi

if curl -sf -o /dev/null "${WEB_PUBLIC}" 2>/dev/null; then
  pass "${WEB_PUBLIC}"
else
  fail "${WEB_PUBLIC} — ¿CNAME movify + npm run build con VITE_API_URL?"
fi

echo ""
if [[ -f .env ]]; then
  echo ".env (revisión rápida):"
  grep -E '^(JWT_SECRET|VITE_API_URL|API_PUBLIC_URL|PASSENGER_WEB_URL|GOOGLE_MAPS|STRIPE_SECRET|TWILIO_ACCOUNT)=' .env 2>/dev/null \
    | sed 's/=.*/=***/' || true
  if grep -qE 'JWT_SECRET=(change-me|$)' .env 2>/dev/null; then
    fail "JWT_SECRET sigue en default"
  fi
  if grep -qE 'VITE_API_URL=http://localhost' .env 2>/dev/null; then
    echo "  WARN VITE_API_URL apunta a localhost — rebuild antes de prod pública"
  fi
else
  fail "falta ride-app/.env"
fi

echo ""
if [[ "${FAIL:-0}" -eq 0 ]]; then
  echo "Todo OK"
  exit 0
fi
echo "Hay fallos — ver docs/CLOUDFLARE_TUNNEL.md"
exit 1
