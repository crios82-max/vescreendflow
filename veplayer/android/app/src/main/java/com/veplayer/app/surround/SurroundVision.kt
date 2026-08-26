package com.veplayer.app.surround

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Front-camera object detection → surround actors (person / moto / car / …).
 * Maps image bbox to bird's-eye meters (heuristic, not calibrated Autopilot).
 */
class SurroundVision(
    private val context: Context,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var detector: ObjectDetector? = null
    private val busy = AtomicBoolean(false)
    private var bound = false

    fun start(lifecycleOwner: LifecycleOwner) {
        if (bound) return
        runCatching { ensureDetector() }
            .onFailure { Log.w(TAG, "detector init failed", it) }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    val provider = future.get()
                    val analysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                            .also { it.setAnalyzer(executor, ::analyze) }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        analysis,
                    )
                    bound = true
                    Log.i(TAG, "vision analysis bound (front camera)")
                } catch (e: Exception) {
                    Log.w(TAG, "bind vision failed — try back camera", e)
                    runCatching {
                        val provider = future.get()
                        val analysis =
                            ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                                .also { it.setAnalyzer(executor, ::analyze) }
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            analysis,
                        )
                        bound = true
                    }.onFailure { Log.e(TAG, "no camera for vision", it) }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun stop() {
        runCatching {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
        bound = false
        detector?.close()
        detector = null
    }

    private fun ensureDetector() {
        if (detector != null) return
        val options =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath("efficientdet_lite0.tflite")
                        .build(),
                )
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(10)
                .setScoreThreshold(0.35f)
                .build()
        detector = ObjectDetector.createFromOptions(context, options)
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        try {
            val det = detector ?: run {
                ensureDetector()
                detector
            } ?: return
            val bitmap = image.toBitmap()
            val mpImage: MPImage = BitmapImageBuilder(bitmap).build()
            val result: ObjectDetectorResult = det.detect(mpImage)
            val actors = result.detections().mapNotNull { d ->
                val cat = d.categories().firstOrNull() ?: return@mapNotNull null
                val kind = labelToKind(cat.categoryName()) ?: return@mapNotNull null
                val box = d.boundingBox()
                val w = bitmap.width.toFloat().coerceAtLeast(1f)
                val h = bitmap.height.toFloat().coerceAtLeast(1f)
                val cx = (box.centerX()) / w // 0..1
                val cy = (box.bottom) / h
                val area = (box.width() * box.height()) / (w * h)
                // Heuristic bird-eye: bottom of box → distance, centerX → lateral
                val yM = (8f + (1f - cy) * 35f).coerceIn(3f, 45f)
                val xM = ((cx - 0.5f) * 2f * 6f) // ±6 m
                SurroundActor(
                    id = "vis-${kind.name}-${box.left.toInt()}-${box.top.toInt()}",
                    kind = kind,
                    xM = xM,
                    yM = yM,
                    speedMps = 0f,
                    source = "vision",
                    confidence = cat.score(),
                ).takeIf { area > 0.002f }
            }
            SurroundEngine.publishVision(actors)
        } catch (e: Exception) {
            Log.w(TAG, "analyze fail", e)
        } finally {
            busy.set(false)
            image.close()
        }
    }

    private fun labelToKind(label: String): ActorKind? {
        val l = label.lowercase()
        return when {
            l.contains("person") -> ActorKind.PERSON
            l.contains("motorcycle") || l.contains("motorbike") -> ActorKind.MOTORCYCLE
            l.contains("bicycle") || l.contains("bike") -> ActorKind.BICYCLE
            l.contains("truck") -> ActorKind.TRUCK
            l.contains("bus") -> ActorKind.BUS
            l.contains("car") || l.contains("vehicle") || l.contains("van") -> ActorKind.CAR
            else -> null
        }
    }

    companion object {
        private const val TAG = "SurroundVision"
    }
}

/** ImageProxy RGBA → Bitmap helper for MediaPipe. */
private fun ImageProxy.toBitmap(): android.graphics.Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer
    buffer.rewind()
    val bmp =
        android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(buffer)
    // Rotate if needed
    val matrix = android.graphics.Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    return android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        .also { if (it !== bmp) bmp.recycle() }
}
