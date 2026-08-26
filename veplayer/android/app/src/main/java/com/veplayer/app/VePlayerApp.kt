package com.veplayer.app

import android.app.Application
import android.content.Intent
import android.util.Log
import com.veplayer.app.media.VeMediaHub
import com.veplayer.app.vehicle.CanBusManager
import com.veplayer.app.watchdog.WatchdogService

class VePlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VeMediaHub.init(this)
        CanBusManager.start(this)
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
