package com.veplayer.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetClient
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SOS dashcam clip — JPEG frame at panic time (sim bitmap today; CameraX hook later).
 */
object SosDashcam {
    data class Result(
        val clipUrl: String?,
        val bytes: Int,
        val sim: Boolean,
        val durationSec: Int,
    )

    /** Minimal branded JPEG for demo / no-camera benches. */
    fun renderSimFrame(
        prefs: VePrefs,
        width: Int = 640,
        height: Int = 360,
    ): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(18, 18, 22))
        val title =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(225, 29, 72)
                textSize = 42f
                isFakeBoldText = true
            }
        val body =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                textSize = 22f
            }
        val mute =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(148, 163, 184)
                textSize = 18f
            }
        val stamp =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        canvas.drawText("SOS · DASHCAM", 24f, 56f, title)
        canvas.drawText(prefs.deviceId().take(20), 24f, 100f, body)
        canvas.drawText(stamp, 24f, 132f, body)
        val driver = prefs.driverName.ifBlank { prefs.driverCode }.ifBlank { "—" }
        canvas.drawText("Conductor · $driver", 24f, 168f, body)
        canvas.drawText("Frame sim · ${prefs.sosClipSec}s buffer", 24f, 210f, mute)
        canvas.drawText("VePlayer ${com.veplayer.app.BuildConfig.VERSION_NAME}", 24f, height - 28f, mute)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
        bmp.recycle()
        return out.toByteArray()
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Capture + upload. Uses sim frame unless a real capture pipeline is wired.
     */
    fun captureAndUpload(
        context: Context,
        prefs: VePrefs,
        fleet: FleetClient,
        alertId: Long?,
    ): Result {
        if (!prefs.sosClipEnabled) {
            return Result(clipUrl = null, bytes = 0, sim = true, durationSec = prefs.sosClipSec)
        }
        val jpeg =
            if (prefs.sosClipSim) {
                renderSimFrame(prefs)
            } else {
                // Real CameraX still pending — fall back to sim so SOS never blocks.
                renderSimFrame(prefs)
            }
        val up =
            fleet
                .uploadPanicClip(
                    alertId = alertId,
                    jpegBytes = jpeg,
                    camera = "front_sim",
                    durationSec = prefs.sosClipSec,
                    sim = true,
                ).getOrElse {
                    return Result(clipUrl = null, bytes = jpeg.size, sim = true, durationSec = prefs.sosClipSec)
                }
        return Result(
            clipUrl = up.clipUrl,
            bytes = up.bytes,
            sim = up.sim,
            durationSec = prefs.sosClipSec,
        )
    }
}
