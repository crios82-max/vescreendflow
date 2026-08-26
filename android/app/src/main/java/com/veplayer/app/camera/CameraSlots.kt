package com.veplayer.app.camera

import android.hardware.camera2.CameraCharacteristics

/** Logical vehicle camera slots for 360 surround. */
enum class CamSlot(val label: String) {
    FRONT("Delantera"),
    REAR("Trasera"),
    LEFT("Izquierda"),
    RIGHT("Derecha"),
}

data class CamSlotAssignment(
    val front: CamDevice? = null,
    val rear: CamDevice? = null,
    val left: CamDevice? = null,
    val right: CamDevice? = null,
) {
    fun get(slot: CamSlot): CamDevice? =
        when (slot) {
            CamSlot.FRONT -> front
            CamSlot.REAR -> rear
            CamSlot.LEFT -> left
            CamSlot.RIGHT -> right
        }

    fun filled(): List<Pair<CamSlot, CamDevice>> =
        listOfNotNull(
            front?.let { CamSlot.FRONT to it },
            rear?.let { CamSlot.REAR to it },
            left?.let { CamSlot.LEFT to it },
            right?.let { CamSlot.RIGHT to it },
        )
}

object CameraSlots {
    fun assign(devices: List<CamDevice>): CamSlotAssignment {
        if (devices.isEmpty()) return CamSlotAssignment()
        val front =
            devices.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_FRONT }
        val rear =
            devices.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        val externals = devices.filter { it.isExternal }.toMutableList()
        // Prefer labeled USB as side cams; else leftover devices
        val left = externals.removeFirstOrNull()
            ?: devices.firstOrNull { it != front && it != rear }
        val right =
            externals.removeFirstOrNull()
                ?: devices.firstOrNull { it != front && it != rear && it != left }
        return CamSlotAssignment(
            front = front ?: devices.getOrNull(0),
            rear = rear ?: devices.getOrNull(1),
            left = left,
            right = right,
        )
    }
}
