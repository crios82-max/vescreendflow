package com.veplayer.app.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.veplayer.app.nav.TileKey
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Disk + memory cache for OSM (or compatible) raster tiles.
 * Uses a polite User-Agent — required by tile.openstreetmap.org.
 */
object OsmTileStore {
    private const val DEFAULT_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    private const val MAX_MEMORY = 96

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()

    private val memory = LinkedHashMap<TileKey, ImageBitmap>(MAX_MEMORY, 0.75f, true)
    private val memoryLock = Any()
    private val inflight = ConcurrentHashMap<TileKey, Mutex>()

    var urlTemplate: String = DEFAULT_TEMPLATE

    fun cacheDir(context: Context): File = File(context.cacheDir, "osm_tiles").also { it.mkdirs() }

    fun peek(key: TileKey): ImageBitmap? =
        synchronized(memoryLock) {
            memory[key]
        }

    suspend fun get(
        context: Context,
        key: TileKey,
        template: String = urlTemplate,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            peek(key)?.let { return@withContext it }
            val mutex = inflight.getOrPut(key) { Mutex() }
            mutex.withLock {
                peek(key)?.let { return@withContext it }
                val file = File(cacheDir(context), "${key.z}/${key.x}/${key.y}.png")
                if (file.exists() && file.length() > 64) {
                    decode(file)?.let {
                        putMemory(key, it)
                        return@withContext it
                    }
                }
                val url =
                    template
                        .replace("{z}", key.z.toString())
                        .replace("{x}", key.x.toString())
                        .replace("{y}", key.y.toString())
                val req =
                    Request.Builder()
                        .url(url)
                        .header(
                            "User-Agent",
                            "VePlayer/0.19 (vescreenflow.com; vehicle-kiosk; +https://vescreenflow.com)",
                        )
                        .header("Accept", "image/png,image/*;q=0.8,*/*;q=0.5")
                        .build()
                try {
                    http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withContext null
                        val bytes = resp.body?.bytes() ?: return@withContext null
                        if (bytes.size < 64) return@withContext null
                        file.parentFile?.mkdirs()
                        file.writeBytes(bytes)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        if (bmp != null) putMemory(key, bmp)
                        bmp
                    }
                } catch (_: Exception) {
                    null
                } finally {
                    inflight.remove(key)
                }
            }
        }

    private fun decode(file: File): ImageBitmap? =
        try {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }

    private fun putMemory(
        key: TileKey,
        bmp: ImageBitmap,
    ) {
        synchronized(memoryLock) {
            memory[key] = bmp
            while (memory.size > MAX_MEMORY) {
                val it = memory.entries.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                } else {
                    break
                }
            }
        }
    }
}
