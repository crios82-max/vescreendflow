package com.veplayer.app.ota

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import com.veplayer.app.data.VePrefs
import com.veplayer.app.kiosk.KioskController

class OtaInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != OtaInstaller.ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val prefs = VePrefs(context)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Device Owner should rarely hit this; still launch confirm if present.
                prefs.lastOtaStatus = if (KioskController.isDeviceOwner(context)) {
                    "pending_user(owner?)"
                } else {
                    "pending_user"
                }
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                prefs.lastOtaStatus = "ok"
                Toast.makeText(context, "OTA instalada", Toast.LENGTH_LONG).show()
                Log.i(TAG, "OTA success")
            }
            else -> {
                prefs.lastOtaStatus = "fail:$status ${msg.orEmpty()}".take(120)
                Toast.makeText(context, "OTA falló: $msg", Toast.LENGTH_LONG).show()
                Log.w(TAG, "OTA status=$status msg=$msg")
            }
        }
    }

    companion object {
        private const val TAG = "OtaInstallReceiver"
    }
}
