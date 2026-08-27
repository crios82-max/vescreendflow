package com.veplayer.app.fleet

import com.veplayer.app.data.VePrefs
import com.veplayer.app.vehicle.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ShiftSnapshot(
    val id: Long = 0,
    val status: String = "idle",
    val distanceKm: Double = 0.0,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val driverCode: String = "",
    val driverName: String = "",
    val ecoScore: Int? = null,
    val ecoBand: String = "",
    val idleSec: Double = 0.0,
    val overspeedSec: Double = 0.0,
    val absEvents: Int = 0,
)

/**
 * Local shift tracking + SenseFlow `/api/fleet/shifts`.
 * Distance: odometer delta when available, else integrates speed×dt.
 */
object ShiftTracker {
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val _shift = MutableStateFlow(ShiftSnapshot())
    val shift: StateFlow<ShiftSnapshot> = _shift.asStateFlow()

    private val _summary = MutableStateFlow(ShiftSummary.State())
    val summary: StateFlow<ShiftSummary.State> = _summary.asStateFlow()

    private var lastSpeedTs = 0L
    private var integratedKm = 0.0
    private var startOdo: Float? = null

    fun clearLocal() {
        integratedKm = 0.0
        startOdo = null
        lastSpeedTs = 0L
        _shift.value = ShiftSnapshot()
    }

    fun clearSummary() {
        _summary.value = ShiftSummary.State()
    }

    fun tickLocal(prefs: VePrefs) {
        if (_shift.value.status != "open" && prefs.driverId <= 0) return
        val snap = VehicleState.state.value
        val odo = snap.odometerKm
        if (odo != null) {
            if (startOdo == null) startOdo = odo
            val fromOdo = (odo - (startOdo ?: odo)).toDouble().coerceAtLeast(0.0)
            integratedKm = maxOf(integratedKm, fromOdo)
        } else {
            val now = System.currentTimeMillis()
            if (lastSpeedTs > 0) {
                val dtH = (now - lastSpeedTs) / 3_600_000.0
                integratedKm += (snap.speedKmh.toDouble().coerceAtLeast(0.0)) * dtH
            }
            lastSpeedTs = now
        }
        val cur = _shift.value
        if (cur.status == "open") {
            _shift.value = cur.copy(distanceKm = integratedKm)
        }
    }

    fun start(
        prefs: VePrefs,
        driverId: Int? = prefs.driverId.takeIf { it > 0 },
    ): Result<ShiftSnapshot> =
        runCatching {
            val odo = VehicleState.state.value.odometerKm
            startOdo = odo
            integratedKm = 0.0
            lastSpeedTs = System.currentTimeMillis()
            clearSummary()
            com.veplayer.app.vehicle.DriverScoreMonitor.reset()
            val body =
                JSONObject()
                    .put("device_id", prefs.deviceId())
            if (driverId != null && driverId > 0) body.put("driver_id", driverId)
            if (odo != null) body.put("odo_km", odo.toDouble())
            val lat = prefs.navFromLat
            val lng = prefs.navFromLng
            body.put("lat", lat).put("lng", lng)
            val req =
                Request.Builder()
                    .url(prefs.senseflowUrl.trimEnd('/') + "/api/fleet/shifts/start")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("shift start HTTP ${resp.code}: $text")
                val s = parse(JSONObject(text).getJSONObject("shift"))
                _shift.value = s
                s
            }
        }

    fun end(prefs: VePrefs): Result<ShiftSnapshot> =
        runCatching {
            tickLocal(prefs)
            val odo = VehicleState.state.value.odometerKm
            val body =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("distance_km", integratedKm)
            if (odo != null) body.put("odo_km", odo.toDouble())
            body.put("lat", prefs.navFromLat).put("lng", prefs.navFromLng)
            val req =
                Request.Builder()
                    .url(prefs.senseflowUrl.trimEnd('/') + "/api/fleet/shifts/end")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("shift end HTTP ${resp.code}: $text")
                val json = JSONObject(text)
                val s = parse(json.getJSONObject("shift")).copy(status = "closed")
                val sum =
                    if (json.has("summary") && !json.isNull("summary")) {
                        ShiftSummary.fromJson(json.getJSONObject("summary"))
                    } else {
                        ShiftSummary.fromShift(s)
                    }
                clearLocal()
                _shift.value = s
                _summary.value = sum
                if (prefs.shiftSummaryEnabled && sum.show) {
                    val phrase = ShiftSummary.voicePhrase(sum)
                    if (prefs.shiftSummaryTts) {
                        com.veplayer.app.nav.NavTts.speakNow(phrase)
                    }
                    FleetInbox.push(
                        prefs = prefs,
                        kind = "shift_summary",
                        text = phrase,
                        severity = "info",
                        id = "shift_summary:${sum.shiftId}",
                        speak = false,
                    )
                }
                s
            }
        }

    fun applyFromHeartbeat(json: JSONObject?) {
        if (json == null || json === JSONObject.NULL) {
            return
        }
        runCatching {
            val s = parse(json)
            if (s.status == "open") {
                integratedKm = maxOf(integratedKm, s.distanceKm)
                _shift.value = s.copy(distanceKm = integratedKm)
            }
        }
    }

    fun deltaKmForHeartbeat(): Double {
        // Server accumulates; send small slice since last tick is already in integratedKm.
        // Prefer odometer path on server — send 0 delta when odo present.
        return if (startOdo != null) 0.0 else 0.0
    }

    private fun parse(o: JSONObject): ShiftSnapshot =
        ShiftSnapshot(
            id = o.optLong("id"),
            status = o.optString("status", "open"),
            distanceKm = o.optDouble("distance_km", 0.0),
            startedAt = o.optLong("started_at") * 1000L,
            endedAt = o.optLong("ended_at") * 1000L,
            driverCode = o.optString("driver_code", ""),
            driverName = o.optString("driver_name", ""),
            ecoScore = if (o.has("eco_score") && !o.isNull("eco_score")) o.optInt("eco_score") else null,
            ecoBand = o.optString("eco_band", ""),
            idleSec = o.optDouble("idle_sec", 0.0),
            overspeedSec = o.optDouble("overspeed_sec", 0.0),
            absEvents = o.optInt("abs_events", 0),
        )
}
