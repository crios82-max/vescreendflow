#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API="${API:-http://127.0.0.1:4100}"

echo "== health =="
curl -sf "$API/api/health" | tee /tmp/senseflow-health.json
echo

echo "== stats =="
curl -sf "$API/api/stats" | tee /tmp/senseflow-stats.json
echo

echo "== traffic =="
curl -sf "$API/api/traffic" | tee /tmp/senseflow-traffic.json | head -c 400
echo "…"

echo "== crowd =="
curl -sf "$API/api/crowd" | tee /tmp/senseflow-crowd.json | head -c 400
echo "…"

echo "== post ping =="
curl -sf -X POST "$API/api/pings" \
  -H 'content-type: application/json' \
  -d '{"pings":[{"lat":10.497,"lng":-66.899,"speed_mps":3.2,"activity":"ON_FOOT","device_bucket":"smoke_test_device_01","accuracy_m":10}]}'
echo
echo "OK"
