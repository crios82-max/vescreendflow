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
import android.util.Log
import com.veplayer.app.MainActivity
import com.veplayer.app.data.VePrefs

object KioskController {
    private const val TAG = "KioskController"

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

    /**
     * Hard kiosk policies when we are Device Owner.
     * Safe no-op if the app is not owner.
     */
    fun applyOwnerPolicies(context: Context) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = adminComponent(context)

        dpm.setLockTaskPackages(
            admin,
            arrayOf(
                context.packageName,
                "com.spotify.music",
                "com.google.android.youtube",
            ),
        )

        // Hard lock-task: no Home / Overview escape (SYSTEM_INFO only for clock/battery).
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO,
                )
            }
        }

        runCatching {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            // Keep unknown-sources open for fleet PackageInstaller OTA.
            if (Build.VERSION.SDK_INT >= 26) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_BLUETOOTH_SHARING)
            }
        }

        runCatching { dpm.setUninstallBlocked(admin, context.packageName, true) }

        if (Build.VERSION.SDK_INT >= 23) {
            runCatching { dpm.setKeyguardDisabled(admin, true) }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching { dpm.setStatusBarDisabled(admin, true) }
        }

        // Prefer VePlayer as Home.
        runCatching {
            val filter = IntentFilterHome()
            dpm.addPersistentPreferredActivity(
                admin,
                filter.intentFilter,
                ComponentName(context, MainActivity::class.java),
            )
        }

        Log.i(TAG, "Owner policies applied")
        VePrefs(context).kioskPoliciesAppliedAt = System.currentTimeMillis()
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

    /** Exit lock-task only after PIN check (Settings / remote unlock). */
    fun exitWithPin(activity: Activity, pin: String): Boolean {
        val prefs = VePrefs(activity)
        if (!prefs.checkPin(pin)) return false
        stopLockTask(activity)
        return true
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

    /** Checklist for Settings / fleet telemetry. */
    fun playbookLines(context: Context): List<String> {
        val owner = isDeviceOwner(context)
        val lock = isLockTaskActive(context)
        val pkg = context.packageName
        return listOf(
            if (owner) "✓ Device Owner ($pkg)" else "○ Device Owner — adb dpm set-device-owner …/VeDeviceAdminReceiver",
            if (lock) "✓ Lock Task activo" else "○ Lock Task — abrir VePlayer o cmd lock",
            "○ Boot auto — BootReceiver + Watchdog",
            if (VePrefs(context).autoOtaEnabled) "✓ OTA silenciosa (flota)" else "○ OTA auto desactivada",
        )
    }

    fun healthSnapshot(context: Context): Map<String, Any?> {
        val prefs = VePrefs(context)
        return mapOf(
            "device_owner" to isDeviceOwner(context),
            "lock_task" to isLockTaskActive(context),
            "auto_ota" to prefs.autoOtaEnabled,
            "last_ota_status" to prefs.lastOtaStatus,
            "last_ota_version_code" to prefs.lastOtaVersionCode,
            "watchdog_relaunches" to prefs.watchdogRelaunchCount,
            "policies_at" to prefs.kioskPoliciesAppliedAt,
        )
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
            com.veplayer.app.watchdog.WatchdogService.start(context)
            val launch =
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(launch)
        }
    }
}
