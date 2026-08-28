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

    /** OEM white-label (fleet set_brand). */
    var brandId: String
        get() = sp.getString("brand_id", "") ?: ""
        set(value) = sp.edit().putString("brand_id", value.trim()).apply()

    var brandName: String
        get() = sp.getString("brand_name", "") ?: ""
        set(value) = sp.edit().putString("brand_name", value.trim()).apply()

    var brandLogoPath: String
        get() = sp.getString("brand_logo_path", "") ?: ""
        set(value) = sp.edit().putString("brand_logo_path", value).apply()

    var brandAccentArgb: Long
        get() = sp.getLong("brand_accent", 0xFF2DD4BFL)
        set(value) = sp.edit().putLong("brand_accent", value).apply()

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

    /** Distance with MIL on (OBD 0121). */
    var milDistEnabled: Boolean
        get() = sp.getBoolean("mil_dist", true)
        set(value) = sp.edit().putBoolean("mil_dist", value).apply()

    var milDistTts: Boolean
        get() = sp.getBoolean("mil_dist_tts", true)
        set(value) = sp.edit().putBoolean("mil_dist_tts", value).apply()

    var milDistWarnKm: Float
        get() = sp.getFloat("mil_dist_warn_km", 50f)
        set(value) = sp.edit().putFloat("mil_dist_warn_km", value.coerceIn(5f, 500f)).apply()

    var milDistAlertKm: Float
        get() = sp.getFloat("mil_dist_alert_km", 100f)
        set(value) = sp.edit().putFloat("mil_dist_alert_km", value.coerceIn(10f, 1000f)).apply()

    /** Demo km with MIL (0 = live OBD). */
    var milDistSimKm: Float
        get() = sp.getFloat("mil_dist_sim_km", 0f)
        set(value) = sp.edit().putFloat("mil_dist_sim_km", value.coerceIn(0f, 500f)).apply()

    /** Distance since DTC clear (OBD 0131). */
    var distClearEnabled: Boolean
        get() = sp.getBoolean("dist_clear", true)
        set(value) = sp.edit().putBoolean("dist_clear", value).apply()

    var distClearTts: Boolean
        get() = sp.getBoolean("dist_clear_tts", true)
        set(value) = sp.edit().putBoolean("dist_clear_tts", value).apply()

    var distClearWarnKm: Float
        get() = sp.getFloat("dist_clear_warn_km", 100f)
        set(value) = sp.edit().putFloat("dist_clear_warn_km", value.coerceIn(10f, 1000f)).apply()

    var distClearAlertKm: Float
        get() = sp.getFloat("dist_clear_alert_km", 200f)
        set(value) = sp.edit().putFloat("dist_clear_alert_km", value.coerceIn(20f, 2000f)).apply()

    /** Demo km since clear (0 = live OBD). */
    var distClearSimKm: Float
        get() = sp.getFloat("dist_clear_sim_km", 0f)
        set(value) = sp.edit().putFloat("dist_clear_sim_km", value.coerceIn(0f, 1000f)).apply()

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

    /** Unauthorized movement / tow while secured. */
    var towEnabled: Boolean
        get() = sp.getBoolean("tow", true)
        set(value) = sp.edit().putBoolean("tow", value).apply()

    var towTts: Boolean
        get() = sp.getBoolean("tow_tts", true)
        set(value) = sp.edit().putBoolean("tow_tts", value).apply()

    var towSpeedMinKmh: Float
        get() = sp.getFloat("tow_speed_min", 3f)
        set(value) = sp.edit().putFloat("tow_speed_min", value.coerceIn(1f, 15f)).apply()

    var towWarnSec: Float
        get() = sp.getFloat("tow_warn_sec", 3f)
        set(value) = sp.edit().putFloat("tow_warn_sec", value.coerceIn(1f, 30f)).apply()

    var towAlertSec: Float
        get() = sp.getFloat("tow_alert_sec", 8f)
        set(value) = sp.edit().putFloat("tow_alert_sec", value.coerceIn(3f, 60f)).apply()

    /** Demo: treat as secured + moving. */
    var towSim: Boolean
        get() = sp.getBoolean("tow_sim", false)
        set(value) = sp.edit().putBoolean("tow_sim", value).apply()

    var towSimKmh: Float
        get() = sp.getFloat("tow_sim_kmh", 12f)
        set(value) = sp.edit().putFloat("tow_sim_kmh", value.coerceIn(0f, 80f)).apply()

    /** Parking brake engaged while moving. */
    var pbrakeEnabled: Boolean
        get() = sp.getBoolean("pbrake_moving", true)
        set(value) = sp.edit().putBoolean("pbrake_moving", value).apply()

    var pbrakeTts: Boolean
        get() = sp.getBoolean("pbrake_tts", true)
        set(value) = sp.edit().putBoolean("pbrake_tts", value).apply()

    var pbrakeWarnKmh: Float
        get() = sp.getFloat("pbrake_warn_kmh", 5f)
        set(value) = sp.edit().putFloat("pbrake_warn_kmh", value.coerceIn(1f, 40f)).apply()

    var pbrakeAlertKmh: Float
        get() = sp.getFloat("pbrake_alert_kmh", 15f)
        set(value) = sp.edit().putFloat("pbrake_alert_kmh", value.coerceIn(5f, 60f)).apply()

    /** Demo: force parking brake on (+ optional speed). */
    var pbrakeSim: Boolean
        get() = sp.getBoolean("pbrake_sim", false)
        set(value) = sp.edit().putBoolean("pbrake_sim", value).apply()

    var pbrakeSimKmh: Float
        get() = sp.getFloat("pbrake_sim_kmh", 20f)
        set(value) = sp.edit().putFloat("pbrake_sim_kmh", value.coerceIn(0f, 80f)).apply()

    /** Rolling in Park / Neutral. */
    var gearRollEnabled: Boolean
        get() = sp.getBoolean("gear_roll", true)
        set(value) = sp.edit().putBoolean("gear_roll", value).apply()

    var gearRollTts: Boolean
        get() = sp.getBoolean("gear_roll_tts", true)
        set(value) = sp.edit().putBoolean("gear_roll_tts", value).apply()

    var gearRollWarnKmh: Float
        get() = sp.getFloat("gear_roll_warn_kmh", 5f)
        set(value) = sp.edit().putFloat("gear_roll_warn_kmh", value.coerceIn(1f, 40f)).apply()

    var gearRollAlertKmh: Float
        get() = sp.getFloat("gear_roll_alert_kmh", 20f)
        set(value) = sp.edit().putFloat("gear_roll_alert_kmh", value.coerceIn(5f, 80f)).apply()

    /** Demo: force P/N + speed. */
    var gearRollSim: Boolean
        get() = sp.getBoolean("gear_roll_sim", false)
        set(value) = sp.edit().putBoolean("gear_roll_sim", value).apply()

    var gearRollSimGear: String
        get() = sp.getString("gear_roll_sim_gear", "N") ?: "N"
        set(value) =
            sp.edit().putString(
                "gear_roll_sim_gear",
                value.trim().uppercase().let { if (it == "P") "P" else "N" },
            ).apply()

    var gearRollSimKmh: Float
        get() = sp.getFloat("gear_roll_sim_kmh", 25f)
        set(value) = sp.edit().putFloat("gear_roll_sim_kmh", value.coerceIn(0f, 80f)).apply()

    /** Forgotten turn signal (LEFT/RIGHT held). */
    var turnStuckEnabled: Boolean
        get() = sp.getBoolean("turn_stuck", true)
        set(value) = sp.edit().putBoolean("turn_stuck", value).apply()

    var turnStuckTts: Boolean
        get() = sp.getBoolean("turn_stuck_tts", true)
        set(value) = sp.edit().putBoolean("turn_stuck_tts", value).apply()

    var turnStuckWarnSec: Float
        get() = sp.getFloat("turn_stuck_warn_sec", 30f)
        set(value) = sp.edit().putFloat("turn_stuck_warn_sec", value.coerceIn(10f, 180f)).apply()

    var turnStuckAlertSec: Float
        get() = sp.getFloat("turn_stuck_alert_sec", 60f)
        set(value) = sp.edit().putFloat("turn_stuck_alert_sec", value.coerceIn(20f, 300f)).apply()

    var turnStuckSpeedMinKmh: Float
        get() = sp.getFloat("turn_stuck_speed_min", 5f)
        set(value) = sp.edit().putFloat("turn_stuck_speed_min", value.coerceIn(0f, 30f)).apply()

    /** |steering| ≥ this cancels held timer (deg). */
    var turnStuckSteerCancelDeg: Float
        get() = sp.getFloat("turn_stuck_steer_cancel", 35f)
        set(value) = sp.edit().putFloat("turn_stuck_steer_cancel", value.coerceIn(10f, 90f)).apply()

    /** Demo: pretend blinker held this many seconds (0 = live). */
    var turnStuckSimSec: Float
        get() = sp.getFloat("turn_stuck_sim_sec", 0f)
        set(value) = sp.edit().putFloat("turn_stuck_sim_sec", value.coerceIn(0f, 300f)).apply()

    var turnStuckSimSide: String
        get() = sp.getString("turn_stuck_sim_side", "left") ?: "left"
        set(value) =
            sp.edit().putString(
                "turn_stuck_sim_side",
                value.trim().lowercase().let { if (it == "right") "right" else "left" },
            ).apply()

    /** Hazard lights forgotten while moving. */
    var hazardStuckEnabled: Boolean
        get() = sp.getBoolean("hazard_stuck", true)
        set(value) = sp.edit().putBoolean("hazard_stuck", value).apply()

    var hazardStuckTts: Boolean
        get() = sp.getBoolean("hazard_stuck_tts", true)
        set(value) = sp.edit().putBoolean("hazard_stuck_tts", value).apply()

    var hazardStuckWarnSec: Float
        get() = sp.getFloat("hazard_stuck_warn_sec", 45f)
        set(value) = sp.edit().putFloat("hazard_stuck_warn_sec", value.coerceIn(15f, 300f)).apply()

    var hazardStuckAlertSec: Float
        get() = sp.getFloat("hazard_stuck_alert_sec", 90f)
        set(value) = sp.edit().putFloat("hazard_stuck_alert_sec", value.coerceIn(30f, 600f)).apply()

    var hazardStuckSpeedMinKmh: Float
        get() = sp.getFloat("hazard_stuck_speed_min", 5f)
        set(value) = sp.edit().putFloat("hazard_stuck_speed_min", value.coerceIn(0f, 40f)).apply()

    /** Demo: pretend hazards held this many seconds (0 = live). */
    var hazardStuckSimSec: Float
        get() = sp.getFloat("hazard_stuck_sim_sec", 0f)
        set(value) = sp.edit().putFloat("hazard_stuck_sim_sec", value.coerceIn(0f, 600f)).apply()

    /** ABS / ESC intervention HUD. */
    var absHudEnabled: Boolean
        get() = sp.getBoolean("abs_hud", true)
        set(value) = sp.edit().putBoolean("abs_hud", value).apply()

    var absHudTts: Boolean
        get() = sp.getBoolean("abs_hud_tts", true)
        set(value) = sp.edit().putBoolean("abs_hud_tts", value).apply()

    /** Seconds ABS active → warn. */
    var absWarnSec: Float
        get() = sp.getFloat("abs_warn_sec", 0.5f)
        set(value) = sp.edit().putFloat("abs_warn_sec", value.coerceIn(0.2f, 5f)).apply()

    /** Seconds ABS active → alert. */
    var absAlertSec: Float
        get() = sp.getFloat("abs_alert_sec", 2f)
        set(value) = sp.edit().putFloat("abs_alert_sec", value.coerceIn(0.5f, 10f)).apply()

    /** Events in 60s → alert. */
    var absAlertEvents: Float
        get() = sp.getFloat("abs_alert_events", 3f)
        set(value) = sp.edit().putFloat("abs_alert_events", value.coerceIn(2f, 20f)).apply()

    /** Demo: force ABS active. */
    var absSim: Boolean
        get() = sp.getBoolean("abs_sim", false)
        set(value) = sp.edit().putBoolean("abs_sim", value).apply()

    /** Sudden fuel drop (theft / leak) in a short window. */
    var fuelDropEnabled: Boolean
        get() = sp.getBoolean("fuel_drop", true)
        set(value) = sp.edit().putBoolean("fuel_drop", value).apply()

    var fuelDropTts: Boolean
        get() = sp.getBoolean("fuel_drop_tts", true)
        set(value) = sp.edit().putBoolean("fuel_drop_tts", value).apply()

    /** Drop % in window → warn. */
    var fuelDropWarnPct: Float
        get() = sp.getFloat("fuel_drop_warn", 8f)
        set(value) = sp.edit().putFloat("fuel_drop_warn", value.coerceIn(2f, 40f)).apply()

    /** Drop % in window → alert. */
    var fuelDropAlertPct: Float
        get() = sp.getFloat("fuel_drop_alert", 15f)
        set(value) = sp.edit().putFloat("fuel_drop_alert", value.coerceIn(5f, 60f)).apply()

    /** Sliding window seconds for peak→current drop. */
    var fuelDropWindowSec: Float
        get() = sp.getFloat("fuel_drop_window", 60f)
        set(value) = sp.edit().putFloat("fuel_drop_window", value.coerceIn(10f, 600f)).apply()

    /** Demo: force a drop of N% (0 = off). */
    var fuelDropSimDropPct: Float
        get() = sp.getFloat("fuel_drop_sim", 0f)
        set(value) = sp.edit().putFloat("fuel_drop_sim", value.coerceIn(0f, 80f)).apply()

    /** Per-wheel TPMS HUD. */
    var tpmsHudEnabled: Boolean
        get() = sp.getBoolean("tpms_hud", true)
        set(value) = sp.edit().putBoolean("tpms_hud", value).apply()

    var tpmsTts: Boolean
        get() = sp.getBoolean("tpms_tts", true)
        set(value) = sp.edit().putBoolean("tpms_tts", value).apply()

    /** PSI below → warn. */
    var tpmsWarnPsi: Float
        get() = sp.getFloat("tpms_warn_psi", 28f)
        set(value) = sp.edit().putFloat("tpms_warn_psi", value.coerceIn(15f, 40f)).apply()

    /** PSI below → alert. */
    var tpmsAlertPsi: Float
        get() = sp.getFloat("tpms_alert_psi", 24f)
        set(value) = sp.edit().putFloat("tpms_alert_psi", value.coerceIn(10f, 35f)).apply()

    /** Demo: override FL psi (0 = live). */
    var tpmsSimFlPsi: Float
        get() = sp.getFloat("tpms_sim_fl", 0f)
        set(value) = sp.edit().putFloat("tpms_sim_fl", value.coerceIn(0f, 60f)).apply()

    /** 12V battery voltage HUD. */
    var battVoltEnabled: Boolean
        get() = sp.getBoolean("batt_volt", true)
        set(value) = sp.edit().putBoolean("batt_volt", value).apply()

    var battVoltTts: Boolean
        get() = sp.getBoolean("batt_volt_tts", true)
        set(value) = sp.edit().putBoolean("batt_volt_tts", value).apply()

    /** Below this V → warn. */
    var battVoltWarnV: Float
        get() = sp.getFloat("batt_volt_warn", 12.0f)
        set(value) = sp.edit().putFloat("batt_volt_warn", value.coerceIn(10f, 13.5f)).apply()

    /** Below this V → alert. */
    var battVoltAlertV: Float
        get() = sp.getFloat("batt_volt_alert", 11.5f)
        set(value) = sp.edit().putFloat("batt_volt_alert", value.coerceIn(9f, 12.5f)).apply()

    /** Demo: override volts (0 = live). */
    var battVoltSimV: Float
        get() = sp.getFloat("batt_volt_sim", 0f)
        set(value) = sp.edit().putFloat("batt_volt_sim", value.coerceIn(0f, 16f)).apply()

    /** Driver incident reports (non-SOS). */
    var incidentEnabled: Boolean
        get() = sp.getBoolean("incident_enabled", true)
        set(value) = sp.edit().putBoolean("incident_enabled", value).apply()

    var incidentClipEnabled: Boolean
        get() = sp.getBoolean("incident_clip", true)
        set(value) = sp.edit().putBoolean("incident_clip", value).apply()

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

    /** Off-route / route deviation vs active nav polyline. */
    var routeDevEnabled: Boolean
        get() = sp.getBoolean("route_dev", true)
        set(value) = sp.edit().putBoolean("route_dev", value).apply()

    var routeDevTts: Boolean
        get() = sp.getBoolean("route_dev_tts", true)
        set(value) = sp.edit().putBoolean("route_dev_tts", value).apply()

    /** Meters off polyline → warn. */
    var routeDevWarnM: Float
        get() = sp.getFloat("route_dev_warn_m", 80f)
        set(value) = sp.edit().putFloat("route_dev_warn_m", value.coerceIn(20f, 500f)).apply()

    /** Meters off polyline → alert. */
    var routeDevAlertM: Float
        get() = sp.getFloat("route_dev_alert_m", 150f)
        set(value) = sp.edit().putFloat("route_dev_alert_m", value.coerceIn(40f, 800f)).apply()

    /** Seconds continuously off-route before showWarn / TTS. */
    var routeDevHoldSec: Float
        get() = sp.getFloat("route_dev_hold_sec", 8f)
        set(value) = sp.edit().putFloat("route_dev_hold_sec", value.coerceIn(0f, 120f)).apply()

    /** Demo: pretend this many meters off route (0 = live). */
    var routeDevSimM: Float
        get() = sp.getFloat("route_dev_sim_m", 0f)
        set(value) = sp.edit().putFloat("route_dev_sim_m", value.coerceIn(0f, 1000f)).apply()

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

    /** Harsh brake / accel detection. */
    var harshEnabled: Boolean
        get() = sp.getBoolean("harsh", true)
        set(value) = sp.edit().putBoolean("harsh", value).apply()

    var harshTts: Boolean
        get() = sp.getBoolean("harsh_tts", true)
        set(value) = sp.edit().putBoolean("harsh_tts", value).apply()

    var harshBrakeWarnKmhS: Float
        get() = sp.getFloat("harsh_brake_warn", 12f)
        set(value) = sp.edit().putFloat("harsh_brake_warn", value.coerceIn(6f, 25f)).apply()

    var harshBrakeAlertKmhS: Float
        get() = sp.getFloat("harsh_brake_alert", 18f)
        set(value) = sp.edit().putFloat("harsh_brake_alert", value.coerceIn(10f, 40f)).apply()

    var harshAccelWarnKmhS: Float
        get() = sp.getFloat("harsh_accel_warn", 10f)
        set(value) = sp.edit().putFloat("harsh_accel_warn", value.coerceIn(5f, 25f)).apply()

    var harshAccelAlertKmhS: Float
        get() = sp.getFloat("harsh_accel_alert", 15f)
        set(value) = sp.edit().putFloat("harsh_accel_alert", value.coerceIn(8f, 35f)).apply()

    /** Collision / impact candidate (extreme decel or yaw). */
    var impactEnabled: Boolean
        get() = sp.getBoolean("impact", true)
        set(value) = sp.edit().putBoolean("impact", value).apply()

    var impactTts: Boolean
        get() = sp.getBoolean("impact_tts", true)
        set(value) = sp.edit().putBoolean("impact_tts", value).apply()

    var impactDecelWarnKmhS: Float
        get() = sp.getFloat("impact_decel_warn", 28f)
        set(value) = sp.edit().putFloat("impact_decel_warn", value.coerceIn(15f, 60f)).apply()

    var impactDecelAlertKmhS: Float
        get() = sp.getFloat("impact_decel_alert", 40f)
        set(value) = sp.edit().putFloat("impact_decel_alert", value.coerceIn(20f, 80f)).apply()

    var impactYawWarnDegS: Float
        get() = sp.getFloat("impact_yaw_warn", 80f)
        set(value) = sp.edit().putFloat("impact_yaw_warn", value.coerceIn(40f, 200f)).apply()

    var impactYawAlertDegS: Float
        get() = sp.getFloat("impact_yaw_alert", 120f)
        set(value) = sp.edit().putFloat("impact_yaw_alert", value.coerceIn(60f, 300f)).apply()

    var impactSpeedMinKmh: Float
        get() = sp.getFloat("impact_speed_min", 8f)
        set(value) = sp.edit().putFloat("impact_speed_min", value.coerceIn(0f, 40f)).apply()

    /** Safety driver scorecard (shift). */
    var driverScoreEnabled: Boolean
        get() = sp.getBoolean("driver_score", true)
        set(value) = sp.edit().putBoolean("driver_score", value).apply()

    var driverScoreTts: Boolean
        get() = sp.getBoolean("driver_score_tts", true)
        set(value) = sp.edit().putBoolean("driver_score_tts", value).apply()

    /** Score below this → warn. */
    var driverScoreWarn: Float
        get() = sp.getFloat("driver_score_warn", 70f)
        set(value) = sp.edit().putFloat("driver_score_warn", value.coerceIn(30f, 95f)).apply()

    /** Score at/below this → alert. */
    var driverScoreAlert: Float
        get() = sp.getFloat("driver_score_alert", 50f)
        set(value) = sp.edit().putFloat("driver_score_alert", value.coerceIn(10f, 80f)).apply()

    /** Demo: force score 1–100 (0 = live). */
    var driverScoreSimScore: Float
        get() = sp.getFloat("driver_score_sim", 0f)
        set(value) = sp.edit().putFloat("driver_score_sim", value.coerceIn(0f, 100f)).apply()

    /** Live eco score alerts during shift. */
    var ecoLiveEnabled: Boolean
        get() = sp.getBoolean("eco_live", true)
        set(value) = sp.edit().putBoolean("eco_live", value).apply()

    var ecoLiveTts: Boolean
        get() = sp.getBoolean("eco_live_tts", true)
        set(value) = sp.edit().putBoolean("eco_live_tts", value).apply()

    var ecoLiveWarn: Float
        get() = sp.getFloat("eco_live_warn", 70f)
        set(value) = sp.edit().putFloat("eco_live_warn", value.coerceIn(30f, 95f)).apply()

    var ecoLiveAlert: Float
        get() = sp.getFloat("eco_live_alert", 50f)
        set(value) = sp.edit().putFloat("eco_live_alert", value.coerceIn(10f, 80f)).apply()

    /** Demo eco score 1–100 (0 = live from shift). */
    var ecoLiveSimScore: Float
        get() = sp.getFloat("eco_live_sim", 0f)
        set(value) = sp.edit().putFloat("eco_live_sim", value.coerceIn(0f, 100f)).apply()

    /** Engine run time since start (OBD 011F). */
    var engineRuntimeEnabled: Boolean
        get() = sp.getBoolean("engine_runtime", true)
        set(value) = sp.edit().putBoolean("engine_runtime", value).apply()

    var engineRuntimeTts: Boolean
        get() = sp.getBoolean("engine_runtime_tts", true)
        set(value) = sp.edit().putBoolean("engine_runtime_tts", value).apply()

    /** Hours of continuous engine run before warn. */
    var engineRuntimeWarnHours: Float
        get() = sp.getFloat("engine_runtime_warn_h", 2f)
        set(value) = sp.edit().putFloat("engine_runtime_warn_h", value.coerceIn(0.25f, 12f)).apply()

    /** Hours of continuous engine run before alert. */
    var engineRuntimeAlertHours: Float
        get() = sp.getFloat("engine_runtime_alert_h", 4f)
        set(value) = sp.edit().putFloat("engine_runtime_alert_h", value.coerceIn(0.5f, 16f)).apply()

    /** Demo runtime hours (0 = live OBD/CAN). */
    var engineRuntimeSimHours: Float
        get() = sp.getFloat("engine_runtime_sim_h", 0f)
        set(value) = sp.edit().putFloat("engine_runtime_sim_h", value.coerceIn(0f, 24f)).apply()

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

    /** Continuous driving rest-break reminder. */
    var restBreakEnabled: Boolean
        get() = sp.getBoolean("rest_break", true)
        set(value) = sp.edit().putBoolean("rest_break", value).apply()

    var restBreakTts: Boolean
        get() = sp.getBoolean("rest_break_tts", true)
        set(value) = sp.edit().putBoolean("rest_break_tts", value).apply()

    /** Minutes continuous driving → warn. */
    var restDriveWarnMin: Float
        get() = sp.getFloat("rest_drive_warn_min", 120f)
        set(value) = sp.edit().putFloat("rest_drive_warn_min", value.coerceIn(15f, 360f)).apply()

    /** Minutes continuous driving → alert. */
    var restDriveAlertMin: Float
        get() = sp.getFloat("rest_drive_alert_min", 150f)
        set(value) = sp.edit().putFloat("rest_drive_alert_min", value.coerceIn(20f, 480f)).apply()

    /** Minutes stopped to reset driving accumulator. */
    var restResetMin: Float
        get() = sp.getFloat("rest_reset_min", 15f)
        set(value) = sp.edit().putFloat("rest_reset_min", value.coerceIn(5f, 60f)).apply()

    /** Speed ≥ this counts as driving. */
    var restSpeedMinKmh: Float
        get() = sp.getFloat("rest_speed_min", 5f)
        set(value) = sp.edit().putFloat("rest_speed_min", value.coerceIn(1f, 20f)).apply()

    /** Demo: pretend continuous driving this many minutes (0 = live). */
    var restSimDriveMin: Float
        get() = sp.getFloat("rest_sim_drive_min", 0f)
        set(value) = sp.edit().putFloat("rest_sim_drive_min", value.coerceIn(0f, 480f)).apply()

    /** End-of-shift summary HUD / TTS. */
    var shiftSummaryEnabled: Boolean
        get() = sp.getBoolean("shift_summary", true)
        set(value) = sp.edit().putBoolean("shift_summary", value).apply()

    var shiftSummaryTts: Boolean
        get() = sp.getBoolean("shift_summary_tts", true)
        set(value) = sp.edit().putBoolean("shift_summary_tts", value).apply()

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

    /** Engine coolant overheat. */
    var coolantEnabled: Boolean
        get() = sp.getBoolean("coolant", true)
        set(value) = sp.edit().putBoolean("coolant", value).apply()

    var coolantTts: Boolean
        get() = sp.getBoolean("coolant_tts", true)
        set(value) = sp.edit().putBoolean("coolant_tts", value).apply()

    var coolantWarnC: Float
        get() = sp.getFloat("coolant_warn_c", 105f)
        set(value) = sp.edit().putFloat("coolant_warn_c", value.coerceIn(90f, 125f)).apply()

    var coolantAlertC: Float
        get() = sp.getFloat("coolant_alert_c", 115f)
        set(value) = sp.edit().putFloat("coolant_alert_c", value.coerceIn(95f, 140f)).apply()

    /** Demo: force coolant °C (0 = live). */
    var coolantSimC: Float
        get() = sp.getFloat("coolant_sim_c", 0f)
        set(value) = sp.edit().putFloat("coolant_sim_c", value.coerceIn(0f, 140f)).apply()

    /** Engine oil temperature (OBD 015C). */
    var oilTempEnabled: Boolean
        get() = sp.getBoolean("oil_temp", true)
        set(value) = sp.edit().putBoolean("oil_temp", value).apply()

    var oilTempTts: Boolean
        get() = sp.getBoolean("oil_temp_tts", true)
        set(value) = sp.edit().putBoolean("oil_temp_tts", value).apply()

    var oilTempWarnC: Float
        get() = sp.getFloat("oil_temp_warn_c", 120f)
        set(value) = sp.edit().putFloat("oil_temp_warn_c", value.coerceIn(90f, 140f)).apply()

    var oilTempAlertC: Float
        get() = sp.getFloat("oil_temp_alert_c", 130f)
        set(value) = sp.edit().putFloat("oil_temp_alert_c", value.coerceIn(100f, 160f)).apply()

    /** Demo: force oil °C (0 = live). */
    var oilTempSimC: Float
        get() = sp.getFloat("oil_temp_sim_c", 0f)
        set(value) = sp.edit().putFloat("oil_temp_sim_c", value.coerceIn(0f, 160f)).apply()

    /** Catalyst temperature (OBD 0134). */
    var catalystEnabled: Boolean
        get() = sp.getBoolean("catalyst_temp", true)
        set(value) = sp.edit().putBoolean("catalyst_temp", value).apply()

    var catalystTts: Boolean
        get() = sp.getBoolean("catalyst_temp_tts", true)
        set(value) = sp.edit().putBoolean("catalyst_temp_tts", value).apply()

    var catalystWarnC: Float
        get() = sp.getFloat("catalyst_warn_c", 750f)
        set(value) = sp.edit().putFloat("catalyst_warn_c", value.coerceIn(400f, 1000f)).apply()

    var catalystAlertC: Float
        get() = sp.getFloat("catalyst_alert_c", 850f)
        set(value) = sp.edit().putFloat("catalyst_alert_c", value.coerceIn(500f, 1200f)).apply()

    /** Demo catalyst °C (0 = live OBD). */
    var catalystSimC: Float
        get() = sp.getFloat("catalyst_sim_c", 0f)
        set(value) = sp.edit().putFloat("catalyst_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Intake air temperature (OBD 010F). */
    var intakeAirEnabled: Boolean
        get() = sp.getBoolean("intake_air", true)
        set(value) = sp.edit().putBoolean("intake_air", value).apply()

    var intakeAirTts: Boolean
        get() = sp.getBoolean("intake_air_tts", true)
        set(value) = sp.edit().putBoolean("intake_air_tts", value).apply()

    var intakeAirWarnC: Float
        get() = sp.getFloat("intake_air_warn_c", 50f)
        set(value) = sp.edit().putFloat("intake_air_warn_c", value.coerceIn(30f, 80f)).apply()

    var intakeAirAlertC: Float
        get() = sp.getFloat("intake_air_alert_c", 60f)
        set(value) = sp.edit().putFloat("intake_air_alert_c", value.coerceIn(35f, 100f)).apply()

    /** Demo: force intake °C (0 = live). */
    var intakeAirSimC: Float
        get() = sp.getFloat("intake_air_sim_c", 0f)
        set(value) = sp.edit().putFloat("intake_air_sim_c", value.coerceIn(0f, 120f)).apply()

    /** Engine fuel rate (OBD 015E). */
    var fuelRateEnabled: Boolean
        get() = sp.getBoolean("fuel_rate", true)
        set(value) = sp.edit().putBoolean("fuel_rate", value).apply()

    var fuelRateTts: Boolean
        get() = sp.getBoolean("fuel_rate_tts", true)
        set(value) = sp.edit().putBoolean("fuel_rate_tts", value).apply()

    var fuelRateWarnLph: Float
        get() = sp.getFloat("fuel_rate_warn_lph", 55f)
        set(value) = sp.edit().putFloat("fuel_rate_warn_lph", value.coerceIn(10f, 200f)).apply()

    var fuelRateAlertLph: Float
        get() = sp.getFloat("fuel_rate_alert_lph", 80f)
        set(value) = sp.edit().putFloat("fuel_rate_alert_lph", value.coerceIn(15f, 250f)).apply()

    var fuelRateSpeedMinKmh: Float
        get() = sp.getFloat("fuel_rate_speed_min", 20f)
        set(value) = sp.edit().putFloat("fuel_rate_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo fuel rate L/h (0 = live). */
    var fuelRateSimLph: Float
        get() = sp.getFloat("fuel_rate_sim_lph", 0f)
        set(value) = sp.edit().putFloat("fuel_rate_sim_lph", value.coerceIn(0f, 250f)).apply()

    var fuelRateSimSpeedKmh: Float
        get() = sp.getFloat("fuel_rate_sim_speed", 40f)
        set(value) = sp.edit().putFloat("fuel_rate_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Mass air flow (OBD 0110). */
    var mafEnabled: Boolean
        get() = sp.getBoolean("maf", true)
        set(value) = sp.edit().putBoolean("maf", value).apply()

    var mafTts: Boolean
        get() = sp.getBoolean("maf_tts", true)
        set(value) = sp.edit().putBoolean("maf_tts", value).apply()

    var mafWarnGps: Float
        get() = sp.getFloat("maf_warn_gps", 80f)
        set(value) = sp.edit().putFloat("maf_warn_gps", value.coerceIn(20f, 300f)).apply()

    var mafAlertGps: Float
        get() = sp.getFloat("maf_alert_gps", 110f)
        set(value) = sp.edit().putFloat("maf_alert_gps", value.coerceIn(30f, 400f)).apply()

    var mafSpeedMinKmh: Float
        get() = sp.getFloat("maf_speed_min", 20f)
        set(value) = sp.edit().putFloat("maf_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo MAF g/s (0 = live OBD). */
    var mafSimGps: Float
        get() = sp.getFloat("maf_sim_gps", 0f)
        set(value) = sp.edit().putFloat("maf_sim_gps", value.coerceIn(0f, 400f)).apply()

    var mafSimSpeedKmh: Float
        get() = sp.getFloat("maf_sim_speed", 40f)
        set(value) = sp.edit().putFloat("maf_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Fuel rail pressure (OBD 010A). */
    var fuelPressEnabled: Boolean
        get() = sp.getBoolean("fuel_press", true)
        set(value) = sp.edit().putBoolean("fuel_press", value).apply()

    var fuelPressTts: Boolean
        get() = sp.getBoolean("fuel_press_tts", true)
        set(value) = sp.edit().putBoolean("fuel_press_tts", value).apply()

    var fuelPressWarnKpa: Float
        get() = sp.getFloat("fuel_press_warn_kpa", 280f)
        set(value) = sp.edit().putFloat("fuel_press_warn_kpa", value.coerceIn(200f, 500f)).apply()

    var fuelPressAlertKpa: Float
        get() = sp.getFloat("fuel_press_alert_kpa", 220f)
        set(value) = sp.edit().putFloat("fuel_press_alert_kpa", value.coerceIn(100f, 400f)).apply()

    var fuelPressSpeedMinKmh: Float
        get() = sp.getFloat("fuel_press_speed_min", 20f)
        set(value) = sp.edit().putFloat("fuel_press_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo fuel pressure kPa (0 = live OBD). */
    var fuelPressSimKpa: Float
        get() = sp.getFloat("fuel_press_sim_kpa", 0f)
        set(value) = sp.edit().putFloat("fuel_press_sim_kpa", value.coerceIn(0f, 765f)).apply()

    var fuelPressSimSpeedKmh: Float
        get() = sp.getFloat("fuel_press_sim_speed", 40f)
        set(value) = sp.edit().putFloat("fuel_press_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Barometric pressure (OBD 0133). */
    var baroEnabled: Boolean
        get() = sp.getBoolean("baro", true)
        set(value) = sp.edit().putBoolean("baro", value).apply()

    var baroTts: Boolean
        get() = sp.getBoolean("baro_tts", true)
        set(value) = sp.edit().putBoolean("baro_tts", value).apply()

    var baroWarnLowKpa: Float
        get() = sp.getFloat("baro_warn_low_kpa", 88f)
        set(value) = sp.edit().putFloat("baro_warn_low_kpa", value.coerceIn(50f, 120f)).apply()

    var baroAlertLowKpa: Float
        get() = sp.getFloat("baro_alert_low_kpa", 82f)
        set(value) = sp.edit().putFloat("baro_alert_low_kpa", value.coerceIn(40f, 115f)).apply()

    var baroWarnHighKpa: Float
        get() = sp.getFloat("baro_warn_high_kpa", 108f)
        set(value) = sp.edit().putFloat("baro_warn_high_kpa", value.coerceIn(100f, 200f)).apply()

    var baroAlertHighKpa: Float
        get() = sp.getFloat("baro_alert_high_kpa", 112f)
        set(value) = sp.edit().putFloat("baro_alert_high_kpa", value.coerceIn(105f, 255f)).apply()

    var baroSpeedMinKmh: Float
        get() = sp.getFloat("baro_speed_min", 20f)
        set(value) = sp.edit().putFloat("baro_speed_min", value.coerceIn(0f, 60f)).apply()

    var baroSimKpa: Float
        get() = sp.getFloat("baro_sim_kpa", 0f)
        set(value) = sp.edit().putFloat("baro_sim_kpa", value.coerceIn(0f, 255f)).apply()

    var baroSimSpeedKmh: Float
        get() = sp.getFloat("baro_sim_speed", 40f)
        set(value) = sp.edit().putFloat("baro_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Ignition timing advance (OBD 010E). */
    var timingEnabled: Boolean
        get() = sp.getBoolean("timing", true)
        set(value) = sp.edit().putBoolean("timing", value).apply()

    var timingTts: Boolean
        get() = sp.getBoolean("timing_tts", true)
        set(value) = sp.edit().putBoolean("timing_tts", value).apply()

    var timingWarnDeg: Float
        get() = sp.getFloat("timing_warn_deg", 38f)
        set(value) = sp.edit().putFloat("timing_warn_deg", value.coerceIn(10f, 60f)).apply()

    var timingAlertDeg: Float
        get() = sp.getFloat("timing_alert_deg", 45f)
        set(value) = sp.edit().putFloat("timing_alert_deg", value.coerceIn(15f, 64f)).apply()

    var timingSpeedMinKmh: Float
        get() = sp.getFloat("timing_speed_min", 20f)
        set(value) = sp.edit().putFloat("timing_speed_min", value.coerceIn(0f, 60f)).apply()

    var timingRpmMin: Float
        get() = sp.getFloat("timing_rpm_min", 800f)
        set(value) = sp.edit().putFloat("timing_rpm_min", value.coerceIn(400f, 3000f)).apply()

    var timingSimDeg: Float
        get() = sp.getFloat("timing_sim_deg", 0f)
        set(value) = sp.edit().putFloat("timing_sim_deg", value.coerceIn(-64f, 64f)).apply()

    var timingSimSpeedKmh: Float
        get() = sp.getFloat("timing_sim_speed", 40f)
        set(value) = sp.edit().putFloat("timing_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** O2 sensor voltage B1S1 (OBD 014A). */
    var o2Enabled: Boolean
        get() = sp.getBoolean("o2_volt", true)
        set(value) = sp.edit().putBoolean("o2_volt", value).apply()

    var o2Tts: Boolean
        get() = sp.getBoolean("o2_volt_tts", true)
        set(value) = sp.edit().putBoolean("o2_volt_tts", value).apply()

    var o2WarnLowV: Float
        get() = sp.getFloat("o2_warn_low_v", 0.10f)
        set(value) = sp.edit().putFloat("o2_warn_low_v", value.coerceIn(0.02f, 0.5f)).apply()

    var o2AlertLowV: Float
        get() = sp.getFloat("o2_alert_low_v", 0.06f)
        set(value) = sp.edit().putFloat("o2_alert_low_v", value.coerceIn(0.01f, 0.2f)).apply()

    var o2WarnHighV: Float
        get() = sp.getFloat("o2_warn_high_v", 0.88f)
        set(value) = sp.edit().putFloat("o2_warn_high_v", value.coerceIn(0.5f, 1.2f)).apply()

    var o2AlertHighV: Float
        get() = sp.getFloat("o2_alert_high_v", 0.95f)
        set(value) = sp.edit().putFloat("o2_alert_high_v", value.coerceIn(0.6f, 1.275f)).apply()

    var o2SpeedMinKmh: Float
        get() = sp.getFloat("o2_speed_min", 20f)
        set(value) = sp.edit().putFloat("o2_speed_min", value.coerceIn(0f, 60f)).apply()

    var o2RpmMin: Float
        get() = sp.getFloat("o2_rpm_min", 800f)
        set(value) = sp.edit().putFloat("o2_rpm_min", value.coerceIn(400f, 3000f)).apply()

    var o2SimVolts: Float
        get() = sp.getFloat("o2_sim_v", 0f)
        set(value) = sp.edit().putFloat("o2_sim_v", value.coerceIn(0f, 1.275f)).apply()

    var o2SimSpeedKmh: Float
        get() = sp.getFloat("o2_sim_speed", 40f)
        set(value) = sp.edit().putFloat("o2_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Absolute load (OBD 0143). */
    var absLoadEnabled: Boolean
        get() = sp.getBoolean("abs_load", true)
        set(value) = sp.edit().putBoolean("abs_load", value).apply()
    var absLoadTts: Boolean
        get() = sp.getBoolean("abs_load_tts", true)
        set(value) = sp.edit().putBoolean("abs_load_tts", value).apply()
    var absLoadWarnPct: Float
        get() = sp.getFloat("abs_load_warn_pct", 85f)
        set(value) = sp.edit().putFloat("abs_load_warn_pct", value.coerceIn(50f, 98f)).apply()
    var absLoadAlertPct: Float
        get() = sp.getFloat("abs_load_alert_pct", 95f)
        set(value) = sp.edit().putFloat("abs_load_alert_pct", value.coerceIn(55f, 100f)).apply()
    var absLoadSpeedMinKmh: Float
        get() = sp.getFloat("abs_load_speed_min", 20f)
        set(value) = sp.edit().putFloat("abs_load_speed_min", value.coerceIn(0f, 60f)).apply()
    var absLoadSimPct: Float
        get() = sp.getFloat("abs_load_sim_pct", 0f)
        set(value) = sp.edit().putFloat("abs_load_sim_pct", value.coerceIn(0f, 100f)).apply()
    var absLoadSimSpeedKmh: Float
        get() = sp.getFloat("abs_load_sim_speed", 40f)
        set(value) = sp.edit().putFloat("abs_load_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Relative throttle (OBD 0145). */
    var relThrEnabled: Boolean
        get() = sp.getBoolean("rel_thr", true)
        set(value) = sp.edit().putBoolean("rel_thr", value).apply()
    var relThrTts: Boolean
        get() = sp.getBoolean("rel_thr_tts", true)
        set(value) = sp.edit().putBoolean("rel_thr_tts", value).apply()
    var relThrWarnPct: Float
        get() = sp.getFloat("rel_thr_warn_pct", 75f)
        set(value) = sp.edit().putFloat("rel_thr_warn_pct", value.coerceIn(40f, 95f)).apply()
    var relThrAlertPct: Float
        get() = sp.getFloat("rel_thr_alert_pct", 90f)
        set(value) = sp.edit().putFloat("rel_thr_alert_pct", value.coerceIn(50f, 100f)).apply()
    var relThrSpeedMinKmh: Float
        get() = sp.getFloat("rel_thr_speed_min", 20f)
        set(value) = sp.edit().putFloat("rel_thr_speed_min", value.coerceIn(0f, 60f)).apply()
    var relThrSimPct: Float
        get() = sp.getFloat("rel_thr_sim_pct", 0f)
        set(value) = sp.edit().putFloat("rel_thr_sim_pct", value.coerceIn(0f, 100f)).apply()
    var relThrSimSpeedKmh: Float
        get() = sp.getFloat("rel_thr_sim_speed", 40f)
        set(value) = sp.edit().putFloat("rel_thr_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Accel pedal D (OBD 0149). */
    var accelPedalEnabled: Boolean
        get() = sp.getBoolean("accel_pedal", true)
        set(value) = sp.edit().putBoolean("accel_pedal", value).apply()
    var accelPedalTts: Boolean
        get() = sp.getBoolean("accel_pedal_tts", true)
        set(value) = sp.edit().putBoolean("accel_pedal_tts", value).apply()
    var accelPedalWarnPct: Float
        get() = sp.getFloat("accel_pedal_warn_pct", 80f)
        set(value) = sp.edit().putFloat("accel_pedal_warn_pct", value.coerceIn(50f, 95f)).apply()
    var accelPedalAlertPct: Float
        get() = sp.getFloat("accel_pedal_alert_pct", 92f)
        set(value) = sp.edit().putFloat("accel_pedal_alert_pct", value.coerceIn(55f, 100f)).apply()
    var accelPedalSpeedMinKmh: Float
        get() = sp.getFloat("accel_pedal_speed_min", 20f)
        set(value) = sp.edit().putFloat("accel_pedal_speed_min", value.coerceIn(0f, 60f)).apply()
    var accelPedalSimPct: Float
        get() = sp.getFloat("accel_pedal_sim_pct", 0f)
        set(value) = sp.edit().putFloat("accel_pedal_sim_pct", value.coerceIn(0f, 100f)).apply()
    var accelPedalSimSpeedKmh: Float
        get() = sp.getFloat("accel_pedal_sim_speed", 40f)
        set(value) = sp.edit().putFloat("accel_pedal_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** O2 B1S2 (OBD 014B). */
    var o2B2Enabled: Boolean
        get() = sp.getBoolean("o2_b2", true)
        set(value) = sp.edit().putBoolean("o2_b2", value).apply()
    var o2B2Tts: Boolean
        get() = sp.getBoolean("o2_b2_tts", true)
        set(value) = sp.edit().putBoolean("o2_b2_tts", value).apply()
    var o2B2WarnLowV: Float
        get() = sp.getFloat("o2_b2_warn_low_v", 0.10f)
        set(value) = sp.edit().putFloat("o2_b2_warn_low_v", value.coerceIn(0.02f, 0.5f)).apply()
    var o2B2AlertLowV: Float
        get() = sp.getFloat("o2_b2_alert_low_v", 0.06f)
        set(value) = sp.edit().putFloat("o2_b2_alert_low_v", value.coerceIn(0.01f, 0.2f)).apply()
    var o2B2WarnHighV: Float
        get() = sp.getFloat("o2_b2_warn_high_v", 0.88f)
        set(value) = sp.edit().putFloat("o2_b2_warn_high_v", value.coerceIn(0.5f, 1.2f)).apply()
    var o2B2AlertHighV: Float
        get() = sp.getFloat("o2_b2_alert_high_v", 0.95f)
        set(value) = sp.edit().putFloat("o2_b2_alert_high_v", value.coerceIn(0.6f, 1.275f)).apply()
    var o2B2SpeedMinKmh: Float
        get() = sp.getFloat("o2_b2_speed_min", 20f)
        set(value) = sp.edit().putFloat("o2_b2_speed_min", value.coerceIn(0f, 60f)).apply()
    var o2B2RpmMin: Float
        get() = sp.getFloat("o2_b2_rpm_min", 800f)
        set(value) = sp.edit().putFloat("o2_b2_rpm_min", value.coerceIn(400f, 3000f)).apply()
    var o2B2SimVolts: Float
        get() = sp.getFloat("o2_b2_sim_v", 0f)
        set(value) = sp.edit().putFloat("o2_b2_sim_v", value.coerceIn(0f, 1.275f)).apply()
    var o2B2SimSpeedKmh: Float
        get() = sp.getFloat("o2_b2_sim_speed", 40f)
        set(value) = sp.edit().putFloat("o2_b2_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** EGR error (OBD 014D). */
    var egrEnabled: Boolean
        get() = sp.getBoolean("egr_error", true)
        set(value) = sp.edit().putBoolean("egr_error", value).apply()
    var egrTts: Boolean
        get() = sp.getBoolean("egr_error_tts", true)
        set(value) = sp.edit().putBoolean("egr_error_tts", value).apply()
    var egrWarnPct: Float
        get() = sp.getFloat("egr_warn_pct", 15f)
        set(value) = sp.edit().putFloat("egr_warn_pct", value.coerceIn(5f, 40f)).apply()
    var egrAlertPct: Float
        get() = sp.getFloat("egr_alert_pct", 25f)
        set(value) = sp.edit().putFloat("egr_alert_pct", value.coerceIn(8f, 50f)).apply()
    var egrSpeedMinKmh: Float
        get() = sp.getFloat("egr_speed_min", 20f)
        set(value) = sp.edit().putFloat("egr_speed_min", value.coerceIn(0f, 60f)).apply()
    var egrSimPct: Float
        get() = sp.getFloat("egr_sim_pct", 0f)
        set(value) = sp.edit().putFloat("egr_sim_pct", value.coerceIn(-50f, 50f)).apply()
    var egrSimSpeedKmh: Float
        get() = sp.getFloat("egr_sim_speed", 40f)
        set(value) = sp.edit().putFloat("egr_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Equivalence ratio / lambda (OBD 0144). */
    var equivEnabled: Boolean
        get() = sp.getBoolean("equiv_ratio", true)
        set(value) = sp.edit().putBoolean("equiv_ratio", value).apply()
    var equivTts: Boolean
        get() = sp.getBoolean("equiv_ratio_tts", true)
        set(value) = sp.edit().putBoolean("equiv_ratio_tts", value).apply()
    var equivWarnLow: Float
        get() = sp.getFloat("equiv_warn_low", 0.88f)
        set(value) = sp.edit().putFloat("equiv_warn_low", value.coerceIn(0.5f, 1.0f)).apply()
    var equivAlertLow: Float
        get() = sp.getFloat("equiv_alert_low", 0.80f)
        set(value) = sp.edit().putFloat("equiv_alert_low", value.coerceIn(0.4f, 0.95f)).apply()
    var equivWarnHigh: Float
        get() = sp.getFloat("equiv_warn_high", 1.12f)
        set(value) = sp.edit().putFloat("equiv_warn_high", value.coerceIn(1.0f, 1.5f)).apply()
    var equivAlertHigh: Float
        get() = sp.getFloat("equiv_alert_high", 1.20f)
        set(value) = sp.edit().putFloat("equiv_alert_high", value.coerceIn(1.05f, 2.0f)).apply()
    var equivSpeedMinKmh: Float
        get() = sp.getFloat("equiv_speed_min", 20f)
        set(value) = sp.edit().putFloat("equiv_speed_min", value.coerceIn(0f, 60f)).apply()
    var equivRpmMin: Float
        get() = sp.getFloat("equiv_rpm_min", 800f)
        set(value) = sp.edit().putFloat("equiv_rpm_min", value.coerceIn(400f, 3000f)).apply()
    var equivSimRatio: Float
        get() = sp.getFloat("equiv_sim_ratio", 0f)
        set(value) = sp.edit().putFloat("equiv_sim_ratio", value.coerceIn(0f, 2.5f)).apply()
    var equivSimSpeedKmh: Float
        get() = sp.getFloat("equiv_sim_speed", 40f)
        set(value) = sp.edit().putFloat("equiv_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Evap purge (OBD 014E). */
    var evapPurgeEnabled: Boolean
        get() = sp.getBoolean("evap_purge", true)
        set(value) = sp.edit().putBoolean("evap_purge", value).apply()
    var evapPurgeTts: Boolean
        get() = sp.getBoolean("evap_purge_tts", true)
        set(value) = sp.edit().putBoolean("evap_purge_tts", value).apply()
    var evapPurgeWarnPct: Float
        get() = sp.getFloat("evap_purge_warn_pct", 55f)
        set(value) = sp.edit().putFloat("evap_purge_warn_pct", value.coerceIn(20f, 90f)).apply()
    var evapPurgeAlertPct: Float
        get() = sp.getFloat("evap_purge_alert_pct", 75f)
        set(value) = sp.edit().putFloat("evap_purge_alert_pct", value.coerceIn(30f, 100f)).apply()
    var evapPurgeSpeedMinKmh: Float
        get() = sp.getFloat("evap_purge_speed_min", 20f)
        set(value) = sp.edit().putFloat("evap_purge_speed_min", value.coerceIn(0f, 60f)).apply()
    var evapPurgeSimPct: Float
        get() = sp.getFloat("evap_purge_sim_pct", 0f)
        set(value) = sp.edit().putFloat("evap_purge_sim_pct", value.coerceIn(0f, 100f)).apply()
    var evapPurgeSimSpeedKmh: Float
        get() = sp.getFloat("evap_purge_sim_speed", 40f)
        set(value) = sp.edit().putFloat("evap_purge_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Ethanol % (OBD 0152). */
    var ethanolEnabled: Boolean
        get() = sp.getBoolean("ethanol_pct", true)
        set(value) = sp.edit().putBoolean("ethanol_pct", value).apply()
    var ethanolTts: Boolean
        get() = sp.getBoolean("ethanol_pct_tts", true)
        set(value) = sp.edit().putBoolean("ethanol_pct_tts", value).apply()
    var ethanolWarnPct: Float
        get() = sp.getFloat("ethanol_warn_pct", 70f)
        set(value) = sp.edit().putFloat("ethanol_warn_pct", value.coerceIn(30f, 95f)).apply()
    var ethanolAlertPct: Float
        get() = sp.getFloat("ethanol_alert_pct", 85f)
        set(value) = sp.edit().putFloat("ethanol_alert_pct", value.coerceIn(40f, 100f)).apply()
    var ethanolSpeedMinKmh: Float
        get() = sp.getFloat("ethanol_speed_min", 20f)
        set(value) = sp.edit().putFloat("ethanol_speed_min", value.coerceIn(0f, 60f)).apply()
    var ethanolSimPct: Float
        get() = sp.getFloat("ethanol_sim_pct", 0f)
        set(value) = sp.edit().putFloat("ethanol_sim_pct", value.coerceIn(0f, 100f)).apply()
    var ethanolSimSpeedKmh: Float
        get() = sp.getFloat("ethanol_sim_speed", 40f)
        set(value) = sp.edit().putFloat("ethanol_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Evap vapor Pa (OBD 0153). */
    var evapVaporEnabled: Boolean
        get() = sp.getBoolean("evap_vapor", true)
        set(value) = sp.edit().putBoolean("evap_vapor", value).apply()
    var evapVaporTts: Boolean
        get() = sp.getBoolean("evap_vapor_tts", true)
        set(value) = sp.edit().putBoolean("evap_vapor_tts", value).apply()
    var evapVaporWarnPa: Float
        get() = sp.getFloat("evap_vapor_warn_pa", 5000f)
        set(value) = sp.edit().putFloat("evap_vapor_warn_pa", value.coerceIn(1000f, 15000f)).apply()
    var evapVaporAlertPa: Float
        get() = sp.getFloat("evap_vapor_alert_pa", 8000f)
        set(value) = sp.edit().putFloat("evap_vapor_alert_pa", value.coerceIn(2000f, 20000f)).apply()
    var evapVaporSpeedMinKmh: Float
        get() = sp.getFloat("evap_vapor_speed_min", 20f)
        set(value) = sp.edit().putFloat("evap_vapor_speed_min", value.coerceIn(0f, 60f)).apply()
    var evapVaporSimPa: Float
        get() = sp.getFloat("evap_vapor_sim_pa", 0f)
        set(value) = sp.edit().putFloat("evap_vapor_sim_pa", value.coerceIn(-20000f, 20000f)).apply()
    var evapVaporSimSpeedKmh: Float
        get() = sp.getFloat("evap_vapor_sim_speed", 40f)
        set(value) = sp.edit().putFloat("evap_vapor_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Fuel rail abs kPa (OBD 0159). */
    var railAbsEnabled: Boolean
        get() = sp.getBoolean("rail_abs", true)
        set(value) = sp.edit().putBoolean("rail_abs", value).apply()
    var railAbsTts: Boolean
        get() = sp.getBoolean("rail_abs_tts", true)
        set(value) = sp.edit().putBoolean("rail_abs_tts", value).apply()
    var railAbsWarnKpa: Float
        get() = sp.getFloat("rail_abs_warn_kpa", 8000f)
        set(value) = sp.edit().putFloat("rail_abs_warn_kpa", value.coerceIn(2000f, 20000f)).apply()
    var railAbsAlertKpa: Float
        get() = sp.getFloat("rail_abs_alert_kpa", 6000f)
        set(value) = sp.edit().putFloat("rail_abs_alert_kpa", value.coerceIn(1000f, 15000f)).apply()
    var railAbsSpeedMinKmh: Float
        get() = sp.getFloat("rail_abs_speed_min", 20f)
        set(value) = sp.edit().putFloat("rail_abs_speed_min", value.coerceIn(0f, 60f)).apply()
    var railAbsSimKpa: Float
        get() = sp.getFloat("rail_abs_sim_kpa", 0f)
        set(value) = sp.edit().putFloat("rail_abs_sim_kpa", value.coerceIn(0f, 655350f)).apply()
    var railAbsSimSpeedKmh: Float
        get() = sp.getFloat("rail_abs_sim_speed", 40f)
        set(value) = sp.edit().putFloat("rail_abs_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Commanded EGR % (OBD 014C). */
    var egrCmdEnabled: Boolean
        get() = sp.getBoolean("egr_cmd", true)
        set(value) = sp.edit().putBoolean("egr_cmd", value).apply()
    var egrCmdTts: Boolean
        get() = sp.getBoolean("egr_cmd_tts", true)
        set(value) = sp.edit().putBoolean("egr_cmd_tts", value).apply()
    var egrCmdWarnPct: Float
        get() = sp.getFloat("egr_cmd_warn_pct", 50f)
        set(value) = sp.edit().putFloat("egr_cmd_warn_pct", value.coerceIn(20f, 90f)).apply()
    var egrCmdAlertPct: Float
        get() = sp.getFloat("egr_cmd_alert_pct", 70f)
        set(value) = sp.edit().putFloat("egr_cmd_alert_pct", value.coerceIn(30f, 100f)).apply()
    var egrCmdSpeedMinKmh: Float
        get() = sp.getFloat("egr_cmd_speed_min", 20f)
        set(value) = sp.edit().putFloat("egr_cmd_speed_min", value.coerceIn(0f, 60f)).apply()
    var egrCmdSimPct: Float
        get() = sp.getFloat("egr_cmd_sim_pct", 0f)
        set(value) = sp.edit().putFloat("egr_cmd_sim_pct", value.coerceIn(0f, 100f)).apply()

    /** Relative accel pedal % (OBD 015A). */
    var relApedEnabled: Boolean
        get() = sp.getBoolean("rel_aped", true)
        set(value) = sp.edit().putBoolean("rel_aped", value).apply()
    var relApedTts: Boolean
        get() = sp.getBoolean("rel_aped_tts", true)
        set(value) = sp.edit().putBoolean("rel_aped_tts", value).apply()
    var relApedWarnPct: Float
        get() = sp.getFloat("rel_aped_warn_pct", 78f)
        set(value) = sp.edit().putFloat("rel_aped_warn_pct", value.coerceIn(50f, 95f)).apply()
    var relApedAlertPct: Float
        get() = sp.getFloat("rel_aped_alert_pct", 90f)
        set(value) = sp.edit().putFloat("rel_aped_alert_pct", value.coerceIn(55f, 100f)).apply()
    var relApedSpeedMinKmh: Float
        get() = sp.getFloat("rel_aped_speed_min", 20f)
        set(value) = sp.edit().putFloat("rel_aped_speed_min", value.coerceIn(0f, 60f)).apply()
    var relApedSimPct: Float
        get() = sp.getFloat("rel_aped_sim_pct", 0f)
        set(value) = sp.edit().putFloat("rel_aped_sim_pct", value.coerceIn(0f, 100f)).apply()

    /** Driver demand torque % (OBD 0161). */
    var drvTorqueEnabled: Boolean
        get() = sp.getBoolean("drv_torque", true)
        set(value) = sp.edit().putBoolean("drv_torque", value).apply()
    var drvTorqueTts: Boolean
        get() = sp.getBoolean("drv_torque_tts", true)
        set(value) = sp.edit().putBoolean("drv_torque_tts", value).apply()
    var drvTorqueWarnPct: Float
        get() = sp.getFloat("drv_torque_warn_pct", 40f)
        set(value) = sp.edit().putFloat("drv_torque_warn_pct", value.coerceIn(15f, 100f)).apply()
    var drvTorqueAlertPct: Float
        get() = sp.getFloat("drv_torque_alert_pct", 55f)
        set(value) = sp.edit().putFloat("drv_torque_alert_pct", value.coerceIn(20f, 125f)).apply()
    var drvTorqueSpeedMinKmh: Float
        get() = sp.getFloat("drv_torque_speed_min", 20f)
        set(value) = sp.edit().putFloat("drv_torque_speed_min", value.coerceIn(0f, 60f)).apply()
    var drvTorqueSimPct: Float
        get() = sp.getFloat("drv_torque_sim_pct", 0f)
        set(value) = sp.edit().putFloat("drv_torque_sim_pct", value.coerceIn(-125f, 125f)).apply()

    /** Actual engine torque % (OBD 0162). */
    var actTorqueEnabled: Boolean
        get() = sp.getBoolean("act_torque", true)
        set(value) = sp.edit().putBoolean("act_torque", value).apply()
    var actTorqueTts: Boolean
        get() = sp.getBoolean("act_torque_tts", true)
        set(value) = sp.edit().putBoolean("act_torque_tts", value).apply()
    var actTorqueWarnPct: Float
        get() = sp.getFloat("act_torque_warn_pct", 40f)
        set(value) = sp.edit().putFloat("act_torque_warn_pct", value.coerceIn(15f, 100f)).apply()
    var actTorqueAlertPct: Float
        get() = sp.getFloat("act_torque_alert_pct", 55f)
        set(value) = sp.edit().putFloat("act_torque_alert_pct", value.coerceIn(20f, 125f)).apply()
    var actTorqueSpeedMinKmh: Float
        get() = sp.getFloat("act_torque_speed_min", 20f)
        set(value) = sp.edit().putFloat("act_torque_speed_min", value.coerceIn(0f, 60f)).apply()
    var actTorqueSimPct: Float
        get() = sp.getFloat("act_torque_sim_pct", 0f)
        set(value) = sp.edit().putFloat("act_torque_sim_pct", value.coerceIn(-125f, 125f)).apply()

    /** Catalyst temp bank 2 °C (OBD 0170). */
    var catB2Enabled: Boolean
        get() = sp.getBoolean("cat_b2", true)
        set(value) = sp.edit().putBoolean("cat_b2", value).apply()
    var catB2Tts: Boolean
        get() = sp.getBoolean("cat_b2_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2_tts", value).apply()
    var catB2WarnC: Float
        get() = sp.getFloat("cat_b2_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2AlertC: Float
        get() = sp.getFloat("cat_b2_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2SimC: Float
        get() = sp.getFloat("cat_b2_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B1S2 °C (OBD 0171). */
    var catB1s2Enabled: Boolean
        get() = sp.getBoolean("cat_b1s2", true)
        set(value) = sp.edit().putBoolean("cat_b1s2", value).apply()
    var catB1s2Tts: Boolean
        get() = sp.getBoolean("cat_b1s2_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s2_tts", value).apply()
    var catB1s2WarnC: Float
        get() = sp.getFloat("cat_b1s2_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s2_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s2AlertC: Float
        get() = sp.getFloat("cat_b1s2_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s2_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s2SimC: Float
        get() = sp.getFloat("cat_b1s2_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s2_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S2 °C (OBD 0172). */
    var catB2s2Enabled: Boolean
        get() = sp.getBoolean("cat_b2s2", true)
        set(value) = sp.edit().putBoolean("cat_b2s2", value).apply()
    var catB2s2Tts: Boolean
        get() = sp.getBoolean("cat_b2s2_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s2_tts", value).apply()
    var catB2s2WarnC: Float
        get() = sp.getFloat("cat_b2s2_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s2_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s2AlertC: Float
        get() = sp.getFloat("cat_b2s2_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s2_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s2SimC: Float
        get() = sp.getFloat("cat_b2s2_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s2_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B1S3 °C (OBD 0173). */
    var catB1s3Enabled: Boolean
        get() = sp.getBoolean("cat_b1s3", true)
        set(value) = sp.edit().putBoolean("cat_b1s3", value).apply()
    var catB1s3Tts: Boolean
        get() = sp.getBoolean("cat_b1s3_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s3_tts", value).apply()
    var catB1s3WarnC: Float
        get() = sp.getFloat("cat_b1s3_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s3_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s3AlertC: Float
        get() = sp.getFloat("cat_b1s3_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s3_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s3SimC: Float
        get() = sp.getFloat("cat_b1s3_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s3_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S3 °C (OBD 0174). */
    var catB2s3Enabled: Boolean
        get() = sp.getBoolean("cat_b2s3", true)
        set(value) = sp.edit().putBoolean("cat_b2s3", value).apply()
    var catB2s3Tts: Boolean
        get() = sp.getBoolean("cat_b2s3_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s3_tts", value).apply()
    var catB2s3WarnC: Float
        get() = sp.getFloat("cat_b2s3_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s3_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s3AlertC: Float
        get() = sp.getFloat("cat_b2s3_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s3_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s3SimC: Float
        get() = sp.getFloat("cat_b2s3_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s3_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B1S4 °C (OBD 0175). */
    var catB1s4Enabled: Boolean
        get() = sp.getBoolean("cat_b1s4", true)
        set(value) = sp.edit().putBoolean("cat_b1s4", value).apply()
    var catB1s4Tts: Boolean
        get() = sp.getBoolean("cat_b1s4_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s4_tts", value).apply()
    var catB1s4WarnC: Float
        get() = sp.getFloat("cat_b1s4_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s4_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s4AlertC: Float
        get() = sp.getFloat("cat_b1s4_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s4_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s4SimC: Float
        get() = sp.getFloat("cat_b1s4_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s4_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S4 °C (OBD 0176). */
    var catB2s4Enabled: Boolean
        get() = sp.getBoolean("cat_b2s4", true)
        set(value) = sp.edit().putBoolean("cat_b2s4", value).apply()
    var catB2s4Tts: Boolean
        get() = sp.getBoolean("cat_b2s4_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s4_tts", value).apply()
    var catB2s4WarnC: Float
        get() = sp.getFloat("cat_b2s4_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s4_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s4AlertC: Float
        get() = sp.getFloat("cat_b2s4_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s4_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s4SimC: Float
        get() = sp.getFloat("cat_b2s4_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s4_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** STFT secondary O2 B1 (OBD 0155). */
    var stft2B1Enabled: Boolean
        get() = sp.getBoolean("stft2_b1", true)
        set(value) = sp.edit().putBoolean("stft2_b1", value).apply()
    var stft2B1Tts: Boolean
        get() = sp.getBoolean("stft2_b1_tts", true)
        set(value) = sp.edit().putBoolean("stft2_b1_tts", value).apply()
    var stft2B1WarnPct: Float
        get() = sp.getFloat("stft2_b1_warn_pct", 12f)
        set(value) = sp.edit().putFloat("stft2_b1_warn_pct", value.coerceIn(5f, 40f)).apply()
    var stft2B1AlertPct: Float
        get() = sp.getFloat("stft2_b1_alert_pct", 20f)
        set(value) = sp.edit().putFloat("stft2_b1_alert_pct", value.coerceIn(8f, 50f)).apply()
    var stft2B1SpeedMinKmh: Float
        get() = sp.getFloat("stft2_b1_speed_min", 20f)
        set(value) = sp.edit().putFloat("stft2_b1_speed_min", value.coerceIn(0f, 60f)).apply()
    var stft2B1SimPct: Float
        get() = sp.getFloat("stft2_b1_sim_pct", 0f)
        set(value) = sp.edit().putFloat("stft2_b1_sim_pct", value.coerceIn(-50f, 50f)).apply()
    var stft2B1SimSpeedKmh: Float
        get() = sp.getFloat("stft2_b1_sim_speed", 40f)
        set(value) = sp.edit().putFloat("stft2_b1_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** LTFT secondary O2 B1 (OBD 0156). */
    var ltft2B1Enabled: Boolean
        get() = sp.getBoolean("ltft2_b1", true)
        set(value) = sp.edit().putBoolean("ltft2_b1", value).apply()
    var ltft2B1Tts: Boolean
        get() = sp.getBoolean("ltft2_b1_tts", true)
        set(value) = sp.edit().putBoolean("ltft2_b1_tts", value).apply()
    var ltft2B1WarnPct: Float
        get() = sp.getFloat("ltft2_b1_warn_pct", 12f)
        set(value) = sp.edit().putFloat("ltft2_b1_warn_pct", value.coerceIn(5f, 40f)).apply()
    var ltft2B1AlertPct: Float
        get() = sp.getFloat("ltft2_b1_alert_pct", 20f)
        set(value) = sp.edit().putFloat("ltft2_b1_alert_pct", value.coerceIn(8f, 50f)).apply()
    var ltft2B1SpeedMinKmh: Float
        get() = sp.getFloat("ltft2_b1_speed_min", 20f)
        set(value) = sp.edit().putFloat("ltft2_b1_speed_min", value.coerceIn(0f, 60f)).apply()
    var ltft2B1SimPct: Float
        get() = sp.getFloat("ltft2_b1_sim_pct", 0f)
        set(value) = sp.edit().putFloat("ltft2_b1_sim_pct", value.coerceIn(-50f, 50f)).apply()
    var ltft2B1SimSpeedKmh: Float
        get() = sp.getFloat("ltft2_b1_sim_speed", 40f)
        set(value) = sp.edit().putFloat("ltft2_b1_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** STFT secondary O2 B2 (OBD 0157). */
    var stft2B2Enabled: Boolean
        get() = sp.getBoolean("stft2_b2", true)
        set(value) = sp.edit().putBoolean("stft2_b2", value).apply()
    var stft2B2Tts: Boolean
        get() = sp.getBoolean("stft2_b2_tts", true)
        set(value) = sp.edit().putBoolean("stft2_b2_tts", value).apply()
    var stft2B2WarnPct: Float
        get() = sp.getFloat("stft2_b2_warn_pct", 12f)
        set(value) = sp.edit().putFloat("stft2_b2_warn_pct", value.coerceIn(5f, 40f)).apply()
    var stft2B2AlertPct: Float
        get() = sp.getFloat("stft2_b2_alert_pct", 20f)
        set(value) = sp.edit().putFloat("stft2_b2_alert_pct", value.coerceIn(8f, 50f)).apply()
    var stft2B2SpeedMinKmh: Float
        get() = sp.getFloat("stft2_b2_speed_min", 20f)
        set(value) = sp.edit().putFloat("stft2_b2_speed_min", value.coerceIn(0f, 60f)).apply()
    var stft2B2SimPct: Float
        get() = sp.getFloat("stft2_b2_sim_pct", 0f)
        set(value) = sp.edit().putFloat("stft2_b2_sim_pct", value.coerceIn(-50f, 50f)).apply()
    var stft2B2SimSpeedKmh: Float
        get() = sp.getFloat("stft2_b2_sim_speed", 40f)
        set(value) = sp.edit().putFloat("stft2_b2_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** LTFT secondary O2 B2 (OBD 0158). */
    var ltft2B2Enabled: Boolean
        get() = sp.getBoolean("ltft2_b2", true)
        set(value) = sp.edit().putBoolean("ltft2_b2", value).apply()
    var ltft2B2Tts: Boolean
        get() = sp.getBoolean("ltft2_b2_tts", true)
        set(value) = sp.edit().putBoolean("ltft2_b2_tts", value).apply()
    var ltft2B2WarnPct: Float
        get() = sp.getFloat("ltft2_b2_warn_pct", 12f)
        set(value) = sp.edit().putFloat("ltft2_b2_warn_pct", value.coerceIn(5f, 40f)).apply()
    var ltft2B2AlertPct: Float
        get() = sp.getFloat("ltft2_b2_alert_pct", 20f)
        set(value) = sp.edit().putFloat("ltft2_b2_alert_pct", value.coerceIn(8f, 50f)).apply()
    var ltft2B2SpeedMinKmh: Float
        get() = sp.getFloat("ltft2_b2_speed_min", 20f)
        set(value) = sp.edit().putFloat("ltft2_b2_speed_min", value.coerceIn(0f, 60f)).apply()
    var ltft2B2SimPct: Float
        get() = sp.getFloat("ltft2_b2_sim_pct", 0f)
        set(value) = sp.edit().putFloat("ltft2_b2_sim_pct", value.coerceIn(-50f, 50f)).apply()
    var ltft2B2SimSpeedKmh: Float
        get() = sp.getFloat("ltft2_b2_sim_speed", 40f)
        set(value) = sp.edit().putFloat("ltft2_b2_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Catalyst temp B1S5 °C (OBD 0177). */
    var catB1s5Enabled: Boolean
        get() = sp.getBoolean("cat_b1s5", true)
        set(value) = sp.edit().putBoolean("cat_b1s5", value).apply()
    var catB1s5Tts: Boolean
        get() = sp.getBoolean("cat_b1s5_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s5_tts", value).apply()
    var catB1s5WarnC: Float
        get() = sp.getFloat("cat_b1s5_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s5_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s5AlertC: Float
        get() = sp.getFloat("cat_b1s5_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s5_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s5SimC: Float
        get() = sp.getFloat("cat_b1s5_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s5_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S5 °C (OBD 0178). */
    var catB2s5Enabled: Boolean
        get() = sp.getBoolean("cat_b2s5", true)
        set(value) = sp.edit().putBoolean("cat_b2s5", value).apply()
    var catB2s5Tts: Boolean
        get() = sp.getBoolean("cat_b2s5_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s5_tts", value).apply()
    var catB2s5WarnC: Float
        get() = sp.getFloat("cat_b2s5_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s5_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s5AlertC: Float
        get() = sp.getFloat("cat_b2s5_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s5_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s5SimC: Float
        get() = sp.getFloat("cat_b2s5_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s5_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Fuel injection timing ° (OBD 015D). */
    var injectEnabled: Boolean
        get() = sp.getBoolean("fuel_inject", true)
        set(value) = sp.edit().putBoolean("fuel_inject", value).apply()
    var injectTts: Boolean
        get() = sp.getBoolean("fuel_inject_tts", true)
        set(value) = sp.edit().putBoolean("fuel_inject_tts", value).apply()
    var injectWarnDeg: Float
        get() = sp.getFloat("inject_warn_deg", 28f)
        set(value) = sp.edit().putFloat("inject_warn_deg", value.coerceIn(10f, 55f)).apply()
    var injectAlertDeg: Float
        get() = sp.getFloat("inject_alert_deg", 40f)
        set(value) = sp.edit().putFloat("inject_alert_deg", value.coerceIn(15f, 64f)).apply()
    var injectSpeedMinKmh: Float
        get() = sp.getFloat("inject_speed_min", 20f)
        set(value) = sp.edit().putFloat("inject_speed_min", value.coerceIn(0f, 60f)).apply()
    var injectSimDeg: Float
        get() = sp.getFloat("inject_sim_deg", 0f)
        set(value) = sp.edit().putFloat("inject_sim_deg", value.coerceIn(-64f, 64f)).apply()

    /** Hybrid pack life % (OBD 015B). */
    var hybridEnabled: Boolean
        get() = sp.getBoolean("hybrid_batt", true)
        set(value) = sp.edit().putBoolean("hybrid_batt", value).apply()
    var hybridTts: Boolean
        get() = sp.getBoolean("hybrid_batt_tts", true)
        set(value) = sp.edit().putBoolean("hybrid_batt_tts", value).apply()
    var hybridWarnPct: Float
        get() = sp.getFloat("hybrid_warn_pct", 30f)
        set(value) = sp.edit().putFloat("hybrid_warn_pct", value.coerceIn(10f, 60f)).apply()
    var hybridAlertPct: Float
        get() = sp.getFloat("hybrid_alert_pct", 15f)
        set(value) = sp.edit().putFloat("hybrid_alert_pct", value.coerceIn(5f, 40f)).apply()
    var hybridSpeedMinKmh: Float
        get() = sp.getFloat("hybrid_speed_min", 0f)
        set(value) = sp.edit().putFloat("hybrid_speed_min", value.coerceIn(0f, 60f)).apply()
    var hybridSimPct: Float
        get() = sp.getFloat("hybrid_sim_pct", 0f)
        set(value) = sp.edit().putFloat("hybrid_sim_pct", value.coerceIn(0f, 100f)).apply()

    /** Engine reference torque Nm (OBD 0163). */
    var refTorqueEnabled: Boolean
        get() = sp.getBoolean("ref_torque", true)
        set(value) = sp.edit().putBoolean("ref_torque", value).apply()
    var refTorqueTts: Boolean
        get() = sp.getBoolean("ref_torque_tts", true)
        set(value) = sp.edit().putBoolean("ref_torque_tts", value).apply()
    var refTorqueWarnLowNm: Float
        get() = sp.getFloat("ref_torque_warn_low", 100f)
        set(value) = sp.edit().putFloat("ref_torque_warn_low", value.coerceIn(50f, 300f)).apply()
    var refTorqueAlertLowNm: Float
        get() = sp.getFloat("ref_torque_alert_low", 80f)
        set(value) = sp.edit().putFloat("ref_torque_alert_low", value.coerceIn(40f, 250f)).apply()
    var refTorqueWarnHighNm: Float
        get() = sp.getFloat("ref_torque_warn_high", 450f)
        set(value) = sp.edit().putFloat("ref_torque_warn_high", value.coerceIn(300f, 800f)).apply()
    var refTorqueAlertHighNm: Float
        get() = sp.getFloat("ref_torque_alert_high", 520f)
        set(value) = sp.edit().putFloat("ref_torque_alert_high", value.coerceIn(400f, 1200f)).apply()
    var refTorqueSimNm: Float
        get() = sp.getFloat("ref_torque_sim_nm", 0f)
        set(value) = sp.edit().putFloat("ref_torque_sim_nm", value.coerceIn(0f, 2000f)).apply()

    /** Catalyst temp B1S6 °C (OBD 0179). */
    var catB1s6Enabled: Boolean
        get() = sp.getBoolean("cat_b1s6", true)
        set(value) = sp.edit().putBoolean("cat_b1s6", value).apply()
    var catB1s6Tts: Boolean
        get() = sp.getBoolean("cat_b1s6_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s6_tts", value).apply()
    var catB1s6WarnC: Float
        get() = sp.getFloat("cat_b1s6_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s6_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s6AlertC: Float
        get() = sp.getFloat("cat_b1s6_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s6_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s6SimC: Float
        get() = sp.getFloat("cat_b1s6_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s6_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S6 °C (OBD 017A). */
    var catB2s6Enabled: Boolean
        get() = sp.getBoolean("cat_b2s6", true)
        set(value) = sp.edit().putBoolean("cat_b2s6", value).apply()
    var catB2s6Tts: Boolean
        get() = sp.getBoolean("cat_b2s6_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s6_tts", value).apply()
    var catB2s6WarnC: Float
        get() = sp.getFloat("cat_b2s6_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s6_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s6AlertC: Float
        get() = sp.getFloat("cat_b2s6_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s6_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s6SimC: Float
        get() = sp.getFloat("cat_b2s6_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s6_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Throttle B % (OBD 0147). */
    var thrBEnabled: Boolean
        get() = sp.getBoolean("thr_b", true)
        set(value) = sp.edit().putBoolean("thr_b", value).apply()
    var thrBTts: Boolean
        get() = sp.getBoolean("thr_b_tts", true)
        set(value) = sp.edit().putBoolean("thr_b_tts", value).apply()
    var thrBWarnPct: Float
        get() = sp.getFloat("thr_b_warn_pct", 75f)
        set(value) = sp.edit().putFloat("thr_b_warn_pct", value.coerceIn(40f, 95f)).apply()
    var thrBAlertPct: Float
        get() = sp.getFloat("thr_b_alert_pct", 90f)
        set(value) = sp.edit().putFloat("thr_b_alert_pct", value.coerceIn(50f, 100f)).apply()
    var thrBSpeedMinKmh: Float
        get() = sp.getFloat("thr_b_speed_min", 20f)
        set(value) = sp.edit().putFloat("thr_b_speed_min", value.coerceIn(0f, 60f)).apply()
    var thrBSimPct: Float
        get() = sp.getFloat("thr_b_sim_pct", 0f)
        set(value) = sp.edit().putFloat("thr_b_sim_pct", value.coerceIn(0f, 100f)).apply()
    var thrBSimSpeedKmh: Float
        get() = sp.getFloat("thr_b_sim_speed", 40f)
        set(value) = sp.edit().putFloat("thr_b_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Throttle C % (OBD 0148). */
    var thrCEnabled: Boolean
        get() = sp.getBoolean("thr_c", true)
        set(value) = sp.edit().putBoolean("thr_c", value).apply()
    var thrCTts: Boolean
        get() = sp.getBoolean("thr_c_tts", true)
        set(value) = sp.edit().putBoolean("thr_c_tts", value).apply()
    var thrCWarnPct: Float
        get() = sp.getFloat("thr_c_warn_pct", 75f)
        set(value) = sp.edit().putFloat("thr_c_warn_pct", value.coerceIn(40f, 95f)).apply()
    var thrCAlertPct: Float
        get() = sp.getFloat("thr_c_alert_pct", 90f)
        set(value) = sp.edit().putFloat("thr_c_alert_pct", value.coerceIn(50f, 100f)).apply()
    var thrCSpeedMinKmh: Float
        get() = sp.getFloat("thr_c_speed_min", 20f)
        set(value) = sp.edit().putFloat("thr_c_speed_min", value.coerceIn(0f, 60f)).apply()
    var thrCSimPct: Float
        get() = sp.getFloat("thr_c_sim_pct", 0f)
        set(value) = sp.edit().putFloat("thr_c_sim_pct", value.coerceIn(0f, 100f)).apply()
    var thrCSimSpeedKmh: Float
        get() = sp.getFloat("thr_c_sim_speed", 40f)
        set(value) = sp.edit().putFloat("thr_c_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Time with MIL on min (OBD 0154). */
    var milTimeEnabled: Boolean
        get() = sp.getBoolean("mil_time", true)
        set(value) = sp.edit().putBoolean("mil_time", value).apply()
    var milTimeTts: Boolean
        get() = sp.getBoolean("mil_time_tts", true)
        set(value) = sp.edit().putBoolean("mil_time_tts", value).apply()
    var milTimeWarnMin: Int
        get() = sp.getInt("mil_time_warn_min", 30)
        set(value) = sp.edit().putInt("mil_time_warn_min", value.coerceIn(5, 180)).apply()
    var milTimeAlertMin: Int
        get() = sp.getInt("mil_time_alert_min", 60)
        set(value) = sp.edit().putInt("mil_time_alert_min", value.coerceIn(10, 600)).apply()
    var milTimeSimMin: Int
        get() = sp.getInt("mil_time_sim_min", 0)
        set(value) = sp.edit().putInt("mil_time_sim_min", value.coerceIn(0, 600)).apply()

    /** Catalyst temp B1S7 °C (OBD 017B). */
    var catB1s7Enabled: Boolean
        get() = sp.getBoolean("cat_b1s7", true)
        set(value) = sp.edit().putBoolean("cat_b1s7", value).apply()
    var catB1s7Tts: Boolean
        get() = sp.getBoolean("cat_b1s7_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s7_tts", value).apply()
    var catB1s7WarnC: Float
        get() = sp.getFloat("cat_b1s7_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s7_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s7AlertC: Float
        get() = sp.getFloat("cat_b1s7_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s7_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s7SimC: Float
        get() = sp.getFloat("cat_b1s7_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s7_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S7 °C (OBD 017C). */
    var catB2s7Enabled: Boolean
        get() = sp.getBoolean("cat_b2s7", true)
        set(value) = sp.edit().putBoolean("cat_b2s7", value).apply()
    var catB2s7Tts: Boolean
        get() = sp.getBoolean("cat_b2s7_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s7_tts", value).apply()
    var catB2s7WarnC: Float
        get() = sp.getFloat("cat_b2s7_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s7_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s7AlertC: Float
        get() = sp.getFloat("cat_b2s7_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s7_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s7SimC: Float
        get() = sp.getFloat("cat_b2s7_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s7_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Fuel type (OBD 0151). */
    var fuelTypeEnabled: Boolean
        get() = sp.getBoolean("fuel_type", true)
        set(value) = sp.edit().putBoolean("fuel_type", value).apply()
    var fuelTypeTts: Boolean
        get() = sp.getBoolean("fuel_type_tts", true)
        set(value) = sp.edit().putBoolean("fuel_type_tts", value).apply()
    var fuelTypeExpected: Int
        get() = sp.getInt("fuel_type_expected", 1)
        set(value) = sp.edit().putInt("fuel_type_expected", value.coerceIn(1, 20)).apply()
    var fuelTypeSpeedMinKmh: Float
        get() = sp.getFloat("fuel_type_speed_min", 5f)
        set(value) = sp.edit().putFloat("fuel_type_speed_min", value.coerceIn(0f, 60f)).apply()
    var fuelTypeSimCode: Int
        get() = sp.getInt("fuel_type_sim_code", 0)
        set(value) = sp.edit().putInt("fuel_type_sim_code", value.coerceIn(0, 255)).apply()

    /** Max equiv ratio (OBD 014F). */
    var maxEquivEnabled: Boolean
        get() = sp.getBoolean("max_equiv", true)
        set(value) = sp.edit().putBoolean("max_equiv", value).apply()
    var maxEquivTts: Boolean
        get() = sp.getBoolean("max_equiv_tts", true)
        set(value) = sp.edit().putBoolean("max_equiv_tts", value).apply()
    var maxEquivWarnLow: Float
        get() = sp.getFloat("max_equiv_warn_low", 0.88f)
        set(value) = sp.edit().putFloat("max_equiv_warn_low", value.coerceIn(0.5f, 1.0f)).apply()
    var maxEquivAlertLow: Float
        get() = sp.getFloat("max_equiv_alert_low", 0.82f)
        set(value) = sp.edit().putFloat("max_equiv_alert_low", value.coerceIn(0.4f, 0.95f)).apply()
    var maxEquivWarnHigh: Float
        get() = sp.getFloat("max_equiv_warn_high", 1.18f)
        set(value) = sp.edit().putFloat("max_equiv_warn_high", value.coerceIn(1.0f, 1.5f)).apply()
    var maxEquivAlertHigh: Float
        get() = sp.getFloat("max_equiv_alert_high", 1.24f)
        set(value) = sp.edit().putFloat("max_equiv_alert_high", value.coerceIn(1.05f, 2.0f)).apply()
    var maxEquivSimRatio: Float
        get() = sp.getFloat("max_equiv_sim_ratio", 0f)
        set(value) = sp.edit().putFloat("max_equiv_sim_ratio", value.coerceIn(0f, 2.5f)).apply()

    /** Max MAF g/s (OBD 0150). */
    var maxMafEnabled: Boolean
        get() = sp.getBoolean("max_maf", true)
        set(value) = sp.edit().putBoolean("max_maf", value).apply()
    var maxMafTts: Boolean
        get() = sp.getBoolean("max_maf_tts", true)
        set(value) = sp.edit().putBoolean("max_maf_tts", value).apply()
    var maxMafWarnLowGps: Float
        get() = sp.getFloat("max_maf_warn_low", 25f)
        set(value) = sp.edit().putFloat("max_maf_warn_low", value.coerceIn(5f, 100f)).apply()
    var maxMafAlertLowGps: Float
        get() = sp.getFloat("max_maf_alert_low", 15f)
        set(value) = sp.edit().putFloat("max_maf_alert_low", value.coerceIn(5f, 80f)).apply()
    var maxMafSimGps: Float
        get() = sp.getFloat("max_maf_sim_gps", 0f)
        set(value) = sp.edit().putFloat("max_maf_sim_gps", value.coerceIn(0f, 400f)).apply()

    /** Catalyst temp B1S8 °C (OBD 017D). */
    var catB1s8Enabled: Boolean
        get() = sp.getBoolean("cat_b1s8", true)
        set(value) = sp.edit().putBoolean("cat_b1s8", value).apply()
    var catB1s8Tts: Boolean
        get() = sp.getBoolean("cat_b1s8_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s8_tts", value).apply()
    var catB1s8WarnC: Float
        get() = sp.getFloat("cat_b1s8_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s8_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s8AlertC: Float
        get() = sp.getFloat("cat_b1s8_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s8_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s8SimC: Float
        get() = sp.getFloat("cat_b1s8_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s8_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S8 °C (OBD 017E). */
    var catB2s8Enabled: Boolean
        get() = sp.getBoolean("cat_b2s8", true)
        set(value) = sp.edit().putBoolean("cat_b2s8", value).apply()
    var catB2s8Tts: Boolean
        get() = sp.getBoolean("cat_b2s8_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s8_tts", value).apply()
    var catB2s8WarnC: Float
        get() = sp.getFloat("cat_b2s8_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s8_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s8AlertC: Float
        get() = sp.getFloat("cat_b2s8_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s8_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s8SimC: Float
        get() = sp.getFloat("cat_b2s8_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s8_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Max available torque % (OBD 0164). */
    var maxAvailTorqueEnabled: Boolean
        get() = sp.getBoolean("max_avail_torque", true)
        set(value) = sp.edit().putBoolean("max_avail_torque", value).apply()
    var maxAvailTorqueTts: Boolean
        get() = sp.getBoolean("max_avail_torque_tts", true)
        set(value) = sp.edit().putBoolean("max_avail_torque_tts", value).apply()
    var maxAvailTorqueWarnLow: Float
        get() = sp.getFloat("max_avail_torque_warn_low", 30f)
        set(value) = sp.edit().putFloat("max_avail_torque_warn_low", value.coerceIn(10f, 80f)).apply()
    var maxAvailTorqueAlertLow: Float
        get() = sp.getFloat("max_avail_torque_alert_low", 20f)
        set(value) = sp.edit().putFloat("max_avail_torque_alert_low", value.coerceIn(5f, 70f)).apply()
    var maxAvailTorqueSpeedMinKmh: Float
        get() = sp.getFloat("max_avail_torque_speed_min", 10f)
        set(value) = sp.edit().putFloat("max_avail_torque_speed_min", value.coerceIn(0f, 60f)).apply()
    var maxAvailTorqueSimPct: Float
        get() = sp.getFloat("max_avail_torque_sim_pct", 0f)
        set(value) = sp.edit().putFloat("max_avail_torque_sim_pct", value.coerceIn(-125f, 125f)).apply()

    /** MAF sensor IAT °C (OBD 0166). */
    var mafIatEnabled: Boolean
        get() = sp.getBoolean("maf_iat", true)
        set(value) = sp.edit().putBoolean("maf_iat", value).apply()
    var mafIatTts: Boolean
        get() = sp.getBoolean("maf_iat_tts", true)
        set(value) = sp.edit().putBoolean("maf_iat_tts", value).apply()
    var mafIatWarnC: Float
        get() = sp.getFloat("maf_iat_warn_c", 70f)
        set(value) = sp.edit().putFloat("maf_iat_warn_c", value.coerceIn(40f, 100f)).apply()
    var mafIatAlertC: Float
        get() = sp.getFloat("maf_iat_alert_c", 85f)
        set(value) = sp.edit().putFloat("maf_iat_alert_c", value.coerceAtLeast(45f)).apply()
    var mafIatSpeedMinKmh: Float
        get() = sp.getFloat("maf_iat_speed_min", 15f)
        set(value) = sp.edit().putFloat("maf_iat_speed_min", value.coerceIn(0f, 60f)).apply()
    var mafIatSimC: Float
        get() = sp.getFloat("maf_iat_sim_c", 0f)
        set(value) = sp.edit().putFloat("maf_iat_sim_c", value.coerceIn(0f, 120f)).apply()

    /** Aux input status (OBD 0165). */
    var auxInputEnabled: Boolean
        get() = sp.getBoolean("aux_input", true)
        set(value) = sp.edit().putBoolean("aux_input", value).apply()
    var auxInputTts: Boolean
        get() = sp.getBoolean("aux_input_tts", true)
        set(value) = sp.edit().putBoolean("aux_input_tts", value).apply()
    var auxInputAlertMask: Int
        get() = sp.getInt("aux_input_alert_mask", 0x0F)
        set(value) = sp.edit().putInt("aux_input_alert_mask", value and 0xFF).apply()
    var auxInputSpeedMinKmh: Float
        get() = sp.getFloat("aux_input_speed_min", 10f)
        set(value) = sp.edit().putFloat("aux_input_speed_min", value.coerceIn(0f, 60f)).apply()
    var auxInputSimCode: Int
        get() = sp.getInt("aux_input_sim_code", 0)
        set(value) = sp.edit().putInt("aux_input_sim_code", value.coerceIn(0, 255)).apply()

    /** Catalyst temp B1S9 °C (OBD 017F). */
    var catB1s9Enabled: Boolean
        get() = sp.getBoolean("cat_b1s9", true)
        set(value) = sp.edit().putBoolean("cat_b1s9", value).apply()
    var catB1s9Tts: Boolean
        get() = sp.getBoolean("cat_b1s9_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s9_tts", value).apply()
    var catB1s9WarnC: Float
        get() = sp.getFloat("cat_b1s9_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s9_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s9AlertC: Float
        get() = sp.getFloat("cat_b1s9_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s9_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s9SimC: Float
        get() = sp.getFloat("cat_b1s9_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s9_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S9 °C (OBD 0180). */
    var catB2s9Enabled: Boolean
        get() = sp.getBoolean("cat_b2s9", true)
        set(value) = sp.edit().putBoolean("cat_b2s9", value).apply()
    var catB2s9Tts: Boolean
        get() = sp.getBoolean("cat_b2s9_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s9_tts", value).apply()
    var catB2s9WarnC: Float
        get() = sp.getFloat("cat_b2s9_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s9_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s9AlertC: Float
        get() = sp.getFloat("cat_b2s9_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s9_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s9SimC: Float
        get() = sp.getFloat("cat_b2s9_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s9_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Coolant ECT2 °C (OBD 0167). */
    var ect2Enabled: Boolean
        get() = sp.getBoolean("ect2", true)
        set(value) = sp.edit().putBoolean("ect2", value).apply()
    var ect2Tts: Boolean
        get() = sp.getBoolean("ect2_tts", true)
        set(value) = sp.edit().putBoolean("ect2_tts", value).apply()
    var ect2WarnC: Float
        get() = sp.getFloat("ect2_warn_c", 95f)
        set(value) = sp.edit().putFloat("ect2_warn_c", value.coerceIn(80f, 120f)).apply()
    var ect2AlertC: Float
        get() = sp.getFloat("ect2_alert_c", 105f)
        set(value) = sp.edit().putFloat("ect2_alert_c", value.coerceAtLeast(90f)).apply()
    var ect2SimC: Float
        get() = sp.getFloat("ect2_sim_c", 0f)
        set(value) = sp.edit().putFloat("ect2_sim_c", value.coerceIn(0f, 215f)).apply()

    /** IAT sensor 2 °C (OBD 0168). */
    var iat2Enabled: Boolean
        get() = sp.getBoolean("iat2", true)
        set(value) = sp.edit().putBoolean("iat2", value).apply()
    var iat2Tts: Boolean
        get() = sp.getBoolean("iat2_tts", true)
        set(value) = sp.edit().putBoolean("iat2_tts", value).apply()
    var iat2WarnC: Float
        get() = sp.getFloat("iat2_warn_c", 55f)
        set(value) = sp.edit().putFloat("iat2_warn_c", value.coerceIn(35f, 100f)).apply()
    var iat2AlertC: Float
        get() = sp.getFloat("iat2_alert_c", 65f)
        set(value) = sp.edit().putFloat("iat2_alert_c", value.coerceAtLeast(40f)).apply()
    var iat2SpeedMinKmh: Float
        get() = sp.getFloat("iat2_speed_min", 10f)
        set(value) = sp.edit().putFloat("iat2_speed_min", value.coerceIn(0f, 60f)).apply()
    var iat2SimC: Float
        get() = sp.getFloat("iat2_sim_c", 0f)
        set(value) = sp.edit().putFloat("iat2_sim_c", value.coerceIn(0f, 215f)).apply()

    /** Turbo inlet pressure kPa (OBD 016F). */
    var turboInletEnabled: Boolean
        get() = sp.getBoolean("turbo_inlet", true)
        set(value) = sp.edit().putBoolean("turbo_inlet", value).apply()
    var turboInletTts: Boolean
        get() = sp.getBoolean("turbo_inlet_tts", true)
        set(value) = sp.edit().putBoolean("turbo_inlet_tts", value).apply()
    var turboInletWarnKpa: Float
        get() = sp.getFloat("turbo_inlet_warn_kpa", 200f)
        set(value) = sp.edit().putFloat("turbo_inlet_warn_kpa", value.coerceIn(120f, 250f)).apply()
    var turboInletAlertKpa: Float
        get() = sp.getFloat("turbo_inlet_alert_kpa", 230f)
        set(value) = sp.edit().putFloat("turbo_inlet_alert_kpa", value.coerceAtLeast(150f)).apply()
    var turboInletSpeedMinKmh: Float
        get() = sp.getFloat("turbo_inlet_speed_min", 15f)
        set(value) = sp.edit().putFloat("turbo_inlet_speed_min", value.coerceIn(0f, 60f)).apply()
    var turboInletSimKpa: Float
        get() = sp.getFloat("turbo_inlet_sim_kpa", 0f)
        set(value) = sp.edit().putFloat("turbo_inlet_sim_kpa", value.coerceIn(0f, 255f)).apply()

    /** Catalyst temp B1S10 °C (OBD 0181). */
    var catB1s10Enabled: Boolean
        get() = sp.getBoolean("cat_b1s10", true)
        set(value) = sp.edit().putBoolean("cat_b1s10", value).apply()
    var catB1s10Tts: Boolean
        get() = sp.getBoolean("cat_b1s10_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s10_tts", value).apply()
    var catB1s10WarnC: Float
        get() = sp.getFloat("cat_b1s10_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s10_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s10AlertC: Float
        get() = sp.getFloat("cat_b1s10_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s10_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s10SimC: Float
        get() = sp.getFloat("cat_b1s10_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s10_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S10 °C (OBD 0182). */
    var catB2s10Enabled: Boolean
        get() = sp.getBoolean("cat_b2s10", true)
        set(value) = sp.edit().putBoolean("cat_b2s10", value).apply()
    var catB2s10Tts: Boolean
        get() = sp.getBoolean("cat_b2s10_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s10_tts", value).apply()
    var catB2s10WarnC: Float
        get() = sp.getFloat("cat_b2s10_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s10_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s10AlertC: Float
        get() = sp.getFloat("cat_b2s10_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s10_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s10SimC: Float
        get() = sp.getFloat("cat_b2s10_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s10_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** EGR temperature °C (OBD 016B). */
    var egrTempEnabled: Boolean
        get() = sp.getBoolean("egr_temp", true)
        set(value) = sp.edit().putBoolean("egr_temp", value).apply()
    var egrTempTts: Boolean
        get() = sp.getBoolean("egr_temp_tts", true)
        set(value) = sp.edit().putBoolean("egr_temp_tts", value).apply()
    var egrTempWarnC: Float
        get() = sp.getFloat("egr_temp_warn_c", 350f)
        set(value) = sp.edit().putFloat("egr_temp_warn_c", value.coerceIn(200f, 600f)).apply()
    var egrTempAlertC: Float
        get() = sp.getFloat("egr_temp_alert_c", 450f)
        set(value) = sp.edit().putFloat("egr_temp_alert_c", value.coerceAtLeast(250f)).apply()
    var egrTempSpeedMinKmh: Float
        get() = sp.getFloat("egr_temp_speed_min", 10f)
        set(value) = sp.edit().putFloat("egr_temp_speed_min", value.coerceIn(0f, 60f)).apply()
    var egrTempSimC: Float
        get() = sp.getFloat("egr_temp_sim_c", 0f)
        set(value) = sp.edit().putFloat("egr_temp_sim_c", value.coerceIn(0f, 800f)).apply()

    /** Diesel intake air flow % (OBD 016A). */
    var dieselIafEnabled: Boolean
        get() = sp.getBoolean("diesel_iaf", true)
        set(value) = sp.edit().putBoolean("diesel_iaf", value).apply()
    var dieselIafTts: Boolean
        get() = sp.getBoolean("diesel_iaf_tts", true)
        set(value) = sp.edit().putBoolean("diesel_iaf_tts", value).apply()
    var dieselIafWarnPct: Float
        get() = sp.getFloat("diesel_iaf_warn_pct", 75f)
        set(value) = sp.edit().putFloat("diesel_iaf_warn_pct", value.coerceIn(40f, 95f)).apply()
    var dieselIafAlertPct: Float
        get() = sp.getFloat("diesel_iaf_alert_pct", 88f)
        set(value) = sp.edit().putFloat("diesel_iaf_alert_pct", value.coerceAtLeast(50f)).apply()
    var dieselIafSpeedMinKmh: Float
        get() = sp.getFloat("diesel_iaf_speed_min", 15f)
        set(value) = sp.edit().putFloat("diesel_iaf_speed_min", value.coerceIn(0f, 60f)).apply()
    var dieselIafSimPct: Float
        get() = sp.getFloat("diesel_iaf_sim_pct", 0f)
        set(value) = sp.edit().putFloat("diesel_iaf_sim_pct", value.coerceIn(0f, 100f)).apply()

    /** Throttle actuator % (OBD 016C). */
    var thrActEnabled: Boolean
        get() = sp.getBoolean("thr_act", true)
        set(value) = sp.edit().putBoolean("thr_act", value).apply()
    var thrActTts: Boolean
        get() = sp.getBoolean("thr_act_tts", true)
        set(value) = sp.edit().putBoolean("thr_act_tts", value).apply()
    var thrActWarnPct: Float
        get() = sp.getFloat("thr_act_warn_pct", 85f)
        set(value) = sp.edit().putFloat("thr_act_warn_pct", value.coerceIn(50f, 98f)).apply()
    var thrActAlertPct: Float
        get() = sp.getFloat("thr_act_alert_pct", 92f)
        set(value) = sp.edit().putFloat("thr_act_alert_pct", value.coerceAtLeast(55f)).apply()
    var thrActSpeedMinKmh: Float
        get() = sp.getFloat("thr_act_speed_min", 10f)
        set(value) = sp.edit().putFloat("thr_act_speed_min", value.coerceIn(0f, 60f)).apply()
    var thrActSimPct: Float
        get() = sp.getFloat("thr_act_sim_pct", 0f)
        set(value) = sp.edit().putFloat("thr_act_sim_pct", value.coerceIn(0f, 100f)).apply()

    /** Catalyst temp B1S11 °C (OBD 0183). */
    var catB1s11Enabled: Boolean
        get() = sp.getBoolean("cat_b1s11", true)
        set(value) = sp.edit().putBoolean("cat_b1s11", value).apply()
    var catB1s11Tts: Boolean
        get() = sp.getBoolean("cat_b1s11_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s11_tts", value).apply()
    var catB1s11WarnC: Float
        get() = sp.getFloat("cat_b1s11_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s11_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s11AlertC: Float
        get() = sp.getFloat("cat_b1s11_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s11_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s11SimC: Float
        get() = sp.getFloat("cat_b1s11_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s11_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S11 °C (OBD 0184). */
    var catB2s11Enabled: Boolean
        get() = sp.getBoolean("cat_b2s11", true)
        set(value) = sp.edit().putBoolean("cat_b2s11", value).apply()
    var catB2s11Tts: Boolean
        get() = sp.getBoolean("cat_b2s11_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s11_tts", value).apply()
    var catB2s11WarnC: Float
        get() = sp.getFloat("cat_b2s11_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s11_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s11AlertC: Float
        get() = sp.getFloat("cat_b2s11_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s11_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s11SimC: Float
        get() = sp.getFloat("cat_b2s11_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s11_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Actual EGR % (OBD 0169). */
    var egrActualEnabled: Boolean
        get() = sp.getBoolean("egr_actual", true)
        set(value) = sp.edit().putBoolean("egr_actual", value).apply()
    var egrActualTts: Boolean
        get() = sp.getBoolean("egr_actual_tts", true)
        set(value) = sp.edit().putBoolean("egr_actual_tts", value).apply()
    var egrActualWarnPct: Float
        get() = sp.getFloat("egr_actual_warn_pct", 55f)
        set(value) = sp.edit().putFloat("egr_actual_warn_pct", value.coerceIn(25f, 90f)).apply()
    var egrActualAlertPct: Float
        get() = sp.getFloat("egr_actual_alert_pct", 70f)
        set(value) = sp.edit().putFloat("egr_actual_alert_pct", value.coerceAtLeast(30f)).apply()
    var egrActualSpeedMinKmh: Float
        get() = sp.getFloat("egr_actual_speed_min", 15f)
        set(value) = sp.edit().putFloat("egr_actual_speed_min", value.coerceIn(0f, 60f)).apply()
    var egrActualSimPct: Float
        get() = sp.getFloat("egr_actual_sim_pct", 0f)
        set(value) = sp.edit().putFloat("egr_actual_sim_pct", value.coerceIn(0f, 100f)).apply()

    /** Injection pressure control kPa (OBD 016E). */
    var injectCtrlEnabled: Boolean
        get() = sp.getBoolean("inject_ctrl", true)
        set(value) = sp.edit().putBoolean("inject_ctrl", value).apply()
    var injectCtrlTts: Boolean
        get() = sp.getBoolean("inject_ctrl_tts", true)
        set(value) = sp.edit().putBoolean("inject_ctrl_tts", value).apply()
    var injectCtrlWarnKpa: Float
        get() = sp.getFloat("inject_ctrl_warn_kpa", 8000f)
        set(value) = sp.edit().putFloat("inject_ctrl_warn_kpa", value.coerceIn(2000f, 20000f)).apply()
    var injectCtrlAlertKpa: Float
        get() = sp.getFloat("inject_ctrl_alert_kpa", 12000f)
        set(value) = sp.edit().putFloat("inject_ctrl_alert_kpa", value.coerceAtLeast(3000f)).apply()
    var injectCtrlSpeedMinKmh: Float
        get() = sp.getFloat("inject_ctrl_speed_min", 10f)
        set(value) = sp.edit().putFloat("inject_ctrl_speed_min", value.coerceIn(0f, 60f)).apply()
    var injectCtrlSimKpa: Float
        get() = sp.getFloat("inject_ctrl_sim_kpa", 0f)
        set(value) = sp.edit().putFloat("inject_ctrl_sim_kpa", value.coerceIn(0f, 25000f)).apply()

    /** Fuel pressure control kPa (OBD 016D). */
    var fuelCtrlEnabled: Boolean
        get() = sp.getBoolean("fuel_ctrl", true)
        set(value) = sp.edit().putBoolean("fuel_ctrl", value).apply()
    var fuelCtrlTts: Boolean
        get() = sp.getBoolean("fuel_ctrl_tts", true)
        set(value) = sp.edit().putBoolean("fuel_ctrl_tts", value).apply()
    var fuelCtrlWarnKpa: Float
        get() = sp.getFloat("fuel_ctrl_warn_kpa", 6000f)
        set(value) = sp.edit().putFloat("fuel_ctrl_warn_kpa", value.coerceIn(1500f, 15000f)).apply()
    var fuelCtrlAlertKpa: Float
        get() = sp.getFloat("fuel_ctrl_alert_kpa", 9000f)
        set(value) = sp.edit().putFloat("fuel_ctrl_alert_kpa", value.coerceAtLeast(2000f)).apply()
    var fuelCtrlSpeedMinKmh: Float
        get() = sp.getFloat("fuel_ctrl_speed_min", 10f)
        set(value) = sp.edit().putFloat("fuel_ctrl_speed_min", value.coerceIn(0f, 60f)).apply()
    var fuelCtrlSimKpa: Float
        get() = sp.getFloat("fuel_ctrl_sim_kpa", 0f)
        set(value) = sp.edit().putFloat("fuel_ctrl_sim_kpa", value.coerceIn(0f, 20000f)).apply()

    /** Catalyst temp B1S12 °C (OBD 0185). */
    var catB1s12Enabled: Boolean
        get() = sp.getBoolean("cat_b1s12", true)
        set(value) = sp.edit().putBoolean("cat_b1s12", value).apply()
    var catB1s12Tts: Boolean
        get() = sp.getBoolean("cat_b1s12_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s12_tts", value).apply()
    var catB1s12WarnC: Float
        get() = sp.getFloat("cat_b1s12_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s12_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s12AlertC: Float
        get() = sp.getFloat("cat_b1s12_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s12_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s12SimC: Float
        get() = sp.getFloat("cat_b1s12_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s12_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S12 °C (OBD 0186). */
    var catB2s12Enabled: Boolean
        get() = sp.getBoolean("cat_b2s12", true)
        set(value) = sp.edit().putBoolean("cat_b2s12", value).apply()
    var catB2s12Tts: Boolean
        get() = sp.getBoolean("cat_b2s12_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s12_tts", value).apply()
    var catB2s12WarnC: Float
        get() = sp.getFloat("cat_b2s12_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s12_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s12AlertC: Float
        get() = sp.getFloat("cat_b2s12_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s12_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s12SimC: Float
        get() = sp.getFloat("cat_b2s12_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s12_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** STFT bank 2 (OBD 0108). */
    var stftB2Enabled: Boolean
        get() = sp.getBoolean("stft_b2", true)
        set(value) = sp.edit().putBoolean("stft_b2", value).apply()
    var stftB2Tts: Boolean
        get() = sp.getBoolean("stft_b2_tts", true)
        set(value) = sp.edit().putBoolean("stft_b2_tts", value).apply()
    var stftB2WarnPct: Float
        get() = sp.getFloat("stft_b2_warn_pct", 12f)
        set(value) = sp.edit().putFloat("stft_b2_warn_pct", value.coerceIn(5f, 40f)).apply()
    var stftB2AlertPct: Float
        get() = sp.getFloat("stft_b2_alert_pct", 20f)
        set(value) = sp.edit().putFloat("stft_b2_alert_pct", value.coerceIn(8f, 50f)).apply()
    var stftB2SpeedMinKmh: Float
        get() = sp.getFloat("stft_b2_speed_min", 20f)
        set(value) = sp.edit().putFloat("stft_b2_speed_min", value.coerceIn(0f, 60f)).apply()
    var stftB2SimPct: Float
        get() = sp.getFloat("stft_b2_sim_pct", 0f)
        set(value) = sp.edit().putFloat("stft_b2_sim_pct", value.coerceIn(-50f, 50f)).apply()
    var stftB2SimSpeedKmh: Float
        get() = sp.getFloat("stft_b2_sim_speed", 40f)
        set(value) = sp.edit().putFloat("stft_b2_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** LTFT bank 2 (OBD 0109). */
    var ltftB2Enabled: Boolean
        get() = sp.getBoolean("ltft_b2", true)
        set(value) = sp.edit().putBoolean("ltft_b2", value).apply()
    var ltftB2Tts: Boolean
        get() = sp.getBoolean("ltft_b2_tts", true)
        set(value) = sp.edit().putBoolean("ltft_b2_tts", value).apply()
    var ltftB2WarnPct: Float
        get() = sp.getFloat("ltft_b2_warn_pct", 12f)
        set(value) = sp.edit().putFloat("ltft_b2_warn_pct", value.coerceIn(5f, 40f)).apply()
    var ltftB2AlertPct: Float
        get() = sp.getFloat("ltft_b2_alert_pct", 20f)
        set(value) = sp.edit().putFloat("ltft_b2_alert_pct", value.coerceIn(8f, 50f)).apply()
    var ltftB2SpeedMinKmh: Float
        get() = sp.getFloat("ltft_b2_speed_min", 20f)
        set(value) = sp.edit().putFloat("ltft_b2_speed_min", value.coerceIn(0f, 60f)).apply()
    var ltftB2SimPct: Float
        get() = sp.getFloat("ltft_b2_sim_pct", 0f)
        set(value) = sp.edit().putFloat("ltft_b2_sim_pct", value.coerceIn(-50f, 50f)).apply()
    var ltftB2SimSpeedKmh: Float
        get() = sp.getFloat("ltft_b2_sim_speed", 40f)
        set(value) = sp.edit().putFloat("ltft_b2_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Catalyst temp B1S13 °C (OBD 0187). */
    var catB1s13Enabled: Boolean
        get() = sp.getBoolean("cat_b1s13", true)
        set(value) = sp.edit().putBoolean("cat_b1s13", value).apply()
    var catB1s13Tts: Boolean
        get() = sp.getBoolean("cat_b1s13_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s13_tts", value).apply()
    var catB1s13WarnC: Float
        get() = sp.getFloat("cat_b1s13_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s13_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s13AlertC: Float
        get() = sp.getFloat("cat_b1s13_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s13_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s13SimC: Float
        get() = sp.getFloat("cat_b1s13_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s13_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S13 °C (OBD 0188). */
    var catB2s13Enabled: Boolean
        get() = sp.getBoolean("cat_b2s13", true)
        set(value) = sp.edit().putBoolean("cat_b2s13", value).apply()
    var catB2s13Tts: Boolean
        get() = sp.getBoolean("cat_b2s13_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s13_tts", value).apply()
    var catB2s13WarnC: Float
        get() = sp.getFloat("cat_b2s13_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s13_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s13AlertC: Float
        get() = sp.getFloat("cat_b2s13_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s13_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s13SimC: Float
        get() = sp.getFloat("cat_b2s13_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s13_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** DPF aftertreatment trigger % (OBD 018B). */
    var dpfTrigEnabled: Boolean
        get() = sp.getBoolean("dpf_trig", true)
        set(value) = sp.edit().putBoolean("dpf_trig", value).apply()
    var dpfTrigTts: Boolean
        get() = sp.getBoolean("dpf_trig_tts", true)
        set(value) = sp.edit().putBoolean("dpf_trig_tts", value).apply()
    var dpfTrigWarnPct: Float
        get() = sp.getFloat("dpf_trig_warn_pct", 70f)
        set(value) = sp.edit().putFloat("dpf_trig_warn_pct", value.coerceIn(40f, 95f)).apply()
    var dpfTrigAlertPct: Float
        get() = sp.getFloat("dpf_trig_alert_pct", 85f)
        set(value) = sp.edit().putFloat("dpf_trig_alert_pct", value.coerceAtLeast(45f)).apply()
    var dpfTrigSpeedMinKmh: Float
        get() = sp.getFloat("dpf_trig_speed_min", 15f)
        set(value) = sp.edit().putFloat("dpf_trig_speed_min", value.coerceIn(0f, 60f)).apply()
    var dpfTrigSimPct: Float
        get() = sp.getFloat("dpf_trig_sim_pct", 0f)
        set(value) = sp.edit().putFloat("dpf_trig_sim_pct", value.coerceIn(0f, 100f)).apply()
    var dpfTrigSimSpeedKmh: Float
        get() = sp.getFloat("dpf_trig_sim_speed", 40f)
        set(value) = sp.edit().putFloat("dpf_trig_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Throttle G % (OBD 018D). */
    var thrGEnabled: Boolean
        get() = sp.getBoolean("thr_g", true)
        set(value) = sp.edit().putBoolean("thr_g", value).apply()
    var thrGTts: Boolean
        get() = sp.getBoolean("thr_g_tts", true)
        set(value) = sp.edit().putBoolean("thr_g_tts", value).apply()
    var thrGWarnPct: Float
        get() = sp.getFloat("thr_g_warn_pct", 75f)
        set(value) = sp.edit().putFloat("thr_g_warn_pct", value.coerceIn(40f, 95f)).apply()
    var thrGAlertPct: Float
        get() = sp.getFloat("thr_g_alert_pct", 90f)
        set(value) = sp.edit().putFloat("thr_g_alert_pct", value.coerceAtLeast(45f)).apply()
    var thrGSpeedMinKmh: Float
        get() = sp.getFloat("thr_g_speed_min", 20f)
        set(value) = sp.edit().putFloat("thr_g_speed_min", value.coerceIn(0f, 60f)).apply()
    var thrGSimPct: Float
        get() = sp.getFloat("thr_g_sim_pct", 0f)
        set(value) = sp.edit().putFloat("thr_g_sim_pct", value.coerceIn(0f, 100f)).apply()
    var thrGSimSpeedKmh: Float
        get() = sp.getFloat("thr_g_sim_speed", 40f)
        set(value) = sp.edit().putFloat("thr_g_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Engine friction torque % (OBD 018E). */
    var engFrictionEnabled: Boolean
        get() = sp.getBoolean("eng_friction", true)
        set(value) = sp.edit().putBoolean("eng_friction", value).apply()
    var engFrictionTts: Boolean
        get() = sp.getBoolean("eng_friction_tts", true)
        set(value) = sp.edit().putBoolean("eng_friction_tts", value).apply()
    var engFrictionWarnPct: Float
        get() = sp.getFloat("eng_friction_warn_pct", 35f)
        set(value) = sp.edit().putFloat("eng_friction_warn_pct", value.coerceIn(10f, 100f)).apply()
    var engFrictionAlertPct: Float
        get() = sp.getFloat("eng_friction_alert_pct", 50f)
        set(value) = sp.edit().putFloat("eng_friction_alert_pct", value.coerceAtLeast(15f)).apply()
    var engFrictionSpeedMinKmh: Float
        get() = sp.getFloat("eng_friction_speed_min", 20f)
        set(value) = sp.edit().putFloat("eng_friction_speed_min", value.coerceIn(0f, 60f)).apply()
    var engFrictionSimPct: Float
        get() = sp.getFloat("eng_friction_sim_pct", 0f)
        set(value) = sp.edit().putFloat("eng_friction_sim_pct", value.coerceIn(-125f, 125f)).apply()
    var engFrictionSimSpeedKmh: Float
        get() = sp.getFloat("eng_friction_sim_speed", 40f)
        set(value) = sp.edit().putFloat("eng_friction_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Catalyst temp B1S14 °C (OBD 0189). */
    var catB1s14Enabled: Boolean
        get() = sp.getBoolean("cat_b1s14", true)
        set(value) = sp.edit().putBoolean("cat_b1s14", value).apply()
    var catB1s14Tts: Boolean
        get() = sp.getBoolean("cat_b1s14_tts", true)
        set(value) = sp.edit().putBoolean("cat_b1s14_tts", value).apply()
    var catB1s14WarnC: Float
        get() = sp.getFloat("cat_b1s14_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b1s14_warn_c", value.coerceAtLeast(400f)).apply()
    var catB1s14AlertC: Float
        get() = sp.getFloat("cat_b1s14_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b1s14_alert_c", value.coerceAtLeast(450f)).apply()
    var catB1s14SimC: Float
        get() = sp.getFloat("cat_b1s14_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b1s14_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** Catalyst temp B2S14 °C (OBD 018A). */
    var catB2s14Enabled: Boolean
        get() = sp.getBoolean("cat_b2s14", true)
        set(value) = sp.edit().putBoolean("cat_b2s14", value).apply()
    var catB2s14Tts: Boolean
        get() = sp.getBoolean("cat_b2s14_tts", true)
        set(value) = sp.edit().putBoolean("cat_b2s14_tts", value).apply()
    var catB2s14WarnC: Float
        get() = sp.getFloat("cat_b2s14_warn_c", 750f)
        set(value) = sp.edit().putFloat("cat_b2s14_warn_c", value.coerceAtLeast(400f)).apply()
    var catB2s14AlertC: Float
        get() = sp.getFloat("cat_b2s14_alert_c", 850f)
        set(value) = sp.edit().putFloat("cat_b2s14_alert_c", value.coerceAtLeast(450f)).apply()
    var catB2s14SimC: Float
        get() = sp.getFloat("cat_b2s14_sim_c", 0f)
        set(value) = sp.edit().putFloat("cat_b2s14_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** O2 lambda B1S1 (OBD 018C). */
    var o2LambdaEnabled: Boolean
        get() = sp.getBoolean("o2_lambda", true)
        set(value) = sp.edit().putBoolean("o2_lambda", value).apply()
    var o2LambdaTts: Boolean
        get() = sp.getBoolean("o2_lambda_tts", true)
        set(value) = sp.edit().putBoolean("o2_lambda_tts", value).apply()
    var o2LambdaWarn: Float
        get() = sp.getFloat("o2_lambda_warn", 1.10f)
        set(value) = sp.edit().putFloat("o2_lambda_warn", value.coerceIn(0.9f, 1.5f)).apply()
    var o2LambdaAlert: Float
        get() = sp.getFloat("o2_lambda_alert", 1.15f)
        set(value) = sp.edit().putFloat("o2_lambda_alert", value.coerceAtLeast(0.92f)).apply()
    var o2LambdaSpeedMinKmh: Float
        get() = sp.getFloat("o2_lambda_speed_min", 20f)
        set(value) = sp.edit().putFloat("o2_lambda_speed_min", value.coerceIn(0f, 60f)).apply()
    var o2LambdaSim: Float
        get() = sp.getFloat("o2_lambda_sim", 0f)
        set(value) = sp.edit().putFloat("o2_lambda_sim", value.coerceIn(0f, 2f)).apply()
    var o2LambdaSimSpeedKmh: Float
        get() = sp.getFloat("o2_lambda_sim_speed", 40f)
        set(value) = sp.edit().putFloat("o2_lambda_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** PM sensor B1 % (OBD 018F C/D). */
    var pmB1Enabled: Boolean
        get() = sp.getBoolean("pm_b1", true)
        set(value) = sp.edit().putBoolean("pm_b1", value).apply()
    var pmB1Tts: Boolean
        get() = sp.getBoolean("pm_b1_tts", true)
        set(value) = sp.edit().putBoolean("pm_b1_tts", value).apply()
    var pmB1WarnPct: Float
        get() = sp.getFloat("pm_b1_warn_pct", 70f)
        set(value) = sp.edit().putFloat("pm_b1_warn_pct", value.coerceIn(30f, 95f)).apply()
    var pmB1AlertPct: Float
        get() = sp.getFloat("pm_b1_alert_pct", 85f)
        set(value) = sp.edit().putFloat("pm_b1_alert_pct", value.coerceAtLeast(35f)).apply()
    var pmB1SpeedMinKmh: Float
        get() = sp.getFloat("pm_b1_speed_min", 15f)
        set(value) = sp.edit().putFloat("pm_b1_speed_min", value.coerceIn(0f, 60f)).apply()
    var pmB1SimPct: Float
        get() = sp.getFloat("pm_b1_sim_pct", 0f)
        set(value) = sp.edit().putFloat("pm_b1_sim_pct", value.coerceIn(0f, 200f)).apply()
    var pmB1SimSpeedKmh: Float
        get() = sp.getFloat("pm_b1_sim_speed", 40f)
        set(value) = sp.edit().putFloat("pm_b1_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** PM sensor B2 % (OBD 018F F/G). */
    var pmB2Enabled: Boolean
        get() = sp.getBoolean("pm_b2", true)
        set(value) = sp.edit().putBoolean("pm_b2", value).apply()
    var pmB2Tts: Boolean
        get() = sp.getBoolean("pm_b2_tts", true)
        set(value) = sp.edit().putBoolean("pm_b2_tts", value).apply()
    var pmB2WarnPct: Float
        get() = sp.getFloat("pm_b2_warn_pct", 70f)
        set(value) = sp.edit().putFloat("pm_b2_warn_pct", value.coerceIn(30f, 95f)).apply()
    var pmB2AlertPct: Float
        get() = sp.getFloat("pm_b2_alert_pct", 85f)
        set(value) = sp.edit().putFloat("pm_b2_alert_pct", value.coerceAtLeast(35f)).apply()
    var pmB2SpeedMinKmh: Float
        get() = sp.getFloat("pm_b2_speed_min", 15f)
        set(value) = sp.edit().putFloat("pm_b2_speed_min", value.coerceIn(0f, 60f)).apply()
    var pmB2SimPct: Float
        get() = sp.getFloat("pm_b2_sim_pct", 0f)
        set(value) = sp.edit().putFloat("pm_b2_sim_pct", value.coerceIn(0f, 200f)).apply()
    var pmB2SimSpeedKmh: Float
        get() = sp.getFloat("pm_b2_sim_speed", 40f)
        set(value) = sp.edit().putFloat("pm_b2_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** EGT B1S5 °C (OBD 0198). */
    var egtB1s5Enabled: Boolean
        get() = sp.getBoolean("egt_b1s5", true)
        set(value) = sp.edit().putBoolean("egt_b1s5", value).apply()
    var egtB1s5Tts: Boolean
        get() = sp.getBoolean("egt_b1s5_tts", true)
        set(value) = sp.edit().putBoolean("egt_b1s5_tts", value).apply()
    var egtB1s5WarnC: Float
        get() = sp.getFloat("egt_b1s5_warn_c", 750f)
        set(value) = sp.edit().putFloat("egt_b1s5_warn_c", value.coerceAtLeast(400f)).apply()
    var egtB1s5AlertC: Float
        get() = sp.getFloat("egt_b1s5_alert_c", 850f)
        set(value) = sp.edit().putFloat("egt_b1s5_alert_c", value.coerceAtLeast(450f)).apply()
    var egtB1s5SimC: Float
        get() = sp.getFloat("egt_b1s5_sim_c", 0f)
        set(value) = sp.edit().putFloat("egt_b1s5_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** EGT B2S5 °C (OBD 0199). */
    var egtB2s5Enabled: Boolean
        get() = sp.getBoolean("egt_b2s5", true)
        set(value) = sp.edit().putBoolean("egt_b2s5", value).apply()
    var egtB2s5Tts: Boolean
        get() = sp.getBoolean("egt_b2s5_tts", true)
        set(value) = sp.edit().putBoolean("egt_b2s5_tts", value).apply()
    var egtB2s5WarnC: Float
        get() = sp.getFloat("egt_b2s5_warn_c", 750f)
        set(value) = sp.edit().putFloat("egt_b2s5_warn_c", value.coerceAtLeast(400f)).apply()
    var egtB2s5AlertC: Float
        get() = sp.getFloat("egt_b2s5_alert_c", 850f)
        set(value) = sp.edit().putFloat("egt_b2s5_alert_c", value.coerceAtLeast(450f)).apply()
    var egtB2s5SimC: Float
        get() = sp.getFloat("egt_b2s5_sim_c", 0f)
        set(value) = sp.edit().putFloat("egt_b2s5_sim_c", value.coerceIn(0f, 1200f)).apply()

    /** O2 lambda B1S3 (OBD 019C). */
    var o2LambdaB1s3Enabled: Boolean
        get() = sp.getBoolean("o2_lmb_b1s3", true)
        set(value) = sp.edit().putBoolean("o2_lmb_b1s3", value).apply()
    var o2LambdaB1s3Tts: Boolean
        get() = sp.getBoolean("o2_lmb_b1s3_tts", true)
        set(value) = sp.edit().putBoolean("o2_lmb_b1s3_tts", value).apply()
    var o2LambdaB1s3Warn: Float
        get() = sp.getFloat("o2_lmb_b1s3_warn", 1.10f)
        set(value) = sp.edit().putFloat("o2_lmb_b1s3_warn", value.coerceIn(0.9f, 1.5f)).apply()
    var o2LambdaB1s3Alert: Float
        get() = sp.getFloat("o2_lmb_b1s3_alert", 1.15f)
        set(value) = sp.edit().putFloat("o2_lmb_b1s3_alert", value.coerceAtLeast(0.92f)).apply()
    var o2LambdaB1s3SpeedMinKmh: Float
        get() = sp.getFloat("o2_lmb_b1s3_speed_min", 20f)
        set(value) = sp.edit().putFloat("o2_lmb_b1s3_speed_min", value.coerceIn(0f, 60f)).apply()
    var o2LambdaB1s3Sim: Float
        get() = sp.getFloat("o2_lmb_b1s3_sim", 0f)
        set(value) = sp.edit().putFloat("o2_lmb_b1s3_sim", value.coerceIn(0f, 2f)).apply()
    var o2LambdaB1s3SimSpeedKmh: Float
        get() = sp.getFloat("o2_lmb_b1s3_sim_speed", 40f)
        set(value) = sp.edit().putFloat("o2_lmb_b1s3_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** O2 lambda B2S3 (OBD 019C). */
    var o2LambdaB2s3Enabled: Boolean
        get() = sp.getBoolean("o2_lmb_b2s3", true)
        set(value) = sp.edit().putBoolean("o2_lmb_b2s3", value).apply()
    var o2LambdaB2s3Tts: Boolean
        get() = sp.getBoolean("o2_lmb_b2s3_tts", true)
        set(value) = sp.edit().putBoolean("o2_lmb_b2s3_tts", value).apply()
    var o2LambdaB2s3Warn: Float
        get() = sp.getFloat("o2_lmb_b2s3_warn", 1.10f)
        set(value) = sp.edit().putFloat("o2_lmb_b2s3_warn", value.coerceIn(0.9f, 1.5f)).apply()
    var o2LambdaB2s3Alert: Float
        get() = sp.getFloat("o2_lmb_b2s3_alert", 1.15f)
        set(value) = sp.edit().putFloat("o2_lmb_b2s3_alert", value.coerceAtLeast(0.92f)).apply()
    var o2LambdaB2s3SpeedMinKmh: Float
        get() = sp.getFloat("o2_lmb_b2s3_speed_min", 20f)
        set(value) = sp.edit().putFloat("o2_lmb_b2s3_speed_min", value.coerceIn(0f, 60f)).apply()
    var o2LambdaB2s3Sim: Float
        get() = sp.getFloat("o2_lmb_b2s3_sim", 0f)
        set(value) = sp.edit().putFloat("o2_lmb_b2s3_sim", value.coerceIn(0f, 2f)).apply()
    var o2LambdaB2s3SimSpeedKmh: Float
        get() = sp.getFloat("o2_lmb_b2s3_sim_speed", 40f)
        set(value) = sp.edit().putFloat("o2_lmb_b2s3_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** NOx reagent quality hours (OBD 0194). */
    var noxReqEnabled: Boolean
        get() = sp.getBoolean("nox_req", true)
        set(value) = sp.edit().putBoolean("nox_req", value).apply()
    var noxReqTts: Boolean
        get() = sp.getBoolean("nox_req_tts", true)
        set(value) = sp.edit().putBoolean("nox_req_tts", value).apply()
    var noxReqWarnH: Float
        get() = sp.getFloat("nox_req_warn_h", 10f)
        set(value) = sp.edit().putFloat("nox_req_warn_h", value.coerceIn(1f, 500f)).apply()
    var noxReqAlertH: Float
        get() = sp.getFloat("nox_req_alert_h", 20f)
        set(value) = sp.edit().putFloat("nox_req_alert_h", value.coerceAtLeast(2f)).apply()
    var noxReqSimH: Float
        get() = sp.getFloat("nox_req_sim_h", 0f)
        set(value) = sp.edit().putFloat("nox_req_sim_h", value.coerceIn(0f, 65535f)).apply()

    /** Engine RPM over-rev (OBD 010C). */
    var rpmEnabled: Boolean
        get() = sp.getBoolean("rpm_over", true)
        set(value) = sp.edit().putBoolean("rpm_over", value).apply()

    var rpmTts: Boolean
        get() = sp.getBoolean("rpm_over_tts", true)
        set(value) = sp.edit().putBoolean("rpm_over_tts", value).apply()

    var rpmWarn: Float
        get() = sp.getFloat("rpm_warn", 4500f)
        set(value) = sp.edit().putFloat("rpm_warn", value.coerceIn(2500f, 7000f)).apply()

    var rpmAlert: Float
        get() = sp.getFloat("rpm_alert", 5500f)
        set(value) = sp.edit().putFloat("rpm_alert", value.coerceIn(3000f, 8000f)).apply()

    /** Demo: force RPM (0 = live). */
    var rpmSim: Float
        get() = sp.getFloat("rpm_sim", 0f)
        set(value) = sp.edit().putFloat("rpm_sim", value.coerceIn(0f, 8000f)).apply()

    /** Calculated engine load (OBD 0104). */
    var engineLoadEnabled: Boolean
        get() = sp.getBoolean("engine_load", true)
        set(value) = sp.edit().putBoolean("engine_load", value).apply()

    var engineLoadTts: Boolean
        get() = sp.getBoolean("engine_load_tts", true)
        set(value) = sp.edit().putBoolean("engine_load_tts", value).apply()

    var engineLoadWarnPct: Float
        get() = sp.getFloat("engine_load_warn_pct", 80f)
        set(value) = sp.edit().putFloat("engine_load_warn_pct", value.coerceIn(50f, 98f)).apply()

    var engineLoadAlertPct: Float
        get() = sp.getFloat("engine_load_alert_pct", 92f)
        set(value) = sp.edit().putFloat("engine_load_alert_pct", value.coerceIn(55f, 100f)).apply()

    var engineLoadSpeedMinKmh: Float
        get() = sp.getFloat("engine_load_speed_min", 20f)
        set(value) = sp.edit().putFloat("engine_load_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo load % (0 = live). */
    var engineLoadSimPct: Float
        get() = sp.getFloat("engine_load_sim_pct", 0f)
        set(value) = sp.edit().putFloat("engine_load_sim_pct", value.coerceIn(0f, 100f)).apply()

    var engineLoadSimSpeedKmh: Float
        get() = sp.getFloat("engine_load_sim_speed", 40f)
        set(value) = sp.edit().putFloat("engine_load_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Short-term fuel trim (OBD 0106). */
    var stftEnabled: Boolean
        get() = sp.getBoolean("stft", true)
        set(value) = sp.edit().putBoolean("stft", value).apply()

    var stftTts: Boolean
        get() = sp.getBoolean("stft_tts", true)
        set(value) = sp.edit().putBoolean("stft_tts", value).apply()

    var stftWarnPct: Float
        get() = sp.getFloat("stft_warn_pct", 12f)
        set(value) = sp.edit().putFloat("stft_warn_pct", value.coerceIn(5f, 40f)).apply()

    var stftAlertPct: Float
        get() = sp.getFloat("stft_alert_pct", 20f)
        set(value) = sp.edit().putFloat("stft_alert_pct", value.coerceIn(8f, 50f)).apply()

    var stftSpeedMinKmh: Float
        get() = sp.getFloat("stft_speed_min", 20f)
        set(value) = sp.edit().putFloat("stft_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo STFT % (0 = live OBD). */
    var stftSimPct: Float
        get() = sp.getFloat("stft_sim_pct", 0f)
        set(value) = sp.edit().putFloat("stft_sim_pct", value.coerceIn(-50f, 50f)).apply()

    var stftSimSpeedKmh: Float
        get() = sp.getFloat("stft_sim_speed", 40f)
        set(value) = sp.edit().putFloat("stft_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Long-term fuel trim (OBD 0107). */
    var ltftEnabled: Boolean
        get() = sp.getBoolean("ltft", true)
        set(value) = sp.edit().putBoolean("ltft", value).apply()

    var ltftTts: Boolean
        get() = sp.getBoolean("ltft_tts", true)
        set(value) = sp.edit().putBoolean("ltft_tts", value).apply()

    var ltftWarnPct: Float
        get() = sp.getFloat("ltft_warn_pct", 12f)
        set(value) = sp.edit().putFloat("ltft_warn_pct", value.coerceIn(5f, 40f)).apply()

    var ltftAlertPct: Float
        get() = sp.getFloat("ltft_alert_pct", 20f)
        set(value) = sp.edit().putFloat("ltft_alert_pct", value.coerceIn(8f, 50f)).apply()

    var ltftSpeedMinKmh: Float
        get() = sp.getFloat("ltft_speed_min", 20f)
        set(value) = sp.edit().putFloat("ltft_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo LTFT % (0 = live OBD). */
    var ltftSimPct: Float
        get() = sp.getFloat("ltft_sim_pct", 0f)
        set(value) = sp.edit().putFloat("ltft_sim_pct", value.coerceIn(-50f, 50f)).apply()

    var ltftSimSpeedKmh: Float
        get() = sp.getFloat("ltft_sim_speed", 40f)
        set(value) = sp.edit().putFloat("ltft_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Intake MAP (OBD 010B). */
    var mapEnabled: Boolean
        get() = sp.getBoolean("map_pressure", true)
        set(value) = sp.edit().putBoolean("map_pressure", value).apply()

    var mapTts: Boolean
        get() = sp.getBoolean("map_pressure_tts", true)
        set(value) = sp.edit().putBoolean("map_pressure_tts", value).apply()

    var mapWarnKpa: Float
        get() = sp.getFloat("map_warn_kpa", 95f)
        set(value) = sp.edit().putFloat("map_warn_kpa", value.coerceIn(50f, 200f)).apply()

    var mapAlertKpa: Float
        get() = sp.getFloat("map_alert_kpa", 105f)
        set(value) = sp.edit().putFloat("map_alert_kpa", value.coerceIn(60f, 255f)).apply()

    var mapSpeedMinKmh: Float
        get() = sp.getFloat("map_speed_min", 20f)
        set(value) = sp.edit().putFloat("map_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo MAP kPa (0 = live OBD). */
    var mapSimKpa: Float
        get() = sp.getFloat("map_sim_kpa", 0f)
        set(value) = sp.edit().putFloat("map_sim_kpa", value.coerceIn(0f, 255f)).apply()

    var mapSimSpeedKmh: Float
        get() = sp.getFloat("map_sim_speed", 40f)
        set(value) = sp.edit().putFloat("map_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** High throttle / WOT (OBD 0111). */
    var throttleEnabled: Boolean
        get() = sp.getBoolean("high_throttle", true)
        set(value) = sp.edit().putBoolean("high_throttle", value).apply()

    var throttleTts: Boolean
        get() = sp.getBoolean("high_throttle_tts", true)
        set(value) = sp.edit().putBoolean("high_throttle_tts", value).apply()

    var throttleWarnPct: Float
        get() = sp.getFloat("throttle_warn_pct", 70f)
        set(value) = sp.edit().putFloat("throttle_warn_pct", value.coerceIn(40f, 95f)).apply()

    var throttleAlertPct: Float
        get() = sp.getFloat("throttle_alert_pct", 85f)
        set(value) = sp.edit().putFloat("throttle_alert_pct", value.coerceIn(50f, 100f)).apply()

    /** Seconds at ≥ warn → escalate to alert. */
    var throttleAlertHoldSec: Float
        get() = sp.getFloat("throttle_alert_hold_sec", 8f)
        set(value) = sp.edit().putFloat("throttle_alert_hold_sec", value.coerceIn(2f, 60f)).apply()

    var throttleSpeedMinKmh: Float
        get() = sp.getFloat("throttle_speed_min", 20f)
        set(value) = sp.edit().putFloat("throttle_speed_min", value.coerceIn(0f, 60f)).apply()

    /** Demo throttle % (0 = live). */
    var throttleSimPct: Float
        get() = sp.getFloat("throttle_sim_pct", 0f)
        set(value) = sp.edit().putFloat("throttle_sim_pct", value.coerceIn(0f, 100f)).apply()

    var throttleSimSpeedKmh: Float
        get() = sp.getFloat("throttle_sim_speed", 40f)
        set(value) = sp.edit().putFloat("throttle_sim_speed", value.coerceIn(0f, 160f)).apply()

    /** Outdoor ice / frost (ambient). */
    var iceEnabled: Boolean
        get() = sp.getBoolean("ice_frost", true)
        set(value) = sp.edit().putBoolean("ice_frost", value).apply()

    var iceTts: Boolean
        get() = sp.getBoolean("ice_frost_tts", true)
        set(value) = sp.edit().putBoolean("ice_frost_tts", value).apply()

    /** Outdoor ≤ this → warn (°C). */
    var iceWarnC: Float
        get() = sp.getFloat("ice_warn_c", 3f)
        set(value) = sp.edit().putFloat("ice_warn_c", value.coerceIn(-5f, 10f)).apply()

    /** Outdoor ≤ this → alert (°C). */
    var iceAlertC: Float
        get() = sp.getFloat("ice_alert_c", 0f)
        set(value) = sp.edit().putFloat("ice_alert_c", value.coerceIn(-20f, 5f)).apply()

    /** Demo outdoor °C (allows 0 / negative). */
    var iceSimOn: Boolean
        get() = sp.getBoolean("ice_sim_on", false)
        set(value) = sp.edit().putBoolean("ice_sim_on", value).apply()

    var iceSimC: Float
        get() = sp.getFloat("ice_sim_c", 0f)
        set(value) = sp.edit().putFloat("ice_sim_c", value.coerceIn(-40f, 40f)).apply()

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

    /** Allow driver ack / reply to fleet messages. */
    var messageReplyEnabled: Boolean
        get() = sp.getBoolean("message_reply", true)
        set(value) = sp.edit().putBoolean("message_reply", value).apply()

    var messageReplyTts: Boolean
        get() = sp.getBoolean("message_reply_tts", true)
        set(value) = sp.edit().putBoolean("message_reply_tts", value).apply()

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
