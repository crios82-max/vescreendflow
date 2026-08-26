package com.senseflow.app.data

import android.content.Context
import com.senseflow.app.BuildConfig
import java.security.MessageDigest
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("senseflow", Context.MODE_PRIVATE)

    var apiBaseUrl: String
        get() = sp.getString("api_base", BuildConfig.API_BASE_URL) ?: BuildConfig.API_BASE_URL
        set(value) = sp.edit().putString("api_base", value).apply()

    var sharingEnabled: Boolean
        get() = sp.getBoolean("sharing", false)
        set(value) = sp.edit().putBoolean("sharing", value).apply()

    /** Daily rotating anonymous bucket — not a stable user id. */
    fun deviceBucket(): String {
        val day = java.time.LocalDate.now().toString()
        var installId = sp.getString("install_id", null)
        if (installId.isNullOrBlank()) {
            installId = UUID.randomUUID().toString()
            sp.edit().putString("install_id", installId).apply()
        }
        val raw = "$installId|$day"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
