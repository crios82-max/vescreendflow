package com.veplayer.app.vehicle.can.dbc

import android.content.Context
import android.util.Log
import com.veplayer.app.data.VePrefs
import java.io.File

/**
 * Loads DBC from assets / files dir / raw text. Caches per source key.
 */
object DbcRepository {
    private const val TAG = "DbcRepo"
    const val ASSET_DEMO = "dbc/veplayer_demo.dbc"

    @Volatile
    private var cached: Pair<String, DbcDatabase>? = null

    fun invalidate() {
        cached = null
    }

    fun load(
        context: Context,
        prefs: VePrefs = VePrefs(context),
    ): DbcDatabase {
        val key = prefs.dbcSource
        cached?.let { if (it.first == key) return it.second }

        val db =
            runCatching {
                when {
                    key.startsWith("file:") -> {
                        val path = key.removePrefix("file:")
                        val f = File(path)
                        require(f.isFile) { "DBC no encontrado: $path" }
                        DbcParser.parse(f.readText(), sourceLabel = f.name)
                    }
                    key.startsWith("asset:") -> {
                        val asset = key.removePrefix("asset:")
                        readAsset(context, asset)
                    }
                    key == "builtin" || key.isBlank() -> readAsset(context, ASSET_DEMO)
                    else -> readAsset(context, key)
                }
            }.getOrElse { e ->
                Log.w(TAG, "DBC load fail ($key): ${e.message} — fallback demo")
                readAsset(context, ASSET_DEMO)
            }

        cached = key to db
        Log.i(TAG, "DBC loaded ${db.sourceLabel}: ${db.messageCount} msgs / ${db.signalCount} signals")
        return db
    }

    private fun readAsset(
        context: Context,
        assetPath: String,
    ): DbcDatabase {
        val text =
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return DbcParser.parse(text, sourceLabel = assetPath.substringAfterLast('/'))
    }

    /** Copy OEM DBC into app files for field use. */
    fun installCustom(
        context: Context,
        text: String,
        fileName: String = "custom.dbc",
    ): String {
        val dir = File(context.filesDir, "dbc").apply { mkdirs() }
        val f = File(dir, fileName)
        f.writeText(text)
        invalidate()
        return "file:${f.absolutePath}"
    }
}
