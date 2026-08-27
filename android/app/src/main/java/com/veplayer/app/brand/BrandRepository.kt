package com.veplayer.app.brand

import android.content.Context
import android.util.Log
import com.veplayer.app.data.VePrefs
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Base64

/**
 * OEM brand assets — download/cache logo like fleet DBC.
 */
object BrandRepository {
    private const val TAG = "BrandRepo"
    private val http = OkHttpClient()

    fun apply(
        context: Context,
        prefs: VePrefs,
        brandId: String = "",
        name: String = "",
        logoUrl: String = "",
        logoBase64: String = "",
        accent: String = "",
    ): String {
        val id = brandId.trim().ifBlank { name.trim().lowercase().replace(' ', '-') }
        if (id.isNotBlank()) prefs.brandId = id
        if (name.isNotBlank()) prefs.brandName = name
        if (accent.isNotBlank()) prefs.brandAccentArgb = parseAccent(accent)

        when {
            logoBase64.isNotBlank() -> {
                val bytes = Base64.decode(logoBase64.trim(), Base64.DEFAULT)
                val path = writeLogoBytes(context, id.ifBlank { "fleet" }, bytes)
                prefs.brandLogoPath = path
            }
            logoUrl.isNotBlank() -> {
                val path = downloadLogo(context, logoUrl.trim(), id.ifBlank { "fleet" })
                prefs.brandLogoPath = path
            }
        }
        val label = prefs.brandName.ifBlank { prefs.brandId.ifBlank { "marca" } }
        Log.i(TAG, "brand applied · $label · logo=${prefs.brandLogoPath}")
        return label
    }

    fun clear(context: Context, prefs: VePrefs) {
        prefs.brandId = ""
        prefs.brandName = ""
        prefs.brandAccentArgb = DEFAULT_ACCENT
        val old = prefs.brandLogoPath
        prefs.brandLogoPath = ""
        if (old.isNotBlank()) {
            runCatching { File(old).delete() }
        }
        Log.i(TAG, "brand cleared")
    }

    fun logoFile(prefs: VePrefs): File? {
        val p = prefs.brandLogoPath
        if (p.isBlank()) return null
        val f = File(p)
        return f.takeIf { it.isFile }
    }

    fun toJsonMap(prefs: VePrefs): Map<String, Any?> =
        mapOf(
            "brand_id" to prefs.brandId.ifBlank { null },
            "name" to prefs.brandName.ifBlank { null },
            "accent" to prefs.brandAccentArgb,
            "has_logo" to logoFile(prefs) != null,
        )

    private fun downloadLogo(context: Context, url: String, key: String): String {
        val req = Request.Builder().url(url).build()
        val bytes =
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("logo HTTP ${resp.code}")
                resp.body?.bytes() ?: error("logo vacío")
            }
        return writeLogoBytes(context, key, bytes)
    }

    private fun writeLogoBytes(context: Context, key: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "brand").apply { mkdirs() }
        val safe = key.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "logo" }
        val f = File(dir, "$safe.png")
        f.writeBytes(bytes)
        return f.absolutePath
    }

    private fun parseAccent(raw: String): Long {
        val s = raw.trim().removePrefix("#")
        if (s.length != 6 && s.length != 8) return DEFAULT_ACCENT
        val argb =
            when (s.length) {
                6 -> 0xFF000000 or s.toLong(16)
                else -> s.toLong(16)
            }
        return argb and 0xFFFFFFFFL
    }

    private const val DEFAULT_ACCENT = 0xFF2DD4BFL
}
