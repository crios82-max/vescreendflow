package com.veplayer.app.ota

import android.content.Context
import android.util.Log
import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.OtaInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies fleet OTA from heartbeat when auto-OTA is enabled.
 * Dedupes by version_code so we don't hammer downloads.
 */
object SilentOtaCoordinator {
    private val mutex = Mutex()
    private const val TAG = "SilentOta"

    suspend fun maybeApply(
        context: Context,
        ota: OtaInfo?,
        onStatus: (String) -> Unit = {},
    ) {
        if (ota == null || !ota.updateAvailable) return
        val prefs = VePrefs(context)
        if (!prefs.autoOtaEnabled) return
        val url = ota.apkUrl?.takeIf { it.isNotBlank() } ?: return
        val code = ota.latestVersionCode ?: return
        if (code <= com.veplayer.app.BuildConfig.VERSION_CODE) return
        if (code <= prefs.lastOtaVersionCode &&
            (prefs.lastOtaStatus == "ok" || prefs.lastOtaStatus.startsWith("committed"))
        ) {
            return
        }
        if (OtaInstaller.isBusy()) return

        mutex.withLock {
            if (OtaInstaller.isBusy()) return
            Log.i(TAG, "auto OTA → $code ${ota.latestVersionName}")
            onStatus("Auto-OTA ${ota.latestVersionName}")
            OtaInstaller(context).downloadAndInstall(
                apkUrl = url,
                targetVersionCode = code,
                silent = true,
                onStatus = onStatus,
            )
        }
    }
}
