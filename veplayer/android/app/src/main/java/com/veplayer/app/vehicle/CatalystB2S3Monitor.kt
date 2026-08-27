package com.veplayer.app.vehicle

import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetInbox
import com.veplayer.app.nav.NavTts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CatalystB2S3Monitor {
    private val _state = MutableStateFlow(CatalystB2S3.State())
    val state: StateFlow<CatalystB2S3.State> = _state.asStateFlow()
    private var warnSinceMs = 0L; private var lastSpokenMs = 0L; private var lastKey = ""

    fun tick(prefs: VePrefs, signals: VehicleSignals, nowMs: Long = System.currentTimeMillis()) {
        val c = if (prefs.catB2s3SimC > 0f) prefs.catB2s3SimC else signals.catalystB2s3TempC
        val st = CatalystB2S3.evaluate(c, prefs.catB2s3WarnC, prefs.catB2s3AlertC)
        if (!prefs.catB2s3Enabled) { _state.value = st.copy(showWarn = false); return }
        _state.value = st
        if (!st.showWarn) { warnSinceMs = 0L; lastKey = ""; return }
        if (warnSinceMs == 0L) warnSinceMs = nowMs
        val key = "${st.band}:${(st.catalystTempC ?: 0f).toInt() / 20}"
        if (nowMs - warnSinceMs >= 2000 && (nowMs - lastSpokenMs >= 30000 || key != lastKey) && prefs.catB2s3Tts) {
            lastSpokenMs = nowMs; lastKey = key
            val phrase = CatalystB2S3.voicePhrase(st)
            NavTts.speakNow(phrase)
            FleetInbox.push(prefs, if (st.band == "alert") "cat_b2s3_alert" else "cat_b2s3_warn", phrase,
                if (st.band == "alert") "critical" else "warn", "cat_b2s3:${st.band}:${nowMs / 60000}", false)
        }
    }
}
