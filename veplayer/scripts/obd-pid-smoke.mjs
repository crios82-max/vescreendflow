#!/usr/bin/env node
/** OBD Mode 01 parser parity smoke — mirrors ObdPidParser.kt */
import { runObdSmokeCases } from './obd-pid-registry.mjs'

const fail = runObdSmokeCases()
if (fail) process.exit(1)
console.log('obd pid parser smoke OK')
