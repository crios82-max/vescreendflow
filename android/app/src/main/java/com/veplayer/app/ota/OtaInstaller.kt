package com.veplayer.app.ota

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.veplayer.app.data.VePrefs
import com.veplayer.app.kiosk.KioskController
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class OtaInstaller(private val context: Context) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    private val prefs = VePrefs(context)

    /**
     * Download APK and commit via PackageInstaller.
     * With Device Owner + USER_ACTION_NOT_REQUIRED → silent install of own package.
     */
    fun downloadAndInstall(
        apkUrl: String,
        targetVersionCode: Int? = null,
        silent: Boolean = true,
        onStatus: (String) -> Unit = {},
    ): Result<Unit> =
        runCatching {
            if (!busy.compareAndSet(false, true)) {
                error("OTA ya en curso")
            }
            try {
                if (
                    targetVersionCode != null &&
                    targetVersionCode <= prefs.lastOtaVersionCode &&
                    (prefs.lastOtaStatus == "ok" || prefs.lastOtaStatus.startsWith("committed"))
                ) {
                    onStatus("OTA ya aplicada ($targetVersionCode)")
                    return@runCatching
                }
                val owner = KioskController.isDeviceOwner(context)
                prefs.lastOtaStatus = "downloading"
                onStatus(if (owner && silent) "OTA silenciosa…" else "Descargando update…")

                val dir = File(context.cacheDir, "ota").apply { mkdirs() }
                val apk = File(dir, "veplayer-update.apk")
                val req = Request.Builder().url(apkUrl).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body ?: error("empty body")
                    apk.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                onStatus("Instalando (${apk.length() / 1024} KB)…")
                prefs.lastOtaStatus = "installing"
                installApk(apk, preferSilent = silent && owner)
                if (targetVersionCode != null) prefs.lastOtaVersionCode = targetVersionCode
                prefs.lastOtaStatus = "committed"
                onStatus(if (owner && silent) "OTA silent commit OK" else "Sesión de instalación enviada")
            } catch (e: Exception) {
                prefs.lastOtaStatus = "fail:${e.message?.take(80)}"
                throw e
            } finally {
                busy.set(false)
            }
        }

    private fun installApk(
        apk: File,
        preferSilent: Boolean,
    ) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(
                        if (preferSilent) {
                            PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                        } else {
                            PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                        },
                    )
                }
            }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("veplayer", 0, apk.length()).use { out ->
                apk.inputStream().use { input -> input.copyTo(out) }
                session.fsync(out)
            }
            val callback =
                PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(ACTION_INSTALL_STATUS).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            session.commit(callback.intentSender)
        }
        Log.i(TAG, "PackageInstaller session $sessionId committed silent=$preferSilent")
    }

    companion object {
        private const val TAG = "OtaInstaller"
        const val ACTION_INSTALL_STATUS = "com.veplayer.app.OTA_INSTALL_STATUS"
        private val busy = AtomicBoolean(false)

        fun isBusy(): Boolean = busy.get()
    }
}
