# Mac mini stacks — autostart

Scripts para el LaunchAgent `com.macmini.stacks.autostart`.

## Ride App (un comando)

En el Mac mini, desde el repo:

```bash
chmod +x macmini-stacks/install-ride-app.sh
./macmini-stacks/install-ride-app.sh
# o con ruta custom:
./macmini-stacks/install-ride-app.sh /Users/server/Documents/vescreendflow/ride-app
```

Esto:
1. Agrega el bloque Ride App a `~/Library/Application Support/macmini-stacks/autostart.sh` (si no existe)
2. Instala/recarga el LaunchAgent
3. Ejecuta `ride-app/scripts/start-stack.sh` si ya hay `.env`

## Requisito previo

```bash
cd ride-app
cp .env.example .env   # JWT + Google Maps key
npm install --legacy-peer-deps
npm run build
```

## Verificar

```bash
launchctl print "gui/$(id -u)/com.macmini.stacks.autostart"
tail -30 ~/Library/Application\ Support/macmini-stacks/autostart.log
curl http://localhost:4001/health
pm2 list | grep ride-
```

## Puertos Ride App

| Servicio | Puerto |
|----------|--------|
| API | 4001 |
| Pasajero | 5174 |
| Conductor | 5175 |
| Postgres | 5436 |
