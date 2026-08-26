#!/usr/bin/env bash
# Enable Device Owner for VePlayer (factory-reset / no accounts device).
# Usage:
#   ./scripts/enable-device-owner.sh              # release com.veplayer.app
#   ./scripts/enable-device-owner.sh com.veplayer.app.debug
set -euo pipefail

PKG="${1:-com.veplayer.app}"
ADMIN="${PKG}/com.veplayer.app.kiosk.VeDeviceAdminReceiver"

echo "==> VePlayer Device Owner playbook"
echo "    package: $PKG"
echo "    admin:   $ADMIN"
echo ""
echo "Prereqs:"
echo "  1. Device freshly wiped (or no Google accounts)"
echo "  2. APK already installed: adb install -r app-debug.apk"
echo "  3. USB debugging on"
echo ""

if ! command -v adb >/dev/null; then
  echo "adb not found" >&2
  exit 1
fi

adb wait-for-device
adb shell dpm set-device-owner "$ADMIN"
adb shell am start -n "${PKG}/com.veplayer.app.MainActivity"
echo ""
echo "OK — Device Owner set. Open Settings → Kiosk → Aplicar políticas / Lock Task."
echo "Verify: adb shell dumpsys device_policy | head"
