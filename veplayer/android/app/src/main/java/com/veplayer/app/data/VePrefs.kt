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
