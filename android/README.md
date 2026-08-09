# vescreenflow Android player (kiosk)

WebView fullscreen → https://vescreenflow.com/play  
Package ID: `com.vescreenflow.player`

## Build debug (sideload)

```bash
cd android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

O GitHub Actions → **Build Android APK** → artifact `vescreenflow-player`.

## Play Store (AAB firmado)

### 1. Crear upload keystore (una sola vez)

```bash
chmod +x android/scripts/create-upload-keystore.sh
./android/scripts/create-upload-keystore.sh
```

Guarda `android/release/vescreenflow-upload.jks` y las contraseñas fuera del repo.

### 2. Secretos en GitHub

Repo → Settings → Secrets and variables → Actions:

| Secret | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -i android/release/vescreenflow-upload.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | de `keystore.properties` |
| `ANDROID_KEY_ALIAS` | `vescreenflow` |
| `ANDROID_KEY_PASSWORD` | de `keystore.properties` |

### 3. Compilar AAB

Actions → **Build Android Release (AAB)** → Run workflow.  
Artifact: `vescreenflow-player-aab` (`.aab`).

### 4. Play Console

1. Crear app → tipo **App** / también TV si quieres Android TV.
2. Activar **Play App Signing** (recomendado).
3. Subir el `.aab` en Producción o prueba interna.
4. Política de privacidad: `https://vescreenflow.com/privacy`
5. Completar ficha (icono 512, capturas, descripción) y enviar a revisión.

Cada release nueva: sube `versionCode` en `android/app/build.gradle.kts`.

## Install (sideload)

1. Activa orígenes desconocidos.
2. Instala el APK debug.
3. Abre **vescreenflow** → código de empareje.
4. Dashboard → **Pantallas → Agregar pantalla**.
