package com.veplayer.app.phone

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.veplayer.app.data.VePrefs
import com.veplayer.app.media.VeMediaHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects Bluetooth phones + optional Android Auto / CarPlay host packages.
 * Demo sim modes for fleet smoke without OEM projection stacks.
 *
 * Nota: host completo AA/CarPlay requiere ROM OEM / MFi. Aquí: pairing BT,
 * detección de paquetes host, simulación demo y estado para flota/UI.
 */
object PhoneLinkManager {
    private const val TAG = "PhoneLink"
    private val AA_PACKAGES =
        listOf(
            "com.google.android.projection.gearhead",
            "com.google.android.gms.car",
            "com.google.android.embedded.projection",
        )
    private val CARPLAY_HINT_PACKAGES =
        listOf(
            "com.carplay.receiver",
            "com.autokit.carplay",
            "com.explay.carplay",
            "com.apple.android.music",
        )

    private var app: Context? = null
    private var prefs: VePrefs? = null
    private var job: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        app = context.applicationContext
        prefs = VePrefs(context.applicationContext)
        if (job?.isActive == true) return
        job =
            scope.launch(Dispatchers.Default) {
                while (isActive) {
                    tick()
                    delay(2_500)
                }
            }
        Log.i(TAG, "phone link started")
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun simulate(protocol: PhoneLinkBus.Protocol) {
        prefs?.phoneLinkSim = protocol.name.lowercase()
        tick()
    }

    fun clearSim() {
        prefs?.phoneLinkSim = "none"
        tick()
    }

    fun openAndroidAutoSettings(): Boolean {
        val ctx = app ?: return false
        for (pkg in AA_PACKAGES) {
            val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(launch) }
                return true
            }
        }
        return openBluetoothSettings()
    }

    fun openBluetoothSettings(): Boolean {
        val ctx = app ?: return false
        return runCatching {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun tick() {
        val ctx = app ?: return
        val p = prefs ?: return
        if (!p.phoneLinkEnabled) {
            PhoneLinkBus.publish(PhoneLinkBus.State(enabled = false, statusText = "Phone Link off"))
            return
        }

        val aaHost = packagePresent(ctx, AA_PACKAGES)
        val cpHost = packagePresent(ctx, CARPLAY_HINT_PACKAGES)
        val sim = p.phoneLinkSim.lowercase()

        if (sim in setOf("android_auto", "carplay", "bt_media")) {
            val proto =
                when (sim) {
                    "android_auto" -> PhoneLinkBus.Protocol.ANDROID_AUTO
                    "carplay" -> PhoneLinkBus.Protocol.CARPLAY
                    else -> PhoneLinkBus.Protocol.BT_MEDIA
                }
            val name =
                when (proto) {
                    PhoneLinkBus.Protocol.ANDROID_AUTO -> "Pixel (sim AA)"
                    PhoneLinkBus.Protocol.CARPLAY -> "iPhone (sim CarPlay)"
                    else -> "Teléfono BT (sim)"
                }
            val st =
                PhoneLinkBus.State(
                    enabled = true,
                    connected = true,
                    protocol = proto,
                    deviceName = name,
                    mediaTitle = "Demo · Phone Link",
                    mediaArtist = name,
                    playing = true,
                    aaHostAvailable = aaHost,
                    carplayHostAvailable = cpHost,
                    statusText = "Sim · ${proto.name}",
                    simulated = true,
                )
            PhoneLinkBus.publish(st)
            VeMediaHub.publishPhone(
                title = st.mediaTitle,
                artist = st.mediaArtist,
                playing = true,
                status = st.statusText,
            )
            return
        }

        val btName = bondedPhoneName(ctx)
        val connected = btName != null
        val protocol =
            when {
                aaHost && connected -> PhoneLinkBus.Protocol.ANDROID_AUTO
                cpHost && connected -> PhoneLinkBus.Protocol.CARPLAY
                connected -> PhoneLinkBus.Protocol.BT_MEDIA
                else -> PhoneLinkBus.Protocol.NONE
            }
        val st =
            PhoneLinkBus.State(
                enabled = true,
                connected = connected,
                protocol = protocol,
                deviceName = btName.orEmpty(),
                mediaTitle = if (connected) "Audio Bluetooth" else "",
                mediaArtist = btName.orEmpty(),
                playing = connected,
                aaHostAvailable = aaHost,
                carplayHostAvailable = cpHost,
                statusText =
                    when {
                        !connected && !aaHost && !cpHost ->
                            "Pareá teléfono BT · host AA/CarPlay = OEM"
                        !connected && aaHost -> "Android Auto host · esperando teléfono"
                        !connected && cpHost -> "CarPlay receiver · esperando iPhone"
                        protocol == PhoneLinkBus.Protocol.ANDROID_AUTO -> "Android Auto · $btName"
                        protocol == PhoneLinkBus.Protocol.CARPLAY -> "CarPlay · $btName"
                        connected -> "BT media · $btName"
                        else -> "Sin teléfono"
                    },
                simulated = false,
            )
        PhoneLinkBus.publish(st)
        if (st.connected) {
            VeMediaHub.publishPhone(
                title = st.mediaTitle,
                artist = st.mediaArtist,
                playing = st.playing,
                status = st.statusText,
            )
        }
    }

    private fun packagePresent(ctx: Context, pkgs: List<String>): Boolean {
        val pm = ctx.packageManager
        return pkgs.any {
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(it, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(it, 0)
                }
                true
            }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun bondedPhoneName(ctx: Context): String? {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (!adapter.isEnabled) return null
        return runCatching {
            adapter.bondedDevices
                ?.firstOrNull {
                    val n = it.name?.lowercase().orEmpty()
                    n.contains("iphone") ||
                        n.contains("pixel") ||
                        n.contains("galaxy") ||
                        n.contains("phone") ||
                        n.contains("android") ||
                        n.contains("oneplus") ||
                        n.contains("xiaomi") ||
                        true // any bonded device as phone candidate on HU
                }?.name
        }.getOrNull()
    }
}
