package com.veplayer.app

import android.app.Application
import android.content.Intent
import android.util.Log
import com.veplayer.app.media.VeMediaHub
import com.veplayer.app.nav.NavEngine
import com.veplayer.app.vehicle.CanBusManager
import com.veplayer.app.watchdog.WatchdogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VePlayerApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        VeMediaHub.init(this)
        CanBusManager.start(this)
        NavEngine.start(com.veplayer.app.data.VePrefs(this), appScope)
        com.veplayer.app.nav.NavTts.start(this, com.veplayer.app.data.VePrefs(this), appScope)
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e(TAG, "Uncaught in ${t.name}", e)
            runCatching {
                WatchdogService.start(this)
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
            default?.uncaughtException(t, e)
        }
        WatchdogService.start(this)
    }

    companion object {
        private const val TAG = "VePlayerApp"
    }
}
