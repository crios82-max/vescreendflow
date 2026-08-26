package com.veplayer.app.vehicle.can

import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SocketCAN bridge (`can0`).
 *
 * Drop in `libveplayer_can.so` with JNI symbols:
 * - `Java_…_SocketCanTransport_nativeOpen`
 * - `nativeClose` / `nativeRead` / `nativeWrite`
 *
 * Without the .so, [open] returns false and [RealCanAdapter] falls through.
 * [inject] remains for instrumented tests.
 */
class SocketCanTransport(
    private val iface: String = "can0",
) : CanTransport {
    override val name: String = "can_socket"

    private val openFlag = AtomicBoolean(false)
    private val rxQueue = LinkedBlockingQueue<CanFrame>(256)
    private var native: NativeBridge? = null

    @Volatile var lastError: String? = null
        private set

    override fun open(): Boolean {
        close()
        lastError = null
        val bridge =
            runCatching {
                System.loadLibrary("veplayer_can")
                NativeBridge()
            }.getOrElse {
                lastError = "libveplayer_can.so ausente — usá USB/Car/sim"
                Log.w(TAG, lastError!!)
                return false
            }
        val rc = runCatching { bridge.open(iface) }.getOrDefault(-1)
        if (rc != 0) {
            lastError = "nativeOpen($iface)=$rc"
            return false
        }
        native = bridge
        openFlag.set(true)
        Thread({
            while (openFlag.get()) {
                val frame = runCatching { bridge.read(200) }.getOrNull()
                if (frame != null) rxQueue.offer(frame)
            }
        }, "socketcan-rx").also {
            it.isDaemon = true
            it.start()
        }
        return true
    }

    override fun close() {
        openFlag.set(false)
        runCatching { native?.close() }
        native = null
        rxQueue.clear()
    }

    override fun isOpen(): Boolean = openFlag.get()

    override fun readFrame(timeoutMs: Long): CanFrame? =
        rxQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)

    override fun writeFrame(frame: CanFrame): Boolean {
        val n = native ?: return false
        return runCatching { n.write(frame.id, frame.data, frame.extended) == 0 }.getOrDefault(false)
    }

    fun inject(frame: CanFrame) {
        rxQueue.offer(frame)
    }

    /**
     * JNI façade — only constructed after [System.loadLibrary] succeeds.
     * Implement in native/veplayer_can.
     */
    private class NativeBridge {
        external fun open(iface: String): Int

        external fun close()

        external fun read(timeoutMs: Int): CanFrame?

        external fun write(
            id: Int,
            data: ByteArray,
            extended: Boolean,
        ): Int
    }

    companion object {
        private const val TAG = "SocketCan"
    }
}
