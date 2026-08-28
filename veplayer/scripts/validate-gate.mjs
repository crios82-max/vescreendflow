#!/usr/bin/env node
/**
 * Pre-fase gate — software consistency before new OBD phases.
 * 1) OBD parser parity (JS ↔ Kotlin)
 * 2) POLL list parity
 * 3) DBC decode smoke
 * 4) Fleet alert smokes fase 16–44 (SenseFlow API)
 *
 * Env: SENSEFLOW_URL (default http://127.0.0.1:4100)
 * Skip API: VALIDATE_SKIP_FLEET=1
 */
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const scripts = path.join(root, 'scripts')

function run(label, file, extraEnv = {}) {
  process.stdout.write(`\n▶ ${label}\n`)
  const r = spawnSync('node', [path.join(scripts, file)], {
    cwd: root,
    env: { ...process.env, ...extraEnv },
    encoding: 'utf8',
    stdio: 'inherit',
  })
  if (r.status !== 0) {
    console.error(`✗ ${label} failed`)
    process.exit(r.status ?? 1)
  }
}

async function main() {
  run('obd-pid-smoke', 'obd-pid-smoke.mjs')
  run('poll-parity', 'poll-parity.mjs')
  run('dbc-smoke', 'dbc-smoke.mjs')

  if (process.env.VALIDATE_SKIP_FLEET === '1') {
    console.log('\n⊘ fleet smokes skipped (VALIDATE_SKIP_FLEET=1)')
  } else {
    const base = process.env.SENSEFLOW_URL || 'http://127.0.0.1:4100'
    try {
      const h = await fetch(`${base}/api/health`)
      if (!h.ok) throw new Error(`health ${h.status}`)
    } catch (e) {
      console.error(`SenseFlow not reachable at ${base} — start API or set VALIDATE_SKIP_FLEET=1`)
      console.error(e)
      process.exit(1)
    }
    for (const n of [16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44]) {
      run(`fase${n}-smoke`, `fase${n}-smoke.mjs`)
    }
  }

  console.log('\n✓ validate-gate OK — safe to ship / start next fase')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
