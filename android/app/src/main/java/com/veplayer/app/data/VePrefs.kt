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

    /** Cockpit speed-limit HUD (km/h). */
    var speedHudEnabled: Boolean
        get() = sp.getBoolean("speed_hud", true)
        set(value) = sp.edit().putBoolean("speed_hud", value).apply()

    var speedLimitKmh: Int
        get() = sp.getInt("speed_limit_kmh", 50)
        set(value) = sp.edit().putInt("speed_limit_kmh", value.coerceIn(10, 160)).apply()

    /** Warn band before limit (near). */
    var speedWarnMarginKmh: Float
        get() = sp.getFloat("speed_warn_margin", 5f)
        set(value) = sp.edit().putFloat("speed_warn_margin", value.coerceIn(0f, 20f)).apply()

    var speedTtsWarn: Boolean
        get() = sp.getBoolean("speed_tts_warn", true)
        set(value) = sp.edit().putBoolean("speed_tts_warn", value).apply()

    /** Apply geofence max_kmh from SenseFlow heartbeat to HUD. */
    var geofenceSpeedEnabled: Boolean
        get() = sp.getBoolean("geofence_speed", true)
        set(value) = sp.edit().putBoolean("geofence_speed", value).apply()

    /** MIL / DTC alerts (inbox + TTS). */
    var dtcAlertsEnabled: Boolean
        get() = sp.getBoolean("dtc_alerts", true)
        set(value) = sp.edit().putBoolean("dtc_alerts", value).apply()

    var dtcTts: Boolean
        get() = sp.getBoolean("dtc_tts", true)
        set(value) = sp.edit().putBoolean("dtc_tts", value).apply()

    /** When OBD sim (no dongle), seed demo P0420/P0301 + MIL. */
    var dtcDemoSeed: Boolean
        get() = sp.getBoolean("dtc_demo_seed", true)
        set(value) = sp.edit().putBoolean("dtc_demo_seed", value).apply()

    /** Phone Link (BT / Android Auto / CarPlay). */
    var phoneLinkEnabled: Boolean
        get() = sp.getBoolean("phone_link", true)
        set(value) = sp.edit().putBoolean("phone_link", value).apply()

    /** none | bt_media | android_auto | carplay */
    var phoneLinkSim: String
        get() = sp.getString("phone_link_sim", "none") ?: "none"
        set(value) = sp.edit().putString("phone_link_sim", value).apply()

    /** Fuel / SOC / range HUD. */
    var fuelHudEnabled: Boolean
        get() = sp.getBoolean("fuel_hud", true)
        set(value) = sp.edit().putBoolean("fuel_hud", value).apply()

    /** Near band threshold (%). */
    var fuelWarnPct: Float
        get() = sp.getFloat("fuel_warn_pct", 20f)
        set(value) = sp.edit().putFloat("fuel_warn_pct", value.coerceIn(5f, 50f)).apply()

    /** Low / critical band (%). */
    var fuelCriticalPct: Float
        get() = sp.getFloat("fuel_crit_pct", 10f)
        set(value) = sp.edit().putFloat("fuel_crit_pct", value.coerceIn(2f, 30f)).apply()

    var rangeWarnKm: Float
        get() = sp.getFloat("range_warn_km", 40f)
        set(value) = sp.edit().putFloat("range_warn_km", value.coerceIn(5f, 200f)).apply()

    var rangeCriticalKm: Float
        get() = sp.getFloat("range_crit_km", 20f)
        set(value) = sp.edit().putFloat("range_crit_km", value.coerceIn(2f, 100f)).apply()

    var fuelTtsWarn: Boolean
        get() = sp.getBoolean("fuel_tts_warn", true)
        set(value) = sp.edit().putBoolean("fuel_tts_warn", value).apply()

    /** Idle (stopped + ignition) alerts. */
    var idleAlertEnabled: Boolean
        get() = sp.getBoolean("idle_alert", true)
        set(value) = sp.edit().putBoolean("idle_alert", value).apply()

    /** Seconds stopped before warn band. */
    var idleWarnSec: Int
        get() = sp.getInt("idle_warn_sec", 120)
        set(value) = sp.edit().putInt("idle_warn_sec", value.coerceIn(30, 3600)).apply()

    /** Seconds stopped before alert band. */
    var idleAlertSec: Int
        get() = sp.getInt("idle_alert_sec", 300)
        set(value) = sp.edit().putInt("idle_alert_sec", value.coerceIn(60, 7200)).apply()

    /** Max speed (km/h) still considered stopped. */
    var idleSpeedMaxKmh: Float
        get() = sp.getFloat("idle_speed_max", 1.5f)
        set(value) = sp.edit().putFloat("idle_speed_max", value.coerceIn(0.5f, 5f)).apply()

    var idleTtsWarn: Boolean
        get() = sp.getBoolean("idle_tts_warn", true)
        set(value) = sp.edit().putBoolean("idle_tts_warn", value).apply()

    /** Show SOS long-press on DriveViz. */
    var panicEnabled: Boolean
        get() = sp.getBoolean("panic_enabled", true)
        set(value) = sp.edit().putBoolean("panic_enabled", value).apply()

    /** Upload dashcam JPEG clip on SOS. */
    var sosClipEnabled: Boolean
        get() = sp.getBoolean("sos_clip", true)
        set(value) = sp.edit().putBoolean("sos_clip", value).apply()

    /** Use synthetic frame (no CameraX yet). */
    var sosClipSim: Boolean
        get() = sp.getBoolean("sos_clip_sim", true)
        set(value) = sp.edit().putBoolean("sos_clip_sim", value).apply()

    /** Declared buffer length metadata (seconds). */
    var sosClipSec: Int
        get() = sp.getInt("sos_clip_sec", 8)
        set(value) = sp.edit().putInt("sos_clip_sec", value.coerceIn(3, 30)).apply()

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

    /** Voice guidance (TextToSpeech) for next-turn cues. */
    var navTtsEnabled: Boolean
        get() = sp.getBoolean("nav_tts", true)
        set(value) = sp.edit().putBoolean("nav_tts", value).apply()

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

    /** Intermediate waypoints JSON: [{name,lat,lng},…] (final dest stays in navTo*). */
    var navWaypointsJson: String
        get() = sp.getString("nav_waypoints_json", "[]") ?: "[]"
        set(value) = sp.edit().putString("nav_waypoints_json", value).apply()

    /** native | web — cockpit map renderer. */
    var mapMode: String
        get() = sp.getString("map_mode", "native") ?: "native"
        set(value) = sp.edit().putString("map_mode", value.trim().lowercase()).apply()

    /** OSM (or compatible) raster tiles under native Compose map. */
    var mapTilesEnabled: Boolean
        get() = sp.getBoolean("map_tiles", true)
        set(value) = sp.edit().putBoolean("map_tiles", value).apply()

    /** SenseFlow crowd / surround actors on native map. */
    var mapCrowdEnabled: Boolean
        get() = sp.getBoolean("map_crowd", true)
        set(value) = sp.edit().putBoolean("map_crowd", value).apply()

    /** Tile URL template with {z}/{x}/{y}. Default: OSM. */
    var mapTileUrl: String
        get() =
            sp.getString("map_tile_url", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")
                ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        set(value) = sp.edit().putString("map_tile_url", value.trim()).apply()

    var mapPrefetchZMin: Int
        get() = sp.getInt("map_prefetch_zmin", 12)
        set(value) = sp.edit().putInt("map_prefetch_zmin", value.coerceIn(8, 16)).apply()

    var mapPrefetchZMax: Int
        get() = sp.getInt("map_prefetch_zmax", 15)
        set(value) = sp.edit().putInt("map_prefetch_zmax", value.coerceIn(10, 18)).apply()

    var mapPrefetchMaxTiles: Int
        get() = sp.getInt("map_prefetch_max", 2000)
        set(value) = sp.edit().putInt("map_prefetch_max", value.coerceIn(100, 8000)).apply()

    /** Parking guidelines on reverse camera. */
    var reverseGuidesEnabled: Boolean
        get() = sp.getBoolean("reverse_guides", true)
        set(value) = sp.edit().putBoolean("reverse_guides", value).apply()

    /** Track width of guide rails as fraction of preview (0.30..0.60). */
    var reverseGuideTrack: Float
        get() = sp.getFloat("reverse_guide_track", 0.46f)
        set(value) = sp.edit().putFloat("reverse_guide_track", value.coerceIn(0.30f, 0.60f)).apply()

    /** Parking distance HUD (PDC / USS). */
    var parkingHudEnabled: Boolean
        get() = sp.getBoolean("parking_hud", true)
        set(value) = sp.edit().putBoolean("parking_hud", value).apply()

    var parkingTts: Boolean
        get() = sp.getBoolean("parking_tts", true)
        set(value) = sp.edit().putBoolean("parking_tts", value).apply()

    /** Simulate rear USS when no live sensors. */
    var parkingSimEnabled: Boolean
        get() = sp.getBoolean("parking_sim", true)
        set(value) = sp.edit().putBoolean("parking_sim", value).apply()

    var parkingWarnM: Float
        get() = sp.getFloat("parking_warn_m", 1.5f)
        set(value) = sp.edit().putFloat("parking_warn_m", value.coerceIn(0.5f, 3f)).apply()

    var parkingCritM: Float
        get() = sp.getFloat("parking_crit_m", 0.6f)
        set(value) = sp.edit().putFloat("parking_crit_m", value.coerceIn(0.2f, 1.5f)).apply()

    /** Door ajar while moving. */
    var doorAjarEnabled: Boolean
        get() = sp.getBoolean("door_ajar", true)
        set(value) = sp.edit().putBoolean("door_ajar", value).apply()

    var doorAjarTts: Boolean
        get() = sp.getBoolean("door_ajar_tts", true)
        set(value) = sp.edit().putBoolean("door_ajar_tts", value).apply()

    var doorAjarWarnKmh: Float
        get() = sp.getFloat("door_ajar_warn_kmh", 5f)
        set(value) = sp.edit().putFloat("door_ajar_warn_kmh", value.coerceIn(1f, 30f)).apply()

    var doorAjarAlertKmh: Float
        get() = sp.getFloat("door_ajar_alert_kmh", 20f)
        set(value) = sp.edit().putFloat("door_ajar_alert_kmh", value.coerceIn(5f, 80f)).apply()

    /** Demo: pulse FL door open in mock/obd_sim. */
    var doorAjarSim: Boolean
        get() = sp.getBoolean("door_ajar_sim", false)
        set(value) = sp.edit().putBoolean("door_ajar_sim", value).apply()

    /** Seatbelt unlatched while moving. */
    var seatbeltEnabled: Boolean
        get() = sp.getBoolean("seatbelt", true)
        set(value) = sp.edit().putBoolean("seatbelt", value).apply()

    var seatbeltTts: Boolean
        get() = sp.getBoolean("seatbelt_tts", true)
        set(value) = sp.edit().putBoolean("seatbelt_tts", value).apply()

    var seatbeltWarnKmh: Float
        get() = sp.getFloat("seatbelt_warn_kmh", 5f)
        set(value) = sp.edit().putFloat("seatbelt_warn_kmh", value.coerceIn(1f, 30f)).apply()

    var seatbeltAlertKmh: Float
        get() = sp.getFloat("seatbelt_alert_kmh", 15f)
        set(value) = sp.edit().putFloat("seatbelt_alert_kmh", value.coerceIn(5f, 80f)).apply()

    /** Demo: driver seatbelt unlatched in mock/obd_sim. */
    var seatbeltSim: Boolean
        get() = sp.getBoolean("seatbelt_sim", false)
        set(value) = sp.edit().putBoolean("seatbelt_sim", value).apply()

    /** Shift duration / fatigue HUD. */
    var fatigueEnabled: Boolean
        get() = sp.getBoolean("fatigue", true)
        set(value) = sp.edit().putBoolean("fatigue", value).apply()

    var fatigueTts: Boolean
        get() = sp.getBoolean("fatigue_tts", true)
        set(value) = sp.edit().putBoolean("fatigue_tts", value).apply()

    /** Hours on shift before warn. */
    var fatigueWarnHours: Float
        get() = sp.getFloat("fatigue_warn_h", 4f)
        set(value) = sp.edit().putFloat("fatigue_warn_h", value.coerceIn(0.5f, 12f)).apply()

    /** Hours on shift before alert. */
    var fatigueAlertHours: Float
        get() = sp.getFloat("fatigue_alert_h", 8f)
        set(value) = sp.edit().putFloat("fatigue_alert_h", value.coerceIn(1f, 16f)).apply()

    /**
     * Demo override: pretend shift has lasted this many hours (0 = use real started_at).
     */
    var fatigueSimHours: Float
        get() = sp.getFloat("fatigue_sim_h", 0f)
        set(value) = sp.edit().putFloat("fatigue_sim_h", value.coerceIn(0f, 16f)).apply()

    /** HVAC climate panel. */
    var hvacPanelEnabled: Boolean
        get() = sp.getBoolean("hvac_panel", true)
        set(value) = sp.edit().putBoolean("hvac_panel", value).apply()

    /** |cabin-target| ≤ this → comfort band (°C). */
    var hvacComfortDeltaC: Float
        get() = sp.getFloat("hvac_comfort_delta", 2.5f)
        set(value) = sp.edit().putFloat("hvac_comfort_delta", value.coerceIn(0.5f, 6f)).apply()

    /** Cabin over-temperature alerts. */
    var cabinOvertempEnabled: Boolean
        get() = sp.getBoolean("cabin_overtemp", true)
        set(value) = sp.edit().putBoolean("cabin_overtemp", value).apply()

    var cabinOvertempTts: Boolean
        get() = sp.getBoolean("cabin_overtemp_tts", true)
        set(value) = sp.edit().putBoolean("cabin_overtemp_tts", value).apply()

    var cabinWarnC: Float
        get() = sp.getFloat("cabin_warn_c", 32f)
        set(value) = sp.edit().putFloat("cabin_warn_c", value.coerceIn(25f, 45f)).apply()

    var cabinAlertC: Float
        get() = sp.getFloat("cabin_alert_c", 38f)
        set(value) = sp.edit().putFloat("cabin_alert_c", value.coerceIn(28f, 55f)).apply()

    /** Demo: force cabin °C (0 = live). */
    var cabinOvertempSimC: Float
        get() = sp.getFloat("cabin_overtemp_sim_c", 0f)
        set(value) = sp.edit().putFloat("cabin_overtemp_sim_c", value.coerceIn(0f, 55f)).apply()

    /** Collect fleet alerts into inbox. */
    var fleetAlertsEnabled: Boolean
        get() = sp.getBoolean("fleet_alerts", true)
        set(value) = sp.edit().putBoolean("fleet_alerts", value).apply()

    /** Speak new fleet alerts (geofence, ABS, TPMS…). */
    var fleetTtsAlerts: Boolean
        get() = sp.getBoolean("fleet_tts_alerts", true)
        set(value) = sp.edit().putBoolean("fleet_tts_alerts", value).apply()

    /** Speak dispatcher message commands. */
    var fleetTtsMessages: Boolean
        get() = sp.getBoolean("fleet_tts_messages", true)
        set(value) = sp.edit().putBoolean("fleet_tts_messages", value).apply()

    /** JSON array ring of inbox items. */
    var fleetInboxJson: String
        get() = sp.getString("fleet_inbox_json", "[]") ?: "[]"
        set(value) = sp.edit().putString("fleet_inbox_json", value).apply()

    /** Odometer maintenance reminders. */
    var maintenanceEnabled: Boolean
        get() = sp.getBoolean("maint_enabled", true)
        set(value) = sp.edit().putBoolean("maint_enabled", value).apply()

    /** Speak maintenance due/warn. */
    var maintenanceTts: Boolean
        get() = sp.getBoolean("maint_tts", true)
        set(value) = sp.edit().putBoolean("maint_tts", value).apply()

    /** JSON schedule of service intervals. */
    var maintenanceJson: String
        get() = sp.getString("maint_json", "") ?: ""
        set(value) = sp.edit().putString("maint_json", value).apply()

    var driverId: Int
        get() = sp.getInt("driver_id", 0)
        set(value) = sp.edit().putInt("driver_id", value).apply()

    var driverCode: String
        get() = sp.getString("driver_code", "") ?: ""
        set(value) = sp.edit().putString("driver_code", value.trim()).apply()

    var driverName: String
        get() = sp.getString("driver_name", "") ?: ""
        set(value) = sp.edit().putString("driver_name", value.trim()).apply()

    var driverLanguage: String
        get() = sp.getString("driver_language", "es") ?: "es"
        set(value) = sp.edit().putString("driver_language", value.trim().ifBlank { "es" }).apply()

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
