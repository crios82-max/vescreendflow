package com.veplayer.app.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.veplayer.app.nav.TileKey
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private const val UA =
        "VePlayer/0.37 (vescreenflow.com; vehicle-kiosk; +https://vescreenflow.com)"

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

    /** Total bytes on disk under osm_tiles/. */
    fun cacheBytes(context: Context): Long {
        val root = cacheDir(context)
        if (!root.exists()) return 0L
        var sum = 0L
        root.walkTopDown().forEach { f ->
            if (f.isFile) sum += f.length()
        }
        return sum
    }

    fun cacheFileCount(context: Context): Int {
        val root = cacheDir(context)
        if (!root.exists()) return 0
        return root.walkTopDown().count { it.isFile && it.extension == "png" }
    }

    fun clearCache(context: Context): Long {
        val before = cacheBytes(context)
        synchronized(memoryLock) { memory.clear() }
        cacheDir(context).deleteRecursively()
        cacheDir(context)
        return before
    }

    fun isCached(
        context: Context,
        key: TileKey,
    ): Boolean {
        if (peek(key) != null) return true
        val file = File(cacheDir(context), "${key.z}/${key.x}/${key.y}.png")
        return file.exists() && file.length() > 64
    }

    suspend fun get(
        context: Context,
        key: TileKey,
        template: String = urlTemplate,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            peek(key)?.let { return@withContext it }
            val mutex = inflight.getOrPut(key) { Mutex() }
            try {
                mutex.withLock {
                    peek(key)?.let { return@withContext it }
                    val file = File(cacheDir(context), "${key.z}/${key.x}/${key.y}.png")
                    if (file.exists() && file.length() > 64) {
                        decode(file)?.let {
                            putMemory(key, it)
                            return@withContext it
                        }
                    }
                    downloadToDisk(context, key, template)?.let {
                        putMemory(key, it)
                        return@withContext it
                    }
                    null
                }
            } finally {
                inflight.remove(key)
            }
        }

    /**
     * Prefetch tiles to disk (skips already cached). Returns ok/fail/skip counts.
     * @param paceMs delay between network fetches (OSM usage policy).
     */
    suspend fun prefetch(
        context: Context,
        keys: List<TileKey>,
        template: String = urlTemplate,
        paceMs: Long = 80L,
        onProgress: (done: Int, total: Int, ok: Int) -> Unit = { _, _, _ -> },
    ): PrefetchResult =
        withContext(Dispatchers.IO) {
            var ok = 0
            var fail = 0
            var skip = 0
            val total = keys.size
            keys.forEachIndexed { i, key ->
                if (isCached(context, key)) {
                    skip++
                } else {
                    val bmp = downloadToDisk(context, key, template)
                    if (bmp != null) {
                        putMemory(key, bmp)
                        ok++
                        if (paceMs > 0) delay(paceMs)
                    } else {
                        fail++
                        if (paceMs > 0) delay(paceMs / 2)
                    }
                }
                onProgress(i + 1, total, ok)
            }
            PrefetchResult(total = total, downloaded = ok, skipped = skip, failed = fail)
        }

    private fun downloadToDisk(
        context: Context,
        key: TileKey,
        template: String,
    ): ImageBitmap? {
        val file = File(cacheDir(context), "${key.z}/${key.x}/${key.y}.png")
        val url =
            template
                .replace("{z}", key.z.toString())
                .replace("{x}", key.x.toString())
                .replace("{y}", key.y.toString())
        val req =
            Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "image/png,image/*;q=0.8,*/*;q=0.5")
                .build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.size < 64) return null
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
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

    data class PrefetchResult(
        val total: Int,
        val downloaded: Int,
        val skipped: Int,
        val failed: Int,
    )
}
