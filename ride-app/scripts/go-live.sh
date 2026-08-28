#!/usr/bin/env bash
# Go-live Movify: verifica DNS, tunnel, stack local y guía TestFlight
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

TUNNEL_ID="${MOVIFY_TUNNEL_ID:-55818726-7a1f-459c-a904-00f5487e6aad}"
TUNNEL_CNAME="${TUNNEL_ID}.cfargotunnel.com"
API_HOST="movify-api.vescreenflow.com"
WEB_HOST="movify.vescreenflow.com"

ok() { echo "  ✓ $*"; }
warn() { echo "  ⚠ $*"; }
fail() { echo "  ✗ $*"; FAIL=1; }

FAIL=0

echo "=============================================="
echo " Movify — Go Live"
echo "=============================================="
echo ""

echo "1) DNS Cloudflare (hazlo en el dashboard si falta)"
echo "   Zona: vescreenflow.com → DNS → Add record"
echo ""
printf "   %-10s %-12s %-55s %s\n" "Type" "Name" "Target" "Proxy"
printf "   %-10s %-12s %-55s %s\n" "CNAME" "movify-api" "$TUNNEL_CNAME" "Proxied"
printf "   %-10s %-12s %-55s %s\n" "CNAME" "movify" "$TUNNEL_CNAME" "Proxied"
echo ""
echo "   Luego reinicia cloudflared en el Mac mini (npm run tunnel o LaunchAgent)."
echo ""

echo "2) Resolución DNS"
if command -v dig >/dev/null 2>&1; then
  for host in "$API_HOST" "$WEB_HOST"; do
    if dig +short "$host" 2>/dev/null | grep -q .; then
      ok "$host resuelve → $(dig +short "$host" | head -1)"
    else
      fail "$host no resuelve — añade CNAME en Cloudflare"
    fi
  done
else
  warn "dig no instalado — salta verificación DNS local"
fi
echo ""

echo "3) Endpoints públicos"
if curl -sf --max-time 12 "https://${API_HOST}/health" >/dev/null 2>&1; then
  ok "https://${API_HOST}/health"
else
  fail "https://${API_HOST}/health — DNS + cloudflared + PM2 ride-api"
fi

if curl -sf --max-time 12 -o /dev/null "https://${WEB_HOST}" 2>/dev/null; then
  ok "https://${WEB_HOST}"
else
  fail "https://${WEB_HOST} — DNS + cloudflared + ride-passenger"
fi
echo ""

echo "4) Stack local (Mac mini)"
if curl -sf http://localhost:4001/health >/dev/null 2>&1; then
  ok "localhost:4001 API"
else
  warn "API local no responde — pm2 / docker"
fi
echo ""

echo "5) .env producción"
if [[ -f .env ]]; then
  ./scripts/setup-prod-env.sh .env | sed -n '/Estado actual/,$p'
else
  fail "falta ride-app/.env — cp .env.example .env && npm run setup:prod"
fi
echo ""

echo "6) TestFlight (cuando API público OK)"
echo "   a) Edita apps/mobile/eas.json → appleId, ascAppId, appleTeamId"
echo "   b) expo.dev → Secrets:"
echo "      EXPO_PUBLIC_API_URL=https://${API_HOST}"
echo "      EXPO_PUBLIC_GOOGLE_MAPS_API_KEY=<tu key>"
echo "   c) eas login && ./scripts/eas-testflight.sh"
echo ""
echo "   Guía completa: docs/GO_LIVE.md"
echo ""

if [[ "${FAIL:-0}" -eq 0 ]]; then
  echo "=============================================="
  echo " Listo para probar en iPhone / web pública"
  echo "=============================================="
  exit 0
fi

echo "=============================================="
echo " Pendientes arriba — empieza por DNS Cloudflare"
echo "=============================================="
exit 1
