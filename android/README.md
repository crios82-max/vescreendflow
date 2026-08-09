# vescreenflow Android player (kiosk)

WebView fullscreen → https://vescreenflow.com/play

## Build (local)

Requiere JDK 17 + Android SDK:

```bash
cd android
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Build (CI)

Push a `android/**` o dispara el workflow **Build Android APK** en GitHub Actions.
Descarga el artifact `vescreenflow-player`.

## Install (sideload)

1. En Android TV / Fire / tablet: activa **Orígenes desconocidos** / apps de origen desconocido.
2. Copia el APK e instálalo.
3. Abre **vescreenflow** → verás el código de empareje.
4. En el panel web: **Pantallas → Agregar pantalla**.
