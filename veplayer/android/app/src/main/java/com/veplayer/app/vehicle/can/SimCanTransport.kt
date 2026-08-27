package com.veplayer.app.vehicle.can

import kotlin.math.sin
import kotlin.random.Random

/**
 * Emits VePlayer demo CAN frames (0x100–0x108) when no adapter is present.
 * Exercises the same decoder path as USB/Car/Socket.
 */
class SimCanTransport : CanTransport {
    override val name: String = "can_sim"
    private var open = false
    private var t = 0.0
    private var cursor = 0
    private val queue = ArrayDeque<CanFrame>()

    override fun open(): Boolean {
        open = true
        return true
    }

    override fun close() {
        open = false
        queue.clear()
    }

    override fun isOpen(): Boolean = open

    override fun readFrame(timeoutMs: Long): CanFrame? {
        if (!open) return null
        if (queue.isEmpty()) refill()
        return queue.removeFirstOrNull()
    }

    private fun refill() {
        t += 0.25
        val kmh = (35.0 + 17.0 * sin(t / 8.0)).toInt().coerceIn(0, 90)
        val gear = if (kmh < 1) 0 else 3
        val phase = ((t / 12.0) % 4).toInt()
        val turn =
            when (phase) {
                1 -> 1
                3 -> 2
                else -> 0
            }
        val steer = (sin(t / 5.0) * 60).toInt() // ×0.1° later
        val rpm = if (gear == 3) 1400 + kmh * 28 else 0
        val soc = (72 + 4 * sin(t / 40.0)).toInt().coerceIn(5, 100)
        val abs = if (phase == 2 && kmh > 40) 1 else 0

        queue.add(CanFrame.classic(CanSignalDecoder.ID_SPEED, kmh))
        queue.add(CanFrame.classic(CanSignalDecoder.ID_GEAR, gear))
        queue.add(CanFrame.classic(CanSignalDecoder.ID_TURN, turn))
        queue.add(CanFrame.classic(CanSignalDecoder.ID_DOORS, 0))
        queue.add(CanFrame.classic(CanSignalDecoder.ID_ENERGY, soc, 0))
        queue.add(
            CanFrame.classic(
                CanSignalDecoder.ID_DYNAMICS,
                steer and 0xFF,
                (steer shr 8) and 0xFF,
                rpm and 0xFF,
                (rpm shr 8) and 0xFF,
            ),
        )
        queue.add(CanFrame.classic(CanSignalDecoder.ID_FLAGS, abs, 2))
        queue.add(
            CanFrame.classic(
                CanSignalDecoder.ID_TPMS,
                32 + Random.nextInt(2),
                32,
                33,
                32,
            ),
        )
        queue.add(
            CanFrame.classic(
                CanSignalDecoder.ID_HVAC,
                24,
                22,
                2,
                1,
                28,
            ),
        )
        cursor++
    }
}
