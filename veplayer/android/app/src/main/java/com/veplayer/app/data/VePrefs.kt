package com.veplayer.app.data

import android.content.Context
import com.veplayer.app.BuildConfig
import java.security.MessageDigest
import java.util.UUID

class VePrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("veplayer", Context.MODE_PRIVATE)

    var pin: String
        get() = sp.getString("pin", DEFAULT_PIN) ?: DEFAULT_PIN
        set(value) = sp.edit().putString("pin", value).apply()

    var senseflowUrl: String
        get() = sp.getString("senseflow_url", BuildConfig.SENSEFLOW_URL) ?: BuildConfig.SENSEFLOW_URL
        set(value) = sp.edit().putString("senseflow_url", value.trim().trimEnd('/')).apply()

    var playerUrl: String
        get() = sp.getString("player_url", BuildConfig.PLAYER_URL) ?: BuildConfig.PLAYER_URL
        set(value) = sp.edit().putString("player_url", value.trim()).apply()

    var videoSpeedBlockKmh: Float
        get() = sp.getFloat("video_block_kmh", 8f)
        set(value) = sp.edit().putFloat("video_block_kmh", value).apply()

    var mockReverse: Boolean
        get() = sp.getBoolean("mock_reverse", false)
        set(value) = sp.edit().putBoolean("mock_reverse", value).apply()

    var mockSpeedKmh: Float
        get() = sp.getFloat("mock_speed_kmh", 0f)
        set(value) = sp.edit().putFloat("mock_speed_kmh", value).apply()

    /** gps | mock | can | obd — see [com.veplayer.app.vehicle.SignalSourceKind]. */
    var signalSource: String
        get() = sp.getString("signal_source", "gps") ?: "gps"
        set(value) = sp.edit().putString("signal_source", value.trim().lowercase()).apply()

    /** Bluetooth MAC for ELM327 (optional; simulator used if blank / unlinkable). */
    var obdDeviceAddress: String
        get() = sp.getString("obd_device_address", "") ?: ""
        set(value) = sp.edit().putString("obd_device_address", value.trim()).apply()

    var deviceName: String
        get() = sp.getString("device_name", "VePlayer") ?: "VePlayer"
        set(value) = sp.edit().putString("device_name", value).apply()

    fun deviceId(): String {
        var id = sp.getString("device_id", null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "").take(32)
            sp.edit().putString("device_id", id).apply()
        }
        return id
    }

    fun pairCodeCached(): String? = sp.getString("pair_code", null)

    fun setPairCode(code: String) = sp.edit().putString("pair_code", code).apply()

    fun dailyBucket(): String {
        val day = java.time.LocalDate.now().toString()
        val raw = "${deviceId()}|$day"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    fun checkPin(input: String): Boolean = input == pin

    companion object {
        const val DEFAULT_PIN = "1234"
    }
}
