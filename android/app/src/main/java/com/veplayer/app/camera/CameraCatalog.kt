package com.veplayer.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

data class CamDevice(
    val id: String,
    val label: String,
    val facing: Int,
    val isExternal: Boolean,
)

object CameraCatalog {
    fun list(context: Context): List<CamDevice> {
        val cm = context.getSystemService(CameraManager::class.java) ?: return emptyList()
        return cm.cameraIdList.mapNotNull { id ->
            runCatching {
                val chars = cm.getCameraCharacteristics(id)
                val facing =
                    chars.get(CameraCharacteristics.LENS_FACING)
                        ?: CameraCharacteristics.LENS_FACING_BACK
                val external =
                    Build.VERSION.SDK_INT >= 28 &&
                        facing == CameraCharacteristics.LENS_FACING_EXTERNAL
                val label =
                    when {
                        external -> "USB / externa ($id)"
                        facing == CameraCharacteristics.LENS_FACING_FRONT ->
                            "Delantera / retrovisor ($id)"
                        facing == CameraCharacteristics.LENS_FACING_BACK -> "Trasera ($id)"
                        else -> "Cámara $id"
                    }
                CamDevice(id = id, label = label, facing = facing, isExternal = external)
            }.getOrNull()
        }
    }

    /** Prefer front+back, else first two, else duplicates handled by UI. */
    fun pickDual(devices: List<CamDevice>): Pair<CamDevice?, CamDevice?> {
        if (devices.isEmpty()) return null to null
        val front =
            devices.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_FRONT }
        val back =
            devices.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        val external = devices.filter { it.isExternal }
        return when {
            front != null && back != null -> front to back
            external.size >= 2 -> external[0] to external[1]
            front != null && external.isNotEmpty() -> front to external[0]
            back != null && external.isNotEmpty() -> back to external[0]
            devices.size >= 2 -> devices[0] to devices[1]
            else -> devices[0] to null
        }
    }
}
