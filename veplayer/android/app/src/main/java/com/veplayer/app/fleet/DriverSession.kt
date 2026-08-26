package com.veplayer.app.fleet

import com.veplayer.app.data.VePrefs
import com.veplayer.app.nav.NavDestination
import com.veplayer.app.nav.NavEngine
import kotlinx.coroutines.CoroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class DriverProfile(
    val id: Int,
    val code: String,
    val name: String,
    val language: String = "es",
    val preferredDest: String? = null,
    val preferredLat: Double? = null,
    val preferredLng: Double? = null,
    val hasPin: Boolean = false,
)

/**
 * Local driver session + SenseFlow login/list.
 */
object DriverSession {
    private val http =
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun clear(prefs: VePrefs) {
        prefs.driverId = 0
        prefs.driverCode = ""
        prefs.driverName = ""
        prefs.driverLanguage = "es"
    }

    fun apply(
        prefs: VePrefs,
        driver: DriverProfile,
        scope: CoroutineScope? = null,
        applyPreferredDest: Boolean = true,
    ) {
        prefs.driverId = driver.id
        prefs.driverCode = driver.code
        prefs.driverName = driver.name
        prefs.driverLanguage = driver.language
        if (applyPreferredDest &&
            driver.preferredLat != null &&
            driver.preferredLng != null &&
            !driver.preferredDest.isNullOrBlank()
        ) {
            val dest =
                NavDestination(
                    id = "driver-${driver.code}",
                    name = driver.preferredDest,
                    lat = driver.preferredLat,
                    lng = driver.preferredLng,
                )
            NavEngine.setDestination(dest, scope)
        }
    }

    fun fromPrefs(prefs: VePrefs): DriverProfile? {
        if (prefs.driverId <= 0 || prefs.driverCode.isBlank()) return null
        return DriverProfile(
            id = prefs.driverId,
            code = prefs.driverCode,
            name = prefs.driverName.ifBlank { prefs.driverCode },
            language = prefs.driverLanguage,
        )
    }

    fun list(prefs: VePrefs): Result<List<DriverProfile>> =
        runCatching {
            val req =
                Request.Builder()
                    .url(prefs.senseflowUrl.trimEnd('/') + "/api/fleet/drivers")
                    .get()
                    .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("drivers HTTP ${resp.code}: $text")
                val arr = JSONObject(text).optJSONArray("drivers") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        parse(arr.getJSONObject(i))?.let { add(it) }
                    }
                }
            }
        }

    fun login(
        prefs: VePrefs,
        code: String,
        pin: String?,
        scope: CoroutineScope? = null,
    ): Result<DriverProfile> =
        runCatching {
            val body =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .put("code", code.trim())
            if (!pin.isNullOrBlank()) body.put("pin", pin)
            val req =
                Request.Builder()
                    .url(prefs.senseflowUrl.trimEnd('/') + "/api/fleet/drivers/login")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error(JSONObject(text).optString("error", "login HTTP ${resp.code}"))
                val driver =
                    parse(JSONObject(text).getJSONObject("driver"))
                        ?: error("driver vacío")
                apply(prefs, driver, scope)
                driver
            }
        }

    fun logout(prefs: VePrefs): Result<Unit> =
        runCatching {
            val body =
                JSONObject()
                    .put("device_id", prefs.deviceId())
                    .toString()
                    .toRequestBody(JSON)
            val req =
                Request.Builder()
                    .url(prefs.senseflowUrl.trimEnd('/') + "/api/fleet/drivers/logout")
                    .post(body)
                    .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("logout HTTP ${resp.code}")
            }
            clear(prefs)
        }

    fun parse(o: JSONObject): DriverProfile? {
        val id = o.optInt("id", 0)
        val code = o.optString("code", "")
        if (id <= 0 || code.isBlank()) return null
        return DriverProfile(
            id = id,
            code = code,
            name = o.optString("name", code),
            language = o.optString("language", "es"),
            preferredDest = o.optString("preferred_dest").takeIf { it.isNotBlank() },
            preferredLat = if (o.has("preferred_lat") && !o.isNull("preferred_lat")) o.optDouble("preferred_lat") else null,
            preferredLng = if (o.has("preferred_lng") && !o.isNull("preferred_lng")) o.optDouble("preferred_lng") else null,
            hasPin = o.optBoolean("has_pin", false),
        )
    }
}
