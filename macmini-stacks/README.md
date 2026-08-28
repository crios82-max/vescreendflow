# Mac mini stacks — autostart

Scripts para el LaunchAgent `com.macmini.stacks.autostart`.

## Ride App — todo en un comando

En el Mac mini, desde la raíz del repo (después de `git pull`):

```bash
chmod +x macmini-stacks/bootstrap-ride-app.sh
./macmini-stacks/bootstrap-ride-app.sh
```

Hace: `.env` → `npm install` → `build` → autostart → docker + PM2.

Solo edita `ride-app/.env` con tu Google Maps key y un `JWT_SECRET` fuerte si aún no lo hiciste.

## Solo autostart (si ya tienes build)

```bash
./macmini-stacks/install-ride-app.sh
```

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
