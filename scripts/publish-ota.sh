#!/usr/bin/env bash
# Publish a signed APK to SenseFlow OTA hosting + register release + optional rollout.
#
# Usage:
#   ./scripts/publish-ota.sh path/to/app-release.apk 0.13.0 15 [notes]
# Env:
#   SENSEFLOW_URL   default http://127.0.0.1:4100
#   PUBLIC_BASE     public URL prefix for devices (default = SENSEFLOW_URL)
#   ROLLOUT=1       also queue silent OTA to outdated devices
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:?apk path required}"
VER_NAME="${2:?version_name required e.g. 0.13.0}"
VER_CODE="${3:?version_code required e.g. 15}"
NOTES="${4:-VePlayer $VER_NAME field release}"
BASE="${SENSEFLOW_URL:-http://127.0.0.1:4100}"
PUBLIC="${PUBLIC_BASE:-$BASE}"
OTA_DIR="$ROOT/../senseflow/ota"
# scripts live in veplayer/ → repo root is parent of veplayer
REPO="$(cd "$ROOT/.." && pwd)"
OTA_DIR="$REPO/senseflow/ota"

mkdir -p "$OTA_DIR"
FILE="veplayer-${VER_NAME}.apk"
DEST="$OTA_DIR/$FILE"
cp -f "$APK" "$DEST"
# also keep latest symlink-ish copy
cp -f "$APK" "$OTA_DIR/veplayer-latest.apk"

APK_URL="${PUBLIC%/}/ota/$FILE"
echo "Published file → $DEST"
echo "Public URL     → $APK_URL"

curl -sf -X POST "${BASE%/}/api/fleet/ota" \
  -H 'content-type: application/json' \
  -d "$(python3 - <<PY
import json
print(json.dumps({
  "version_name": "$VER_NAME",
  "version_code": int("$VER_CODE"),
  "apk_url": "$APK_URL",
  "notes": """$NOTES""",
}))
PY
)" >/dev/null

echo "Registered OTA $VER_NAME ($VER_CODE)"

if [[ "${ROLLOUT:-0}" == "1" ]]; then
  echo "Rolling out…"
  curl -sf -X POST "${BASE%/}/api/fleet/ota/rollout" \
    -H 'content-type: application/json' \
    -d "{\"version_code\":$VER_CODE,\"silent\":true}" | python3 -m json.tool
fi

curl -sf "${BASE%/}/api/fleet/ota/latest" | python3 -m json.tool
echo "OK publish-ota"
