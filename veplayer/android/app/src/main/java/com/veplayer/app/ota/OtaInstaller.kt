package com.veplayer.app.ota

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class OtaInstaller(private val context: Context) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

    /**
     * Download APK and commit via PackageInstaller session.
     * Requires REQUEST_INSTALL_PACKAGES (and user approval unless Device Owner).
     */
    fun downloadAndInstall(apkUrl: String, onStatus: (String) -> Unit): Result<Unit> =
        runCatching {
            onStatus("Descargando update…")
            val dir = File(context.cacheDir, "ota").apply { mkdirs() }
            val apk = File(dir, "veplayer-update.apk")
            val req = Request.Builder().url(apkUrl).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("empty body")
                apk.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            onStatus("Instalando (${apk.length() / 1024} KB)…")
            installApk(apk)
            onStatus("Sesión de instalación enviada")
        }

    private fun installApk(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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
        Log.i(TAG, "PackageInstaller session $sessionId committed")
    }

    companion object {
        private const val TAG = "OtaInstaller"
        const val ACTION_INSTALL_STATUS = "com.veplayer.app.OTA_INSTALL_STATUS"
    }
}
