package com.veplayer.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import com.veplayer.app.MainActivity

object KioskController {
    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, VeDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isLockTaskActive(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun applyOwnerPolicies(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = adminComponent(context)

        // Whitelist this package (+ Spotify / YouTube) for lock-task.
        dpm.setLockTaskPackages(
            admin,
            arrayOf(
                context.packageName,
                "com.spotify.music",
                "com.google.android.youtube",
            ),
        )

        if (Build.VERSION.SDK_INT >= 28) {
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                    DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW,
            )
        }

        // Keep vehicle player focused: block status-bar expansion / safe boot noise.
        runCatching {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
        }

        // Stay as preferred home (best-effort).
        runCatching {
            val filter =
                IntentFilterHome()
            dpm.addPersistentPreferredActivity(
                admin,
                filter.intentFilter,
                ComponentName(context, MainActivity::class.java),
            )
        }
    }

    fun tryStartLockTask(activity: Activity) {
        applyOwnerPolicies(activity)
        try {
            activity.startLockTask()
        } catch (_: Exception) {
            // Soft immersive still works without Device Owner.
        }
    }

    fun stopLockTask(activity: Activity) {
        try {
            activity.stopLockTask()
        } catch (_: Exception) {
        }
    }

    fun statusLabel(context: Context): String {
        val owner = isDeviceOwner(context)
        val lock = isLockTaskActive(context)
        return when {
            owner && lock -> "Kiosk duro · Device Owner + Lock Task"
            owner -> "Device Owner activo · Lock Task pendiente"
            lock -> "Lock Task (soft) · sin Device Owner"
            else -> "Modo normal · activa Device Owner para kiosk duro"
        }
    }
}

/** Tiny helper so IntentFilter construction stays readable. */
private class IntentFilterHome {
    val intentFilter =
        android.content.IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            KioskController.applyOwnerPolicies(context)
            val launch =
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(launch)
        }
    }
}
