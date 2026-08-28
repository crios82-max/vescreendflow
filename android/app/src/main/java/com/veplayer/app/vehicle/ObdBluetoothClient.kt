package com.veplayer.app.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class ObdLinkState {
    IDLE,
    CONNECTING,
    READY,
    POLLING,
    FALLBACK_SIM,
    ERROR,
}

data class ObdBondedDevice(
    val name: String,
    val address: String,
)

/**
 * Bluetooth Classic RFCOMM client for ELM327 (SPP UUID).
 * Falls back cleanly when adapter/permission/dongle missing.
 */
class ObdBluetoothClient(private val context: Context) {
    private val lock = Any()
    private var socket: BluetoothSocket? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val open = AtomicBoolean(false)

    @Volatile var lastError: String? = null
        private set

    fun adapter(): BluetoothAdapter? {
        val mgr = context.getSystemService(BluetoothManager::class.java)
        return mgr?.adapter
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<ObdBondedDevice> {
        if (!hasConnectPermission()) return emptyList()
        val adapter = adapter() ?: return emptyList()
        return runCatching {
            adapter.bondedDevices
                .orEmpty()
                .map { ObdBondedDevice(name = it.name ?: "BT", address = it.address) }
                .sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Boolean =
        withContext(Dispatchers.IO) {
            disconnect()
            lastError = null
            if (address.isBlank()) {
                lastError = "MAC vacío"
                return@withContext false
            }
            if (!hasConnectPermission()) {
                lastError = "Falta BLUETOOTH_CONNECT"
                return@withContext false
            }
            val adapter = adapter()
            if (adapter == null || !adapter.isEnabled) {
                lastError = "Bluetooth apagado / no disponible"
                return@withContext false
            }
            try {
                val device: BluetoothDevice = adapter.getRemoteDevice(address.uppercase())
                // Cancel discovery so RFCOMM is faster/more reliable
                runCatching { adapter.cancelDiscovery() }
                val sock =
                    withTimeoutOrNull(12_000L) {
                        device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                    }
                if (sock == null || !sock.isConnected) {
                    lastError = "Timeout RFCOMM $address"
                    return@withContext false
                }
                synchronized(lock) {
                    socket = sock
                    writer = OutputStreamWriter(sock.outputStream, Charsets.US_ASCII)
                    reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.US_ASCII))
                    open.set(true)
                }
                if (!initElm()) {
                    disconnect()
                    return@withContext false
                }
                true
            } catch (e: SecurityException) {
                lastError = "Permiso BT denegado"
                Log.w(TAG, "connect security", e)
                false
            } catch (e: Exception) {
                lastError = e.message ?: "connect fail"
                Log.w(TAG, "connect fail $address", e)
                false
            }
        }

    fun disconnect() {
        synchronized(lock) {
            open.set(false)
            runCatching { writer?.close() }
            runCatching { reader?.close() }
            runCatching { socket?.close() }
            writer = null
            reader = null
            socket = null
        }
    }

    fun isConnected(): Boolean = open.get() && socket?.isConnected == true

    private fun initElm(): Boolean {
        // Warm reset + quiet ASCII
        val steps =
            listOf(
                "ATZ" to 1500L,
                "ATE0" to 200L,
                "ATL0" to 200L,
                "ATS0" to 200L,
                "ATH0" to 200L,
                "ATSP0" to 400L,
            )
        for ((cmd, wait) in steps) {
            val r = sendCommand(cmd, wait)
            if (r == null) {
                lastError = "Sin respuesta a $cmd"
                return false
            }
        }
        return true
    }

    /** Send AT/OBD command and collect until prompt `>` or timeout. */
    fun sendCommand(cmd: String, readTimeoutMs: Long = 1500L): String? {
        if (!isConnected()) return null
        return try {
            synchronized(lock) {
                val w = writer ?: return null
                val r = reader ?: return null
                // Drain leftover
                while (r.ready()) r.read()
                w.write(cmd.trim() + "\r")
                w.flush()
                val sb = StringBuilder()
                val deadline = System.currentTimeMillis() + readTimeoutMs
                while (System.currentTimeMillis() < deadline) {
                    if (r.ready()) {
                        val ch = r.read()
                        if (ch < 0) break
                        val c = ch.toChar()
                        if (c == '>') break
                        sb.append(c)
                    } else {
                        Thread.sleep(15)
                    }
                }
                sb.toString()
            }
        } catch (e: Exception) {
            lastError = e.message
            Log.w(TAG, "sendCommand $cmd", e)
            open.set(false)
            null
        }
    }

    suspend fun pollPids(): ObdPidParser.PidValues =
        withContext(Dispatchers.IO) {
            var acc = ObdPidParser.PidValues()
            for (pid in POLL_PIDS) {
                val raw = sendCommand(pid, 1200L) ?: continue
                acc = ObdPidParser.merge(acc, ObdPidParser.parseMode01(raw))
                delay(40)
            }
            acc
        }

    /** Mode 01 status + Modes 03/07/0A trouble codes. */
    suspend fun pollDtc(): ObdDtc.Snapshot =
        withContext(Dispatchers.IO) {
            var mil = false
            var count = 0
            val statusRaw = sendCommand("0101", 1500L)
            if (statusRaw != null) {
                ObdDtc.parseMonitorStatus(statusRaw)?.let {
                    mil = it.first
                    count = it.second
                }
            }
            delay(40)
            val codes = mutableListOf<ObdDtc.Code>()
            for ((cmd, mode) in listOf("03" to 0x03, "07" to 0x07, "0A" to 0x0A)) {
                val raw = sendCommand(cmd, 2000L) ?: continue
                codes += ObdDtc.parseDtcResponse(raw, mode)
                delay(60)
            }
            val unique = codes.distinctBy { "${it.code}:${it.status}" }
            ObdDtc.Snapshot(
                mil = mil || unique.any { it.status == "stored" },
                dtcCount = if (count > 0) count else unique.size,
                codes = unique,
            )
        }

    /** Mode 04 clear DTCs (live ELM only). */
    fun clearDtcs(): Boolean {
        val raw = sendCommand("04", 2500L) ?: return false
        return raw.uppercase().contains("44") || raw.uppercase().contains("OK")
    }

    companion object {
        private const val TAG = "ObdBt"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val POLL_PIDS =
            listOf(
                "010D", // speed
                "010C", // rpm
                "0110", // mass air flow
                "010A", // fuel pressure
                "0133", // barometric
                "010E", // timing advance
                "014A", // O2 B1S1 voltage
                "0143", // absolute load
                "0145", // relative throttle
                "0149", // accel pedal D
                "014B", // O2 B1S2 voltage
                "014D", // EGR error
                "0144", // equiv ratio
                "014E", // evap purge
                "0152", // ethanol
                "0153", // evap vapor
                "0159", // fuel rail abs
                "014C", // commanded EGR
                "015A", // relative accel pedal
                "0161", // driver torque
                "0162", // actual torque
                "0170", // catalyst B2 temp
                "0171", // catalyst B1S2 temp
                "0172", // catalyst B2S2 temp
                "0173", // catalyst B1S3 temp
                "0174", // catalyst B2S3 temp
                "0175", // catalyst B1S4 temp
                "0176", // catalyst B2S4 temp
                "0155", // STFT sec O2 B1
                "0156", // LTFT sec O2 B1
                "0157", // STFT sec O2 B2
                "0158", // LTFT sec O2 B2
                "0177", // catalyst B1S5 temp
                "0178", // catalyst B2S5 temp
                "015D", // fuel injection timing
                "015B", // hybrid batt life
                "0163", // engine ref torque
                "0179", // catalyst B1S6 temp
                "017A", // catalyst B2S6 temp
                "0147", // throttle B
                "0148", // throttle C
                "0154", // time MIL on
                "017B", // catalyst B1S7 temp
                "017C", // catalyst B2S7 temp
                "0151", // fuel type
                "014F", // max equiv ratio
                "0150", // max MAF
                "017D", // catalyst B1S8 temp
                "017E", // catalyst B2S8 temp
                "0164", // max avail torque
                "0166", // MAF sensor IAT
                "0165", // aux input status
                "017F", // catalyst B1S9 temp
                "0180", // catalyst B2S9 temp
                "0167", // coolant ECT2
                "0168", // IAT sensor 2
                "016F", // turbo inlet pressure
                "0181", // catalyst B1S10 temp
                "0182", // catalyst B2S10 temp
                "016B", // EGR temperature
                "016A", // diesel intake air flow
                "016C", // throttle actuator
                "0183", // catalyst B1S11 temp
                "0184", // catalyst B2S11 temp
                "0169", // actual EGR
                "016E", // injection pressure control
                "016D", // fuel pressure control
                "0185", // catalyst B1S12 temp
                "0186", // catalyst B2S12 temp
                "0108", // STFT bank 2
                "0109", // LTFT bank 2
                "0187", // catalyst B1S13 temp
                "0188", // catalyst B2S13 temp
                "0189", // catalyst B1S14 temp
                "018A", // catalyst B2S14 temp
                "018C", // O2 lambda B1S1
                "018F", // PM sensor B1/B2
                "0198", // EGT B1S5
                "0199", // EGT B2S5
                "019C", // O2 lambda B1S3/B2S3
                "0190", // WWH-OBD continuous MI counter
                "0191", // WWH-OBD ECU B1 counter
                "0192", // fuel system control status
                "0193", // WWH-OBD cumulative MI counter
                "019A", // hybrid/EV battery voltage
                "01B2", // traction battery SOH
                "01B4", // HVESS temperature
                "01B5", // HVESS current
                "01B6", // HVESS pack voltage
                "01B7", // max cell temperature
                "01B8", // time since cell balancing
                "01B9", // min/max cell voltage
                "01BA", // power available / charge / discharge limit
                "01BB", // cumulative energy into HVESS
                "01BC", // cumulative energy from HVESS
                "01BD", // HVESS energy throughput
                "0194", // NOx reagent quality
                "019B", // DEF fluid level
                "01A1", // NOx corrected B1S1
                "01A5", // DEF dosing command
                "01A7", // NOx concentration S3/S4
                "01A8", // NOx corrected S3/S4
                "01A2", // cylinder fuel rate
                "01A3", // evap system vapor pressure
                "01A4", // transmission actual gear
                "01A6", // odometer
                "01A9", // ABS disable switch
                "01C5", // fuel pressure A/B
                "01C7", // distance since reflash
                "01C3", // fuel level input A/B
                "01C4", // EPCS diagnostic time/count
                "01C8", // NOx/PCD warning lamp
                "01C6", // particulate control inducement/counters
                "019D", // engine fuel rate g/s
                "019E", // engine exhaust flow kg/h
                "019F", // fuel system percentage use
                "018B", // DPF aftertreatment
                "018D", // throttle G
                "018E", // engine friction torque
                "0104", // calculated engine load
                "0106", // short-term fuel trim
                "0107", // long-term fuel trim
                "010B", // intake MAP
                "0105", // coolant
                "010F", // intake air temp
                "015C", // engine oil temp
                "012F", // fuel
                "015E", // fuel rate
                "0146", // ambient
                "0111", // throttle
                "011F", // run time since engine start
                "0121", // distance with MIL on
                "0131", // distance since codes cleared
                "0134", // catalyst temperature
                "0142", // control module voltage
            )
    }
}
