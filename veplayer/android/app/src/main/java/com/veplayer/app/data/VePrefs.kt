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

    /** auto | car | usb | socket | sim — see [com.veplayer.app.vehicle.can.CanBackend]. */
    var canBackend: String
        get() = sp.getString("can_backend", "auto") ?: "auto"
        set(value) = sp.edit().putString("can_backend", value.trim().lowercase()).apply()

    var canSocketIface: String
        get() = sp.getString("can_socket_iface", "can0") ?: "can0"
        set(value) = sp.edit().putString("can_socket_iface", value.trim().ifBlank { "can0" }).apply()

    /**
     * DBC source key:
     * - `builtin` / blank → assets/dbc/veplayer_demo.dbc
     * - `asset:…` → assets path
     * - `file:/…` → absolute path (field OEM file)
     */
    var dbcSource: String
        get() = sp.getString("dbc_source", "builtin") ?: "builtin"
        set(value) = sp.edit().putString("dbc_source", value.trim()).apply()

    /** stream | fm — default band on Radio screen. */
    var radioMode: String
        get() = sp.getString("radio_mode", "stream") ?: "stream"
        set(value) = sp.edit().putString("radio_mode", value.trim().lowercase()).apply()

    /** auto | hal | sim */
    var fmBackend: String
        get() = sp.getString("fm_backend", "auto") ?: "auto"
        set(value) = sp.edit().putString("fm_backend", value.trim().lowercase()).apply()

    /** itu2 | itu1 */
    var fmRegion: String
        get() = sp.getString("fm_region", "itu2") ?: "itu2"
        set(value) = sp.edit().putString("fm_region", value.trim().lowercase()).apply()

    var fmLastFreqKhz: Int
        get() = sp.getInt("fm_last_freq_khz", 95_500)
        set(value) = sp.edit().putInt("fm_last_freq_khz", value).apply()

    var birdEyeMaxAheadM: Float
        get() = sp.getFloat("bird_eye_max_ahead_m", 50f)
        set(value) = sp.edit().putFloat("bird_eye_max_ahead_m", value.coerceIn(15f, 80f)).apply()

    var birdEyeMaxLatM: Float
        get() = sp.getFloat("bird_eye_max_lat_m", 18f)
        set(value) = sp.edit().putFloat("bird_eye_max_lat_m", value.coerceIn(6f, 30f)).apply()

    var navEnabled: Boolean
        get() = sp.getBoolean("nav_enabled", true)
        set(value) = sp.edit().putBoolean("nav_enabled", value).apply()

    var navFromLat: Double
        get() = Double.fromBits(sp.getLong("nav_from_lat", java.lang.Double.doubleToRawLongBits(10.496)))
        set(value) = sp.edit().putLong("nav_from_lat", value.toRawBits()).apply()

    var navFromLng: Double
        get() = Double.fromBits(sp.getLong("nav_from_lng", java.lang.Double.doubleToRawLongBits(-66.898)))
        set(value) = sp.edit().putLong("nav_from_lng", value.toRawBits()).apply()

    var navToLat: Double
        get() = Double.fromBits(sp.getLong("nav_to_lat", java.lang.Double.doubleToRawLongBits(10.4965)))
        set(value) = sp.edit().putLong("nav_to_lat", value.toRawBits()).apply()

    var navToLng: Double
        get() = Double.fromBits(sp.getLong("nav_to_lng", java.lang.Double.doubleToRawLongBits(-66.8492)))
        set(value) = sp.edit().putLong("nav_to_lng", value.toRawBits()).apply()

    var navDestName: String
        get() = sp.getString("nav_dest_name", "Altamira") ?: "Altamira"
        set(value) = sp.edit().putString("nav_dest_name", value.trim()).apply()

    /** native | web — cockpit map renderer. */
    var mapMode: String
        get() = sp.getString("map_mode", "native") ?: "native"
        set(value) = sp.edit().putString("map_mode", value.trim().lowercase()).apply()

    /** OSM (or compatible) raster tiles under native Compose map. */
    var mapTilesEnabled: Boolean
        get() = sp.getBoolean("map_tiles", true)
        set(value) = sp.edit().putBoolean("map_tiles", value).apply()

    /** Tile URL template with {z}/{x}/{y}. Default: OSM. */
    var mapTileUrl: String
        get() =
            sp.getString("map_tile_url", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")
                ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        set(value) = sp.edit().putString("map_tile_url", value.trim()).apply()

    var deviceName: String
        get() = sp.getString("device_name", "VePlayer") ?: "VePlayer"
        set(value) = sp.edit().putString("device_name", value).apply()

    /** Auto-apply fleet OTA when heartbeat reports update_available (Device Owner = silent). */
    var autoOtaEnabled: Boolean
        get() = sp.getBoolean("auto_ota_enabled", true)
        set(value) = sp.edit().putBoolean("auto_ota_enabled", value).apply()

    var lastOtaStatus: String
        get() = sp.getString("last_ota_status", "—") ?: "—"
        set(value) = sp.edit().putString("last_ota_status", value.take(120)).apply()

    var lastOtaVersionCode: Int
        get() = sp.getInt("last_ota_version_code", 0)
        set(value) = sp.edit().putInt("last_ota_version_code", value).apply()

    var kioskPoliciesAppliedAt: Long
        get() = sp.getLong("kiosk_policies_at", 0L)
        set(value) = sp.edit().putLong("kiosk_policies_at", value).apply()

    var watchdogRelaunchCount: Int
        get() = sp.getInt("watchdog_relaunch_count", 0)
        set(value) = sp.edit().putInt("watchdog_relaunch_count", value).apply()

    var watchdogLastKickAt: Long
        get() = sp.getLong("watchdog_last_kick_at", 0L)
        set(value) = sp.edit().putLong("watchdog_last_kick_at", value).apply()

    var watchdogLastTickAt: Long
        get() = sp.getLong("watchdog_last_tick_at", 0L)
        set(value) = sp.edit().putLong("watchdog_last_tick_at", value).apply()

    var lastFieldDiag: String
        get() = sp.getString("last_field_diag", "") ?: ""
        set(value) = sp.edit().putString("last_field_diag", value.take(4000)).apply()

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
