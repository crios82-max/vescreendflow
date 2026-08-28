# Ride App — Autostart Mac mini

Integración con el LaunchAgent maestro `com.macmini.stacks.autostart`.

## Puertos

| Servicio | Puerto |
|----------|--------|
| API | 4001 |
| Pasajero web | 5174 |
| Conductor web | 5175 |
| PostgreSQL (Docker) | 5436 |

## Primera vez (manual)

```bash
cd /Users/server/Documents/vescreendflow/ride-app   # ajusta ruta
cp .env.example .env
# JWT_SECRET, VITE_GOOGLE_MAPS_API_KEY, CORS_ORIGINS si usas LAN

npm install --legacy-peer-deps
npm run build
chmod +x scripts/start-stack.sh scripts/stop-stack.sh
./scripts/start-stack.sh
```

## Bloque para `autostart.sh`

Agrega esto en `~/Library/Application Support/macmini-stacks/autostart.sh`, **después** de que Docker Desktop esté arriba:

```bash
# --- Ride App (:4001 / :5174 / :5175 / db :5436) ---
RIDE_APP_DIR="${RIDE_APP_DIR:-/Users/server/Documents/vescreendflow/ride-app}"
if [[ -x "$RIDE_APP_DIR/scripts/start-stack.sh" ]]; then
  log "Ride App"
  "$RIDE_APP_DIR/scripts/start-stack.sh" >>"$LOG" 2>&1 || log "Ride App FAIL"
else
  log "Ride App SKIP (no dir: $RIDE_APP_DIR)"
fi
```

Si el repo vive en otra ruta, exporta antes del bloque:

```bash
export RIDE_APP_DIR="/Users/server/ruta/ride-app"
```

## Comandos útiles

```bash
./scripts/start-stack.sh    # docker + pm2 (idempotente)
./scripts/stop-stack.sh     # para el stack
npm run pm2:logs
npm run health
pm2 list | grep ride-
docker ps | grep ride-app-db
curl http://localhost:4001/health
```

## PM2 persistencia

Tras el primer `start-stack.sh` exitoso:

```bash
pm2 save
# pm2 startup ya debería estar configurado en el Mac mini
```

## Verificación post-boot

```bash
launchctl print "gui/$(id -u)/com.macmini.stacks.autostart"
tail -50 ~/Library/Application\ Support/macmini-stacks/autostart.log | grep ride-app
curl -sf http://localhost:4001/health && echo OK
```
