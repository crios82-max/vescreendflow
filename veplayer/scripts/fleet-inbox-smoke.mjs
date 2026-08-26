#!/usr/bin/env node
/**
 * Fleet inbox / voice phrase smoke (VePlayer 0.24 — fase 4).
 */

function voicePhrase(kind, severity, text) {
  const body = String(text || '')
    .trim()
    .replace(/\.+$/, '')
  if (kind.startsWith('geofence')) return `Alerta de zona. ${body}.`
  if (kind === 'abs') return 'Atención. Sistema ABS activo.'
  if (kind === 'tpms_low') return 'Atención. Presión de neumáticos baja.'
  if (kind === 'soc_low') return `Atención. Batería baja. ${body}.`
  if (kind === 'message') return `Mensaje de flota. ${body}.`
  if (severity === 'warn') return `Alerta. ${body}.`
  return `Aviso de flota. ${body}.`
}

function assert(c, m) {
  if (!c) throw new Error(m)
}

assert(
  voicePhrase('geofence_enter:1', 'info', 'Entró a geofence «Base»').includes('zona'),
  'geofence',
)
assert(voicePhrase('abs', 'warn', 'ABS activo').includes('ABS'), 'abs')
assert(voicePhrase('message', 'info', 'Hola equipo').startsWith('Mensaje de flota'), 'msg')
assert(voicePhrase('tpms_low', 'warn', 'x').includes('neumáticos'), 'tpms')
assert(voicePhrase('soc_low', 'warn', 'SOC bajo (12%)').includes('Batería'), 'soc')

// Dedupe key simulation
const spoken = new Set()
function ingest(id) {
  if (spoken.has(id)) return false
  spoken.add(id)
  return true
}
assert(ingest(10) === true && ingest(10) === false, 'dedupe')
assert(ingest(11) === true, 'new id')

console.log('OK fleet-inbox ·', voicePhrase('message', 'info', 'Ruta a Altamira'))
console.log('OK fleet-inbox-smoke')
