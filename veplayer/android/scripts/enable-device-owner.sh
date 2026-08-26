#!/usr/bin/env bash
# Enable VePlayer as Device Owner (factory-reset / no accounts required).
set -euo pipefail
PKG="${1:-com.veplayer.app.debug}"
ADMIN="$PKG/com.veplayer.app.kiosk.VeDeviceAdminReceiver"

echo "Target: $ADMIN"
echo "1) Uninstall previous owner if any…"
adb shell dpm remove-active-admin "$ADMIN" 2>/dev/null || true

echo "2) Install debug APK if needed (assembleDebug first)."
echo "3) Setting device owner…"
adb shell dpm set-device-owner "$ADMIN"
adb shell dpm list-owners
echo "OK — reopen VePlayer; Lock Task should engage."
