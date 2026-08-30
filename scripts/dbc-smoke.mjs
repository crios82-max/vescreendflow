#!/usr/bin/env node
/**
 * DBC parser + Intel bit-extract smoke (mirrors Kotlin DbcParser / DbcBitExtract / mapper).
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const dbcPath = path.join(root, 'android/app/src/main/assets/dbc/veplayer_demo.dbc')

const bo = /^BO_\s+(\d+)\s+(\w+)\s*:\s*(\d+)\s+(\w+)/
const sg =
  /^\s*SG_\s+(\w+)\s*(?:M|m\d+)?\s*:\s*(\d+)\|(\d+)@([01])([+-])\s*\(\s*([^,]+)\s*,\s*([^)]+)\s*\)\s*\[\s*([^|]*)\s*\|\s*([^\]]*)\s*\]\s*"([^"]*)"/

function parseDbc(text) {
  const messages = new Map()
  let cur = null
  const flush = () => {
    if (!cur) return
    messages.set(cur.id, cur)
    cur = null
  }
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trimEnd()
    const bm = line.match(bo)
    if (bm) {
      flush()
      cur = { id: +bm[1], name: bm[2], dlc: +bm[3], signals: [] }
      continue
    }
    const sm = line.match(sg)
    if (sm && cur) {
      cur.signals.push({
        name: sm[1],
        startBit: +sm[2],
        length: +sm[3],
        littleEndian: sm[4] === '1',
        signed: sm[5] === '-',
        factor: +sm[6],
        offset: +sm[7],
        unit: sm[10],
      })
    }
  }
  flush()
  return messages
}

function extractIntel(data, startBit, length) {
  let value = 0n
  for (let i = 0; i < length; i++) {
    const bit = startBit + i
    const byteIndex = Math.floor(bit / 8)
    const bitInByte = bit % 8
    if (byteIndex >= data.length) continue
    if ((data[byteIndex] >> bitInByte) & 1) value |= 1n << BigInt(i)
  }
  return value
}

function physical(data, sig) {
  let raw = extractIntel(data, sig.startBit, sig.length)
  if (sig.signed) {
    const signBit = 1n << BigInt(sig.length - 1)
    if (raw & signBit) raw -= 1n << BigInt(sig.length)
  }
  return Number(raw) * sig.factor + sig.offset
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg)
}

const text = fs.readFileSync(dbcPath, 'utf8')
const messages = parseDbc(text)
assert(messages.size === 9, `expected 9 BO_, got ${messages.size}`)
assert(messages.get(256)?.signals.some((s) => s.name === 'Speed_Kmh'), 'speed sig')
assert(messages.get(261)?.signals.find((s) => s.name === 'Steering')?.signed === true, 'steering signed')

// frame 0x100 speed 40 km/h
{
  const msg = messages.get(256)
  const sig = msg.signals[0]
  const v = physical([0x28], sig)
  assert(v === 40, `speed got ${v}`)
}

// frame 0x105 steering -12.5° → raw int16 -125 little endian = 0x83 0xFF
{
  const sig = messages.get(261).signals.find((s) => s.name === 'Steering')
  const v = physical([0x83, 0xff, 0, 0], sig)
  assert(Math.abs(v - -12.5) < 1e-9, `steer got ${v}`)
}

// frame 0x104 SOC 70 fuel 40
{
  const msg = messages.get(260)
  const soc = physical([70, 40], msg.signals.find((s) => s.name === 'SOC'))
  const fuel = physical([70, 40], msg.signals.find((s) => s.name === 'Fuel'))
  assert(soc === 70 && fuel === 40, `energy ${soc}/${fuel}`)
}

// ABS bit0 + parking bit1 on flags
{
  const msg = messages.get(262)
  const abs = physical([0b011], msg.signals.find((s) => s.name === 'ABS'))
  const park = physical([0b011], msg.signals.find((s) => s.name === 'Parking'))
  assert(abs === 1 && park === 1, `flags abs=${abs} park=${park}`)
}

console.log('OK dbc-smoke', messages.size, 'messages from', path.basename(dbcPath))

// SenseFlow static optional
const BASE = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
try {
  const r = await fetch(BASE + '/dbc/veplayer_demo.dbc')
  if (r.ok) {
    const remote = await r.text()
    assert(remote.includes('BO_ 256'), 'remote DBC')
    console.log('SenseFlow /dbc/veplayer_demo.dbc OK')

    const deviceId = `dbc-smoke-${Date.now().toString(36)}`
    await fetch(BASE + '/api/fleet/register', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        device_id: deviceId,
        name: 'DBC smoke',
        app_version: '0.14.0',
        version_code: 16,
      }),
    })
    const cmd = await fetch(BASE + '/api/fleet/command', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        device_id: deviceId,
        command: 'set_dbc',
        payload: { url: `${BASE}/dbc/veplayer_demo.dbc` },
      }),
    })
    const j = await cmd.json()
    assert(cmd.ok && j.id, 'set_dbc cmd')
    console.log('set_dbc queued', j.id)
  } else {
    console.log('SenseFlow not up — skipped remote checks')
  }
} catch {
  console.log('SenseFlow not up — skipped remote checks')
}
