package com.veplayer.app.vehicle.can

import android.content.Context
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android Automotive [android.car.hardware.property.CarPropertyManager] via reflection
 * so the phone/tablet APK still compiles without the automotive SDK.
 *
 * Maps a few VehiclePropertyIds into VePlayer demo [CanFrame]s for the shared decoder.
 * On non-AAOS devices [open] returns false.
 */
class CarPropertyTransport(
    private val context: Context,
) : CanTransport {
    override val name: String = "can_car"

    private val openFlag = AtomicBoolean(false)
    private val rxQueue = LinkedBlockingQueue<CanFrame>(64)
    private var carObj: Any? = null
    private var propMgr: Any? = null
    private var callbackProxy: Any? = null

    @Volatile var lastError: String? = null
        private set

    override fun open(): Boolean {
        close()
        lastError = null
        return try {
            val carClass = Class.forName("android.car.Car")
            val createMethod =
                carClass.methods.first {
                    it.name == "createCar" && it.parameterTypes.size == 1
                }
            val car = createMethod.invoke(null, context) ?: error("createCar null")
            carClass.getMethod("connect").invoke(car)
            val getMgr =
                carClass.methods.first {
                    it.name == "getCarManager" && it.parameterTypes.size == 1
                }
            // Car.PROPERTY_SERVICE == "property"
            val mgr = getMgr.invoke(car, "property")
            if (mgr == null) {
                lastError = "CarPropertyManager no disponible"
                runCatching { carClass.getMethod("disconnect").invoke(car) }
                return false
            }
            carObj = car
            propMgr = mgr
            registerSpeedListener(mgr)
            // Seed initial snapshot
            pollOnce(mgr)
            openFlag.set(true)
            Log.i(TAG, "CarProperty transport open")
            true
        } catch (e: ClassNotFoundException) {
            lastError = "No AAOS (android.car ausente)"
            false
        } catch (e: Exception) {
            lastError = e.message ?: "CarProperty fail"
            Log.w(TAG, "open fail", e)
            false
        }
    }

    override fun close() {
        openFlag.set(false)
        runCatching {
            val car = carObj ?: return@runCatching
            car.javaClass.getMethod("disconnect").invoke(car)
        }
        carObj = null
        propMgr = null
        callbackProxy = null
        rxQueue.clear()
    }

    override fun isOpen(): Boolean = openFlag.get()

    override fun readFrame(timeoutMs: Long): CanFrame? {
        val f = rxQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
        if (f != null) return f
        // Soft poll if no callback fired
        propMgr?.let { pollOnce(it) }
        return rxQueue.poll()
    }

    private fun pollOnce(mgr: Any) {
        // PERF_VEHICLE_SPEED = 291504647 (float m/s) — use reflection lookup by name if possible
        val speed = readFloatProperty(mgr, "PERF_VEHICLE_SPEED") ?: readFloatProperty(mgr, 0x11600207)
        if (speed != null) {
            val kmh = (speed * 3.6f).toInt().coerceIn(0, 250)
            rxQueue.offer(CanFrame.classic(CanSignalDecoder.ID_SPEED, kmh))
        }
        val gear = readIntProperty(mgr, "GEAR_SELECTION") ?: readIntProperty(mgr, 0x11400400)
        if (gear != null) {
            // CarGear: 0=N unknowns vary; map common AAOS values loosely
            val g =
                when (gear) {
                    1 -> 1 // R often
                    2 -> 0 // P
                    4 -> 2 // N
                    8 -> 3 // D
                    else -> if (gear < 0) 1 else 3
                }
            rxQueue.offer(CanFrame.classic(CanSignalDecoder.ID_GEAR, g))
        }
        val ign = readIntProperty(mgr, "IGNITION_STATE")
        if (ign != null) {
            val mapped =
                when (ign) {
                    1 -> 1
                    2 -> 2
                    3 -> 3
                    else -> 0
                }
            rxQueue.offer(CanFrame.classic(CanSignalDecoder.ID_FLAGS, 0, mapped))
        }
    }

    private fun registerSpeedListener(mgr: Any) {
        // Best-effort; many images restrict property permissions
        runCatching {
            Log.i(TAG, "CarProperty mgr=${mgr.javaClass.name}")
        }
    }

    private fun readFloatProperty(
        mgr: Any,
        idOrName: Any,
    ): Float? =
        runCatching {
            val id = resolvePropId(idOrName) ?: return null
            val m =
                mgr.javaClass.methods.firstOrNull {
                    it.name == "getFloatProperty" && it.parameterTypes.size == 2
                } ?: return null
            (m.invoke(mgr, id, 0) as? Float)
        }.getOrNull()

    private fun readIntProperty(
        mgr: Any,
        idOrName: Any,
    ): Int? =
        runCatching {
            val id = resolvePropId(idOrName) ?: return null
            val m =
                mgr.javaClass.methods.firstOrNull {
                    it.name == "getIntProperty" && it.parameterTypes.size == 2
                } ?: return null
            (m.invoke(mgr, id, 0) as? Int)
        }.getOrNull()

    private fun resolvePropId(idOrName: Any): Int? {
        if (idOrName is Int) return idOrName
        if (idOrName is String) {
            return runCatching {
                val ids = Class.forName("android.car.VehiclePropertyIds")
                ids.getField(idOrName).getInt(null)
            }.getOrNull()
        }
        return null
    }

    companion object {
        private const val TAG = "CarPropertyCan"
    }
}
