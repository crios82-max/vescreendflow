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
                "0104", // calculated engine load
                "0105", // coolant
                "010F", // intake air temp
                "015C", // engine oil temp
                "012F", // fuel
                "015E", // fuel rate
                "0146", // ambient
                "0111", // throttle
                "011F", // run time since engine start
                "0121", // distance with MIL on
                "0142", // control module voltage
            )
    }
}
