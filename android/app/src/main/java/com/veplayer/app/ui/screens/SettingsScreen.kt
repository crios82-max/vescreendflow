package com.veplayer.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.veplayer.app.BuildConfig
import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetClient
import com.veplayer.app.kiosk.KioskController
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.vehicle.CanBusManager
import com.veplayer.app.vehicle.ObdLinkBus
import com.veplayer.app.vehicle.SignalSourceKind
import com.veplayer.app.vehicle.VehicleState
import com.veplayer.app.vehicle.can.CanBackend
import com.veplayer.app.vehicle.can.CanLinkBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    val fleet = remember { FleetClient(prefs) }
    val scope = rememberCoroutineScope()

    var unlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    var senseUrl by remember { mutableStateOf(prefs.senseflowUrl) }
    var playerUrl by remember { mutableStateOf(prefs.playerUrl) }
    var deviceName by remember { mutableStateOf(prefs.deviceName) }
    var blockKmh by remember { mutableStateOf(prefs.videoSpeedBlockKmh.toString()) }
    var newPin by remember { mutableStateOf("") }
    var mockReverse by remember { mutableStateOf(prefs.mockReverse) }
    var mockSpeed by remember { mutableStateOf(prefs.mockSpeedKmh.toString()) }
    var signalSource by remember { mutableStateOf(prefs.signalSource) }
    var obdAddr by remember { mutableStateOf(prefs.obdDeviceAddress) }
    var canBackend by remember { mutableStateOf(prefs.canBackend) }
    var canIface by remember { mutableStateOf(prefs.canSocketIface) }
    var pairCode by remember { mutableStateOf(prefs.pairCodeCached() ?: "—") }
    var otaText by remember { mutableStateOf("OTA: sin chequear") }
    var autoOta by remember { mutableStateOf(prefs.autoOtaEnabled) }
    val live by VehicleState.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, color = Mist, fontWeight = FontWeight.Bold)
        Text("PIN · flota · OTA · CAN / señales", color = Mute)

        if (!unlocked) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Desbloquear (PIN)", color = Teal, fontWeight = FontWeight.Bold)
                Text("Default de fábrica: ${VePrefs.DEFAULT_PIN}", color = Mute)
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (prefs.checkPin(pinInput)) {
                            unlocked = true
                            status = "Desbloqueado"
                        } else {
                            status = "PIN incorrecto"
                        }
                    },
                ) { Text("Entrar") }
                Text(status, color = Mute)
            }
            return
        }

        PanelBlock("Kiosk") {
            Text(KioskController.statusLabel(context), color = Mist)
            Text("Device: ${prefs.deviceId().take(12)}…", color = Mute)
            Text("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Mute)
            KioskController.playbookLines(context).forEach { Text(it, color = Mute) }
            Text(
                "Watchdog relaunches: ${prefs.watchdogRelaunchCount} · OTA: ${prefs.lastOtaStatus}",
                color = Mute,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("OTA auto (flota)", color = Mist)
                Switch(
                    checked = autoOta,
                    onCheckedChange = {
                        autoOta = it
                        prefs.autoOtaEnabled = it
                        status = if (it) "OTA auto ON" else "OTA auto OFF"
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        KioskController.applyOwnerPolicies(context)
                        status = "Políticas kiosk aplicadas"
                    },
                ) { Text("Aplicar políticas") }
                OutlinedButton(
                    onClick = {
                        val act = context as? android.app.Activity
                        if (act != null) KioskController.tryStartLockTask(act)
                        status = "Lock Task solicitado"
                    },
                ) { Text("Lock Task") }
            }
        }

        PanelBlock("Flota / pairing") {
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Nombre unidad") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Código de empareje: $pairCode", color = Mist, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            status = "Registrando…"
                            val r = withContext(Dispatchers.IO) { fleet.register() }
                            r.onSuccess {
                                pairCode = it
                                status = "Registrado · pair $it"
                            }.onFailure { status = it.message ?: "Error register" }
                        }
                    },
                ) { Text("Registrar / renovar código") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { fleet.heartbeat() }
                            r.onSuccess { hb ->
                                val ota = hb.ota
                                if (ota == null) {
                                    otaText = "Heartbeat OK · sin OTA · cmds ${hb.commands.size}"
                                } else {
                                    otaText =
                                        if (ota.updateAvailable) {
                                            "Update ${ota.latestVersionName} disponible · cmds ${hb.commands.size}"
                                        } else {
                                            "Al día (${ota.latestVersionName}) · cmds ${hb.commands.size}"
                                        }
                                    status = otaText
                                }
                            }.onFailure { status = it.message ?: "Error heartbeat" }
                        }
                    },
                ) { Text("Heartbeat") }
            }
        }

        PanelBlock("OTA") {
            Text(otaText, color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { fleet.heartbeat() }
                            r.onSuccess { hb ->
                                val ota = hb.ota
                                if (ota?.updateAvailable == true && !ota.apkUrl.isNullOrBlank()) {
                                    otaText = "Descargando ${ota.latestVersionName}…"
                                    val install =
                                        withContext(Dispatchers.IO) {
                                            com.veplayer.app.ota.OtaInstaller(context)
                                                .downloadAndInstall(ota.apkUrl!!) { otaText = it }
                                        }
                                    install
                                        .onSuccess { status = "OTA enviada a PackageInstaller" }
                                        .onFailure {
                                            status = it.message ?: "OTA fail"
                                            // Fallback: open URL
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(ota.apkUrl)),
                                                )
                                            }
                                        }
                                } else {
                                    status = "Sin update pendiente"
                                    otaText = "Sin update"
                                }
                            }.onFailure { status = it.message ?: "OTA check fail" }
                        }
                    },
                ) { Text("Buscar e instalar update") }
            }
        }

        PanelBlock("URLs") {
            OutlinedTextField(
                value = senseUrl,
                onValueChange = { senseUrl = it },
                label = { Text("SenseFlow / Fleet API") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = playerUrl,
                onValueChange = { playerUrl = it },
                label = { Text("Player URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = blockKmh,
                onValueChange = { blockKmh = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Bloquear video ≥ km/h") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PanelBlock("Señales vehículo (CAN / OBD / GPS)") {
            Text("Fuente activa: ${SignalSourceKind.fromId(signalSource).label}", color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SignalSourceKind.entries.forEach { kind ->
                    val selected = signalSource == kind.id
                    if (selected) {
                        Button(
                            onClick = {
                                signalSource = kind.id
                                prefs.signalSource = kind.id
                                CanBusManager.rebind()
                                status = "Fuente → ${kind.label}"
                            },
                        ) { Text(kind.id.uppercase()) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                signalSource = kind.id
                                prefs.signalSource = kind.id
                                CanBusManager.rebind()
                                status = "Fuente → ${kind.label}"
                            },
                        ) { Text(kind.id.uppercase()) }
                    }
                }
            }
            Text("CAN backend:", color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CanBackend.entries.forEach { b ->
                    val selected = canBackend == b.id
                    if (selected) {
                        Button(
                            onClick = {
                                canBackend = b.id
                                prefs.canBackend = b.id
                                if (signalSource == "can") CanBusManager.rebind()
                                status = "CAN → ${b.label}"
                            },
                        ) { Text(b.id) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                canBackend = b.id
                                prefs.canBackend = b.id
                                if (signalSource == "can") CanBusManager.rebind()
                                status = "CAN → ${b.label}"
                            },
                        ) { Text(b.id) }
                    }
                }
            }
            OutlinedTextField(
                value = canIface,
                onValueChange = { canIface = it },
                label = { Text("SocketCAN iface") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val canLink by CanLinkBus.state.collectAsState()
            Text("CAN link: ${canLink.state} · ${canLink.text}", color = Mute)
            var usbList by remember { mutableStateOf(CanBusManager.usbCanDevices()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { usbList = CanBusManager.usbCanDevices() }) {
                    Text("Refresh USB")
                }
            }
            if (usbList.isEmpty()) {
                Text("USB CAN: ninguno conectado", color = Mute)
            } else {
                usbList.forEach { Text("USB · $it", color = Mute) }
            }
            OutlinedTextField(
                value = obdAddr,
                onValueChange = { obdAddr = it },
                label = { Text("OBD ELM327 MAC") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val obdLink by ObdLinkBus.state.collectAsState()
            Text("OBD link: ${obdLink.state} · ${obdLink.text}", color = Mute)
            var bonded by remember { mutableStateOf(CanBusManager.bondedObdDevices()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Emparejados (Bluetooth Classic):", color = Mist)
                OutlinedButton(onClick = { bonded = CanBusManager.bondedObdDevices() }) {
                    Text("Refresh BT")
                }
            }
            if (bonded.isEmpty()) {
                Text("Ninguno · emparejá el ELM327 en Ajustes del sistema", color = Mute)
            } else {
                bonded.forEach { d ->
                    OutlinedButton(
                        onClick = {
                            obdAddr = d.address
                            prefs.obdDeviceAddress = d.address
                            prefs.signalSource = "obd"
                            signalSource = "obd"
                            CanBusManager.rebind()
                            status = "OBD → ${d.name} (${d.address})"
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${d.name} · ${d.address}") }
                }
            }
            Text(
                buildString {
                    append("Live · ${live.speedKmh.toInt()} km/h · gear ${live.gear}")
                    append(" · turn ${live.turn}")
                    append(" · src ${live.source}")
                    live.batterySocPct?.let { append(" · SOC ${it.toInt()}%") }
                    live.rpm?.let { append(" · ${it.toInt()} rpm") }
                    live.fuelPct?.let { append(" · fuel ${it.toInt()}%") }
                    live.headingDeg?.let { append(" · hdg ${it.toInt()}°") }
                    if (live.absActive) append(" · ABS")
                    if (live.tpmsLow) append(" · TPMS low")
                    live.hvacCabinC?.let { append(" · cabin ${"%.0f".format(it)}°C") }
                    if (live.hvacAcOn) append(" · AC")
                    if (live.anyDoorOpen) append(" · puerta abierta")
                    if (live.parkingBrake) append(" · freno parking")
                },
                color = Mute,
            )
            live.tpmsFlPsi?.let {
                Text(
                    "TPMS psi FL/FR/RL/RR: ${fmtPsi(live.tpmsFlPsi)} / ${fmtPsi(live.tpmsFrPsi)} / ${fmtPsi(live.tpmsRlPsi)} / ${fmtPsi(live.tpmsRrPsi)}",
                    color = Mute,
                )
            }
        }

        PanelBlock("Navegación") {
            var navOn by remember { mutableStateOf(prefs.navEnabled) }
            var destName by remember { mutableStateOf(prefs.navDestName) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nav activa", color = Mist)
                Switch(
                    checked = navOn,
                    onCheckedChange = {
                        navOn = it
                        prefs.navEnabled = it
                        com.veplayer.app.nav.NavEngine.refreshAsync(scope)
                        status = if (it) "Nav ON" else "Nav OFF"
                    },
                )
            }
            Text("Destino rápido (Caracas demo)", color = Mute)
            val presets =
                listOf(
                    Triple("Altamira", 10.4965, -66.8492),
                    Triple("Chacao", 10.4958, -66.8756),
                    Triple("Bellas Artes", 10.4989, -66.8986),
                    Triple("Aeropuerto", 10.6013, -66.9912),
                )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { (name, lat, lng) ->
                    val selected = destName == name
                    val click = {
                        destName = name
                        prefs.navDestName = name
                        prefs.navToLat = lat
                        prefs.navToLng = lng
                        com.veplayer.app.nav.NavEngine.refreshAsync(scope)
                        status = "Destino → $name"
                    }
                    if (selected) Button(onClick = click) { Text(name) }
                    else OutlinedButton(onClick = click) { Text(name) }
                }
            }
            Text("Actual: $destName (${prefs.navToLat}, ${prefs.navToLng})", color = Mute)
            OutlinedButton(
                onClick = {
                    com.veplayer.app.nav.NavEngine.refreshAsync(scope)
                    status = "Ruta refrescada"
                },
            ) { Text("Recalcular ruta") }
        }

        PanelBlock("Mock vehículo (demo)") {
            Text("Aplica sobre mock / can_stub / obd_sim (no pisa GPS real).", color = Mute)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Marcha atrás", color = Mist)
                Switch(
                    checked = mockReverse,
                    onCheckedChange = {
                        mockReverse = it
                        prefs.mockReverse = it
                        if (signalSource == "gps") {
                            VehicleState.applyMock(mockSpeed.toFloatOrNull() ?: 0f, it)
                        } else {
                            CanBusManager.rebind()
                        }
                    },
                )
            }
            OutlinedTextField(
                value = mockSpeed,
                onValueChange = { mockSpeed = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Velocidad mock km/h") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val kmh = mockSpeed.toFloatOrNull() ?: 0f
                    prefs.mockSpeedKmh = kmh
                    prefs.mockReverse = mockReverse
                    if (signalSource == "gps") {
                        VehicleState.applyMock(kmh, mockReverse)
                    } else {
                        CanBusManager.rebind()
                    }
                    status = "Mock: ${kmh} km/h reverse=$mockReverse"
                },
            ) { Text("Aplicar mock") }
        }

        PanelBlock("PIN") {
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("Nuevo PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = {
                prefs.senseflowUrl = senseUrl
                prefs.playerUrl = playerUrl
                prefs.deviceName = deviceName
                prefs.videoSpeedBlockKmh = blockKmh.toFloatOrNull() ?: 8f
                prefs.signalSource = signalSource
                prefs.obdDeviceAddress = obdAddr
                prefs.canBackend = canBackend
                prefs.canSocketIface = canIface
                if (newPin.length >= 4) prefs.pin = newPin
                CanBusManager.rebind()
                status = "Guardado · fuente ${prefs.signalSource} · can ${prefs.canBackend}"
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Guardar ajustes") }

        Text(status, color = Mute)
    }
}

@Composable
private fun PanelBlock(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Teal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

private fun fmtPsi(v: Float?): String = v?.let { "%.1f".format(it) } ?: "—"
