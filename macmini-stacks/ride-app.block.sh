#!/usr/bin/env bash
# Bloque Ride App — incluido por install-ride-app.sh o autostart.sh maestro

: "${RIDE_APP_DIR:=/Users/server/Documents/MOVIFY}"

if [[ -x "$RIDE_APP_DIR/scripts/start-stack.sh" ]]; then
  log "Ride App ($RIDE_APP_DIR)"
  "$RIDE_APP_DIR/scripts/start-stack.sh" >>"$LOG" 2>&1 || log "Ride App FAIL"
else
  log "Ride App SKIP (no script: $RIDE_APP_DIR/scripts/start-stack.sh)"
fi
