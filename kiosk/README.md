# Kiosk gratis — vescreenflow

Player: https://vescreenflow.com/play

## Flujo

1. En la TV/PC/Android abre el player (script, Chrome o APK).
2. Anota el código de 8 dígitos.
3. En el panel → **Pantallas → Agregar pantalla** → pega el código.
4. Opcional: **Grupos / Wall** → crea un video wall y asigna pantallas a celdas.
5. La pantalla empieza a reproducir (sync compartido en wall).

## Raspberry Pi

```bash
sudo apt update
sudo apt install -y chromium-browser unclutter
chmod +x kiosk-pi.sh
./kiosk-pi.sh
```

## Windows

1. Instala [Google Chrome](https://www.google.com/chrome/)
2. Doble clic en `kiosk-windows.bat`

## Android APK

Proyecto nativo: [`../android`](../android)

1. Compila con GitHub Actions (**Build Android APK**) o localmente (`./gradlew assembleDebug`).
2. Sideload del `.apk` en Android TV / Fire Stick / tablet.
3. Abre **vescreenflow** → empareja el código en el panel.

Alternativa rápida sin APK: Chrome → https://vescreenflow.com/play → Añadir a inicio.

## Video wall

1. Empareja N pantallas.
2. Dashboard → **Grupos / Wall** → **Nuevo video wall** (ej. 2x2).
3. Asigna cada pantalla a una celda y elige playlist.
4. Pulsa **Reiniciar sync** al cambiar layout.
5. Cada player muestra su porción del mismo contenido.

## Salir del kiosk

- Windows/Pi: `Alt+F4` o `Ctrl+W`
- Player: `F` pantalla completa · `R` nuevo código
