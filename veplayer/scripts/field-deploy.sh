#!/usr/bin/env bash
# Field deploy: install signed release APK + Device Owner + launch + dump diagnostics.
#
# Usage:
#   ./scripts/field-deploy.sh [apk_path] [package]
# Env:
#   SENSEFLOW_URL   optional — pushed into app prefs via adb (best-effort)
#   SKIP_OWNER=1    skip dpm set-device-owner
#   DEVICE_SERIAL   adb -s
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/android/app/build/outputs/apk/release/app-release.apk}"
PKG="${2:-com.veplayer.app}"
ADMIN="$PKG/com.veplayer.app.kiosk.VeDeviceAdminReceiver"
ADB=(adb)
[[ -n "${DEVICE_SERIAL:-}" ]] && ADB=(adb -s "$DEVICE_SERIAL")

echo "==> VePlayer field deploy"
echo "    apk: $APK"
echo "    pkg: $PKG"

if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK not found. Build first:" >&2
  echo "  $ROOT/scripts/gen-field-keystore.sh" >&2
  echo "  cd $ROOT/android && ./gradlew :app:assembleRelease" >&2
  exit 1
fi

"${ADB[@]}" wait-for-device
echo "-- device:"
"${ADB[@]}" devices -l | head -5

echo "-- uninstall previous (keep data if possible fails ok)"
"${ADB[@]}" uninstall "$PKG" 2>/dev/null || true
# also remove debug variant if present
"${ADB[@]}" uninstall "${PKG}.debug" 2>/dev/null || true

echo "-- install release"
"${ADB[@]}" install -r "$APK"

echo "-- runtime permissions (best-effort)"
for p in \
  android.permission.CAMERA \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.ACCESS_BACKGROUND_LOCATION \
  android.permission.POST_NOTIFICATIONS \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.BLUETOOTH_SCAN
do
  "${ADB[@]}" shell pm grant "$PKG" "$p" 2>/dev/null || true
done

if [[ "${SKIP_OWNER:-0}" != "1" ]]; then
  echo "-- Device Owner"
  "${ADB[@]}" shell dpm remove-active-admin "$ADMIN" 2>/dev/null || true
  if ! "${ADB[@]}" shell dpm set-device-owner "$ADMIN"; then
    echo "WARN: set-device-owner failed (accounts on device? factory reset needed)" >&2
  else
    "${ADB[@]}" shell dpm list-owners || true
  fi
fi

if [[ -n "${SENSEFLOW_URL:-}" ]]; then
  echo "-- SenseFlow URL hint: set in Ajustes → $SENSEFLOW_URL"
fi

echo "-- launch"
"${ADB[@]}" shell am start -n "$PKG/com.veplayer.app.MainActivity" \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

sleep 2
echo "-- dumpsys device_policy (owners):"
"${ADB[@]}" shell dumpsys device_policy 2>/dev/null | head -40 || true

echo "-- send remote apply_kiosk if SenseFlow reachable (optional DEVICE_ID)"
if [[ -n "${SENSEFLOW_URL:-}" && -n "${DEVICE_ID:-}" ]]; then
  curl -sf -X POST "${SENSEFLOW_URL%/}/api/fleet/command" \
    -H 'content-type: application/json' \
    -d "{\"device_id\":\"$DEVICE_ID\",\"command\":\"apply_kiosk\",\"payload\":{}}" \
    && echo " apply_kiosk queued" || echo " (command skip)"
fi

echo "OK — field deploy done. Open Ajustes (PIN) → Campo → Diagnóstico."
