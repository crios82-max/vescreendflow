package com.veplayer.app.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.veplayer.app.MainActivity

object KioskController {
    fun tryStartLockTask(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                val dpm = activity.getSystemService(DevicePolicyManager::class.java)
                if (dpm?.isLockTaskPermitted(activity.packageName) == true) {
                    activity.startLockTask()
                    return
                }
            }
            // Soft kiosk: still immersive; Device Owner whitelist enables hard lock later.
            activity.startLockTask()
        } catch (_: Exception) {
            // Not whitelisted yet — immersive UI still works.
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val launch =
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(launch)
        }
    }
}
