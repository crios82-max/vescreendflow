#!/usr/bin/env node
/** Parity: ObdBluetoothClient.POLL_PIDS ↔ obd-pid-registry ↔ ObdPidParser.kt */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { POLL_PID_HEX, pollPidByte, parseMode01 } from './obd-pid-registry.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const clientPath = path.join(
  root,
  'android/app/src/main/java/com/veplayer/app/vehicle/ObdBluetoothClient.kt',
)
const parserPath = path.join(root, 'android/app/src/main/java/com/veplayer/app/vehicle/ObdPidParser.kt')

function extractPollFromKotlin() {
  const text = fs.readFileSync(clientPath, 'utf8')
  const block = text.match(/POLL_PIDS\s*=\s*listOf\([\s\S]*?\)/)
  if (!block) throw new Error('POLL_PIDS block not found')
  return [...block[0].matchAll(/"([0-9A-F]{4})"/gi)].map((m) => m[1].toUpperCase())
}

function extractParserPids() {
  const text = fs.readFileSync(parserPath, 'utf8')
  const pids = new Set()
  for (const m of text.matchAll(/^\s*0x([0-9A-Fa-f]{2})\s*->/gm)) {
    pids.add(parseInt(m[1], 16))
  }
  return pids
}

const pollKotlin = extractPollFromKotlin()
const registry = [...POLL_PID_HEX]
const parserPids = extractParserPids()

let fail = 0
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b)

if (!eq(pollKotlin.sort(), registry.sort())) {
  console.error('POLL_PIDS mismatch kotlin vs registry')
  console.error('kotlin only:', pollKotlin.filter((p) => !registry.includes(p)))
  console.error('registry only:', registry.filter((p) => !pollKotlin.includes(p)))
  fail++
}

for (const hex of pollKotlin) {
  const byte = pollPidByte(hex)
  if (!parserPids.has(byte)) {
    console.error('parser missing PID', hex, 'byte', byte)
    fail++
  }
  const sample = parseMode01(`41 ${byte.toString(16).padStart(2, '0').toUpperCase()} 00 00`)
  if (!Object.keys(sample).length && byte !== 0x0d) {
    // single-byte PIDs still return a field; 2-byte need more data — spot-check known
    const twoByte = [0x0c, 0x10, 0x34, 0x43, 0x44, 0x53, 0x59, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x5d, 0x63, 0x1f, 0x21, 0x31, 0x42, 0x5e, 0x7f, 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x6d, 0x6e]
    const threeByte = [0x67, 0x68, 0x8b]
    const defByte = [0x9b]
    const lambdaByte = [0x8c, 0x9c]
    const pmByte = [0x8f]
    const egtByte = [0x98, 0x99]
    const noxByte = [0x94]
    const defDoseByte = [0xa5]
    const noxCorrByte = [0xa1]
    const noxConcByte = [0xa7]
    const noxCorrS34Byte = [0xa8]
    const cylFuelByte = [0xa2]
    const evapSysByte = [0xa3]
    const transGearByte = [0xa4]
    const twoByteMin2 = [0x6b]
    if (noxByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 00 00 00 19`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (defDoseByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 01 BE`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (noxCorrByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 00 00 00 00 00 00 00 03 84`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (noxConcByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 03 84 00 00`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (noxCorrS34Byte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 03 84 00 00`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (cylFuelByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 07 00`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (evapSysByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 00 23 28`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (transGearByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 02 00 0E 10`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (egtByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 01 24 54 24 54 24 54 24 54`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (defByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 00 00 00 1A`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (pmByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 00 00 23 28 00 23 28`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (lambdaByte.includes(byte)) {
      const suffix =
        byte === 0x9c
          ? '00 00 00 00 00 00 00 00 00 00 00 00 00 00 27 10'
          : '00 00 00 00 00 00 00 00 00 27 10'
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} ${suffix}`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (threeByte.includes(byte)) {
      const suffix = byte === 0x8b ? '00 D9' : '50 8C'
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 00 ${suffix}`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (twoByteMin2.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 00 8C`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    } else if (!twoByte.includes(byte)) {
      const got = parseMode01(`41 ${byte.toString(16).padStart(2, '0')} 28`)
      if (!Object.keys(got).length) {
        console.error('registry parse empty for polled PID', hex)
        fail++
      }
    }
  }
}

if (fail) {
  console.error('poll-parity FAIL', fail)
  process.exit(1)
}
console.log('poll-parity OK ·', pollKotlin.length, 'PIDs aligned kotlin/registry/parser')
