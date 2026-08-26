#!/usr/bin/env bash
# Generate a field/fleet signing keystore for VePlayer release builds.
# Does NOT commit secrets — writes local files gitignored by root .gitignore.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AND="$ROOT/android"
KEYSTORE_DIR="$AND/keystore"
KEYSTORE="$KEYSTORE_DIR/veplayer-field.jks"
PROPS="$AND/keystore.properties"

mkdir -p "$KEYSTORE_DIR"

ALIAS="${VEPLAYER_KEY_ALIAS:-veplayer}"
STORE_PASS="${VEPLAYER_STORE_PASSWORD:-veplayer-field}"
KEY_PASS="${VEPLAYER_KEY_PASSWORD:-veplayer-field}"
CN="${VEPLAYER_KEY_CN:-VePlayer Field}"

if [[ -f "$KEYSTORE" ]]; then
  echo "Keystore already exists: $KEYSTORE"
else
  echo "Generating field keystore…"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "CN=$CN, OU=Fleet, O=VePlayer, L=Caracas, C=VE"
  echo "Created $KEYSTORE"
fi

cat > "$PROPS" <<EOF
storeFile=keystore/veplayer-field.jks
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
EOF

echo "Wrote $PROPS"
echo ""
echo "Build release:"
echo "  cd $AND && ./gradlew :app:assembleRelease"
echo "APK: app/build/outputs/apk/release/app-release.apk"
