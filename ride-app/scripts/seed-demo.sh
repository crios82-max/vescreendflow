#!/usr/bin/env bash
# Crea usuarios demo para probar Movify en LAN (idempotente)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

API="${API_URL:-http://localhost:4001}"
PASS="${DEMO_PASSWORD:-movify123}"

ok() { echo "  ✓ $*"; }
warn() { echo "  ⚠ $*"; }
fail() { echo "  ✗ $*"; exit 1; }

echo "=============================================="
echo " Movify — seed usuarios demo"
echo "=============================================="
echo ""

if ! curl -sf "$API/health" >/dev/null; then
  fail "API no responde en $API — corre: npm run stack:start / prep:local"
fi
ok "API $API"

if ! command -v jq >/dev/null 2>&1; then
  fail "necesita jq (brew install jq)"
fi

post_register() {
  local body="$1"
  local email
  email=$(echo "$body" | jq -r .email)
  code=$(curl -s -o /tmp/movify-seed.json -w "%{http_code}" \
    -X POST "$API/auth/register" \
    -H 'Content-Type: application/json' \
    -d "$body")
  if [[ "$code" == "201" ]]; then
    ok "creado $email"
  elif [[ "$code" == "409" ]]; then
    warn "ya existe $email"
  else
    warn "register $email → HTTP $code $(cat /tmp/movify-seed.json)"
  fi
}

post_register "$(jq -n --arg p "$PASS" '{
  email: "pasajero@movify.demo",
  password: $p,
  name: "Pasajero Demo",
  role: "passenger",
  phone: "+584121000001"
}')"

post_register "$(jq -n --arg p "$PASS" '{
  email: "conductor@movify.demo",
  password: $p,
  name: "Conductor Demo",
  role: "driver",
  phone: "+584121000002",
  vehicleMake: "Toyota",
  vehicleModel: "Corolla",
  vehiclePlate: "DEMO01",
  vehicleType: "standard"
}')"

post_register "$(jq -n --arg p "$PASS" '{
  email: "moto@movify.demo",
  password: $p,
  name: "Repartidor Moto",
  role: "driver",
  phone: "+584121000004",
  vehicleMake: "Yamaha",
  vehicleModel: "FZ25",
  vehiclePlate: "MOTO01",
  vehicleType: "moto"
}')"

post_register "$(jq -n --arg p "$PASS" '{
  email: "bici@movify.demo",
  password: $p,
  name: "Repartidor Bici",
  role: "driver",
  phone: "+584121000005",
  vehicleMake: "Trek",
  vehicleModel: "FX 2",
  vehiclePlate: "BICI01",
  vehicleType: "bicicleta"
}')"

post_register "$(jq -n --arg p "$PASS" '{
  email: "admin@movify.demo",
  password: $p,
  name: "Admin Demo",
  role: "passenger",
  phone: "+584121000003"
}')"

echo ""
echo "Aplicando flags en DB (admin / verify / approve)..."

SQL=$(cat <<'SQL'
UPDATE users SET phone_verified = TRUE
 WHERE email IN (
   'pasajero@movify.demo',
   'conductor@movify.demo',
   'moto@movify.demo',
   'bici@movify.demo',
   'admin@movify.demo'
 );

UPDATE users SET is_admin = TRUE
 WHERE email = 'admin@movify.demo';

UPDATE driver_profiles SET approval_status = 'approved', rejection_reason = NULL
 WHERE user_id IN (
   SELECT id FROM users WHERE email IN (
     'conductor@movify.demo',
     'moto@movify.demo',
     'bici@movify.demo'
   )
 );
SQL
)

if docker compose exec -T db psql -U ride -d ride_app -v ON_ERROR_STOP=1 -c "$SQL" >/dev/null 2>&1; then
  ok "phone_verified + admin + conductor approved"
elif [[ -n "${DATABASE_URL:-}" ]] && command -v psql >/dev/null 2>&1; then
  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -c "$SQL" >/dev/null
  ok "flags vía DATABASE_URL"
else
  fail "no pude hablar con Postgres (docker compose up -d)"
fi

echo ""
echo "=============================================="
echo " Credenciales demo (password: $PASS)"
echo "=============================================="
echo "  Pasajero   pasajero@movify.demo   :5174"
echo "  Conductor  conductor@movify.demo  :5175"
echo "  Moto       moto@movify.demo       :5175  (entrega comida)"
echo "  Bici       bici@movify.demo       :5175  (entrega comida)"
echo "  Admin      admin@movify.demo      :5176"
echo ""
echo " Flujo rápido:"
echo "  1) Conductor → login → Ir online"
echo "  2) Pasajero → Viaje (auto) o Entrega de comida (moto/bici)"
echo "  3) Admin → ver stats"
echo "=============================================="
