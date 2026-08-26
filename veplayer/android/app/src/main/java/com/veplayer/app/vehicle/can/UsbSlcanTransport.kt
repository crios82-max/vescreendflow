package com.veplayer.app.vehicle.can

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB host SLCAN (Lawicel) ASCII protocol.
 * Works with many USB-CAN adapters that speak `tIIILDD…\r` / `TIIIIIIIILDD…\r`.
 *
 * If no compatible device / permission → [open] returns false (caller falls back).
 */
class UsbSlcanTransport(
    private val context: Context,
) : CanTransport {
    override val name: String = "can_usb"

    private val usb = context.getSystemService(UsbManager::class.java)
    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var usbIface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private val openFlag = AtomicBoolean(false)
    private val rxQueue = LinkedBlockingQueue<CanFrame>(256)
    private var readerThread: Thread? = null
    private val asciiBuf = StringBuilder()

    @Volatile var lastError: String? = null
        private set

    @Volatile var deviceLabel: String? = null
        private set

    fun listCandidates(): List<String> {
        val mgr = usb ?: return emptyList()
        return mgr.deviceList.values.map { d ->
            "%04X:%04X %s".format(d.vendorId, d.productId, d.deviceName)
        }
    }

    override fun open(): Boolean {
        close()
        lastError = null
        val mgr = usb
        if (mgr == null) {
            lastError = "UsbManager null"
            return false
        }
        val candidates = mgr.deviceList.values.toList()
        if (candidates.isEmpty()) {
            lastError = "Sin dispositivos USB"
            return false
        }
        // Prefer known CAN adapter VIDs; else first device with bulk endpoints
        val preferredVids = setOf(0x1D50, 0x16D0, 0x0483, 0x0403, 0x067B, 0x1CBE) // openmoko/candle, peak-ish, ST, FTDI, Prolific
        val chosen =
            candidates.firstOrNull { it.vendorId in preferredVids }
                ?: candidates.firstOrNull { hasBulkPair(it) }
                ?: candidates.first()

        if (!mgr.hasPermission(chosen)) {
            requestPermission(chosen)
            lastError = "Esperando permiso USB para ${chosen.deviceName}"
            // Try once more after short wait if user already granted
            Thread.sleep(300)
            if (!mgr.hasPermission(chosen)) return false
        }

        val conn = mgr.openDevice(chosen)
        if (conn == null) {
            lastError = "openDevice falló"
            return false
        }
        val pair = findBulkPair(chosen) ?: run {
            conn.close()
            lastError = "Sin endpoints bulk"
            return false
        }
        if (!conn.claimInterface(pair.first, true)) {
            conn.close()
            lastError = "claimInterface falló"
            return false
        }
        device = chosen
        connection = conn
        usbIface = pair.first
        epIn = pair.second
        epOut = pair.third
        deviceLabel = "%04X:%04X".format(chosen.vendorId, chosen.productId)
        openFlag.set(true)

        // SLCAN open channel @ 500k (common vehicle bitrate)
        sendAscii("C\r")
        sendAscii("S6\r") // 500 kbit
        sendAscii("O\r")

        readerThread =
            Thread({
                val buf = ByteArray(64)
                while (openFlag.get()) {
                    val n =
                        try {
                            conn.bulkTransfer(epIn, buf, buf.size, 200)
                        } catch (_: Exception) {
                            -1
                        }
                    if (n != null && n > 0) {
                        ingestAscii(String(buf, 0, n, Charsets.US_ASCII))
                    }
                }
            }, "slcan-rx").also { it.isDaemon = true; it.start() }

        Log.i(TAG, "USB SLCAN open $deviceLabel")
        return true
    }

    override fun close() {
        openFlag.set(false)
        readerThread?.interrupt()
        readerThread = null
        runCatching {
            sendAscii("C\r")
            val c = connection
            val i = usbIface
            if (c != null && i != null) c.releaseInterface(i)
            c?.close()
        }
        connection = null
        usbIface = null
        epIn = null
        epOut = null
        device = null
        rxQueue.clear()
        asciiBuf.setLength(0)
    }

    override fun isOpen(): Boolean = openFlag.get()

    override fun readFrame(timeoutMs: Long): CanFrame? =
        rxQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)

    override fun writeFrame(frame: CanFrame): Boolean {
        val line =
            if (frame.extended) {
                "T%08X%X%s\r".format(frame.id, frame.data.size, frame.dataHex())
            } else {
                "t%03X%X%s\r".format(frame.id and 0x7FF, frame.data.size, frame.dataHex())
            }
        return sendAscii(line)
    }

    private fun sendAscii(s: String): Boolean {
        val conn = connection ?: return false
        val out = epOut ?: return false
        val bytes = s.toByteArray(Charsets.US_ASCII)
        val n = conn.bulkTransfer(out, bytes, bytes.size, 500)
        return n == bytes.size
    }

    private fun ingestAscii(chunk: String) {
        asciiBuf.append(chunk)
        while (true) {
            val cr = asciiBuf.indexOf("\r")
            if (cr < 0) break
            val line = asciiBuf.substring(0, cr).trim()
            asciiBuf.delete(0, cr + 1)
            parseSlcanLine(line)?.let { rxQueue.offer(it) }
        }
        if (asciiBuf.length > 512) asciiBuf.setLength(0)
    }

    private fun requestPermission(device: UsbDevice) {
        val mgr = usb ?: return
        val flags =
            if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        val pi =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags,
            )
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(usbReceiver, filter)
            }
        }
        mgr.requestPermission(device, pi)
    }

    private val usbReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                ctx: Context,
                intent: Intent,
            ) {
                if (intent.action != ACTION_USB_PERMISSION) return
                runCatching { context.unregisterReceiver(this) }
            }
        }

    companion object {
        private const val TAG = "UsbSlcan"
        const val ACTION_USB_PERMISSION = "com.veplayer.app.USB_CAN_PERMISSION"

        fun parseSlcanLine(line: String): CanFrame? {
            if (line.isEmpty()) return null
            return try {
                when (line[0]) {
                    't' -> {
                        // tIIILDD..
                        if (line.length < 5) return null
                        val id = line.substring(1, 4).toInt(16)
                        val len = line.substring(4, 5).toInt(16).coerceIn(0, 8)
                        val hex = line.substring(5)
                        val data = hexToBytes(hex, len)
                        CanFrame(id = id, data = data, extended = false)
                    }
                    'T' -> {
                        if (line.length < 10) return null
                        val id = line.substring(1, 9).toInt(16)
                        val len = line.substring(9, 10).toInt(16).coerceIn(0, 8)
                        val hex = line.substring(10)
                        val data = hexToBytes(hex, len)
                        CanFrame(id = id, data = data, extended = true)
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun hexToBytes(
            hex: String,
            len: Int,
        ): ByteArray {
            val out = ByteArray(len)
            var i = 0
            while (i < len && (i * 2 + 1) < hex.length) {
                out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                i++
            }
            return out
        }

        private fun hasBulkPair(device: UsbDevice): Boolean = findBulkPair(device) != null

        private fun findBulkPair(device: UsbDevice): Triple<UsbInterface, UsbEndpoint, UsbEndpoint>? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                var inn: UsbEndpoint? = null
                var out: UsbEndpoint? = null
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                    if (ep.direction == UsbConstants.USB_DIR_IN) inn = ep
                    if (ep.direction == UsbConstants.USB_DIR_OUT) out = ep
                }
                if (inn != null && out != null) return Triple(iface, inn, out)
            }
            return null
        }
    }
}
