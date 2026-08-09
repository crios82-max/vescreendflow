#!/usr/bin/env bash
# Creates a Play Console upload keystore (keep it forever; losing it blocks updates).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/release"
JKS="$OUT_DIR/vescreenflow-upload.jks"
PROPS="$ROOT/keystore.properties"
ALIAS="vescreenflow"

mkdir -p "$OUT_DIR"

if [[ -f "$JKS" ]]; then
  echo "Already exists: $JKS"
  exit 1
fi

STORE_PASS="${ANDROID_KEYSTORE_PASSWORD:-$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)}"
KEY_PASS="${ANDROID_KEY_PASSWORD:-$STORE_PASS}"

if command -v keytool >/dev/null 2>&1 && keytool -help >/dev/null 2>&1; then
  KEYTOOL=(keytool)
elif command -v docker >/dev/null 2>&1; then
  KEYTOOL=(docker run --rm -v "$OUT_DIR:/work" eclipse-temurin:17-jdk keytool)
  JKS_IN_CMD="/work/vescreenflow-upload.jks"
else
  echo "Need JDK keytool or Docker."
  exit 1
fi

JKS_ARG="${JKS_IN_CMD:-$JKS}"

"${KEYTOOL[@]}" -genkeypair -v \
  -storetype PKCS12 \
  -keystore "$JKS_ARG" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=vescreenflow, OU=Mobile, O=vescreenflow, L=Caracas, C=VE"

cat > "$PROPS" <<EOF
storeFile=release/vescreenflow-upload.jks
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
EOF

echo
echo "Created:"
echo "  $JKS"
echo "  $PROPS"
echo
echo "Add these GitHub Actions secrets (repo → Settings → Secrets):"
echo "  ANDROID_KEYSTORE_BASE64 = base64 of the .jks file"
echo "  ANDROID_KEYSTORE_PASSWORD = (from keystore.properties)"
echo "  ANDROID_KEY_ALIAS = $ALIAS"
echo "  ANDROID_KEY_PASSWORD = (from keystore.properties)"
echo
echo "Backup the .jks + passwords offline. Do not commit them."
if command -v base64 >/dev/null 2>&1; then
  echo
  echo "base64 one-liner:"
  echo "  base64 -i \"$JKS\" | pbcopy   # macOS: copies to clipboard"
fi
