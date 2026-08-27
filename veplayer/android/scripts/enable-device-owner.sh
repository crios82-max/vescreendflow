#!/usr/bin/env bash
# Enable VePlayer as Device Owner (factory-reset / no accounts required).
# Prefer release package for field; debug uses .debug suffix.
set -euo pipefail
PKG="${1:-com.veplayer.app}"
ADMIN="$PKG/com.veplayer.app.kiosk.VeDeviceAdminReceiver"

echo "Target: $ADMIN"
echo "1) Remove previous owner if any…"
adb shell dpm remove-active-admin "$ADMIN" 2>/dev/null || true

echo "2) Setting device owner…"
adb shell dpm set-device-owner "$ADMIN"
adb shell dpm list-owners
echo "OK — reopen VePlayer; Lock Task should engage."
echo "Tip: full field path → veplayer/scripts/field-deploy.sh"
