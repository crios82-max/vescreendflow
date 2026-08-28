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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.veplayer.app.BuildConfig
import com.veplayer.app.data.VePrefs
import com.veplayer.app.fleet.FleetClient
import com.veplayer.app.kiosk.KioskController
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Panel
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.brand.BrandBus
import com.veplayer.app.brand.BrandRepository
import com.veplayer.app.ui.brand.BrandLogo
import androidx.compose.ui.graphics.Color
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
    var fmBackend by remember { mutableStateOf(prefs.fmBackend) }
    var pairCode by remember { mutableStateOf(prefs.pairCodeCached() ?: "—") }
    var otaText by remember { mutableStateOf("OTA: sin chequear") }
    var autoOta by remember { mutableStateOf(prefs.autoOtaEnabled) }
    var diagText by remember { mutableStateOf(prefs.lastFieldDiag.ifBlank { "Sin diagnóstico aún" }) }
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

        PanelBlock("Campo (HW)") {
            Text(diagText, color = Mute)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            status = "Diagnosticando…"
                            val report =
                                withContext(Dispatchers.IO) {
                                    com.veplayer.app.field.FieldDiagnostics.collect(context)
                                }
                            prefs.lastFieldDiag = report.asText()
                            diagText = report.asText()
                            status = "Diag OK"
                        }
                    },
                ) { Text("Diagnóstico") }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, prefs.lastFieldDiag.ifBlank { diagText })
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            context.startActivity(Intent.createChooser(send, "Diag VePlayer"))
                        }
                        status = "Compartir diag"
                    },
                ) { Text("Compartir") }
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
                OutlinedButton(
                    onClick = {
                        val url = senseUrl.trimEnd('/') + "/fleet.html"
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }.onFailure { status = "No se pudo abrir mapa flota" }
                    },
                ) { Text("Mapa flota") }
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
            SettingsVehicleSignalsPanel(
                prefs = prefs,
                signalSource = signalSource,
                onSignalSourceChange = { id ->
                    signalSource = id
                    prefs.signalSource = id
                    CanBusManager.rebind()
                },
                obdAddr = obdAddr,
                onObdAddrChange = { obdAddr = it },
                canBackend = canBackend,
                onCanBackendChange = { id ->
                    canBackend = id
                    prefs.canBackend = id
                    CanBusManager.rebind()
                },
                canIface = canIface,
                onCanIfaceChange = { canIface = it },
                onStatus = { status = it },
            )
        }

        PanelBlock("Phone Link · Android Auto / CarPlay") {
            var phoneOn by remember { mutableStateOf(prefs.phoneLinkEnabled) }
            val phone by com.veplayer.app.phone.PhoneLinkBus.state.collectAsState()
            Text(
                "BT media ahora. Host AA/CarPlay completo requiere ROM OEM / MFi — aquí: detección, sim demo y estado flota.",
                color = Mute,
                fontSize = 12.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Phone Link", color = Mist)
                Switch(
                    checked = phoneOn,
                    onCheckedChange = {
                        phoneOn = it
                        prefs.phoneLinkEnabled = it
                        com.veplayer.app.phone.PhoneLinkManager.tick()
                    },
                )
            }
            Text(phone.statusText, color = if (phone.connected) Teal else Mute)
            if (phone.connected) {
                Text(
                    "${phone.protocol.name} · ${phone.deviceName}" +
                        if (phone.mediaTitle.isNotBlank()) " · ${phone.mediaTitle}" else "",
                    color = Mute,
                    fontSize = 12.sp,
                )
            }
            Text(
                "Host AA: ${if (phone.aaHostAvailable) "sí" else "no"} · CarPlay pkg: ${if (phone.carplayHostAvailable) "sí" else "no"}",
                color = Mute,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.phone.PhoneLinkManager.simulate(
                            com.veplayer.app.phone.PhoneLinkBus.Protocol.ANDROID_AUTO,
                        )
                        status = "Sim Android Auto"
                    },
                ) { Text("Sim AA") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.phone.PhoneLinkManager.simulate(
                            com.veplayer.app.phone.PhoneLinkBus.Protocol.CARPLAY,
                        )
                        status = "Sim CarPlay"
                    },
                ) { Text("Sim CarPlay") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.phone.PhoneLinkManager.simulate(
                            com.veplayer.app.phone.PhoneLinkBus.Protocol.BT_MEDIA,
                        )
                        status = "Sim BT"
                    },
                ) { Text("Sim BT") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.phone.PhoneLinkManager.clearSim()
                        status = "Sim off"
                    },
                ) { Text("Limpiar sim") }
                OutlinedButton(
                    onClick = {
                        val ok = com.veplayer.app.phone.PhoneLinkManager.openAndroidAutoSettings()
                        status = if (ok) "Abriendo AA/BT" else "Sin AA — BT settings"
                    },
                ) { Text("Abrir AA / BT") }
            }
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
            var revGuides by remember { mutableStateOf(prefs.reverseGuidesEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Guías reverse (cámara)", color = Mist)
                Switch(
                    checked = revGuides,
                    onCheckedChange = {
                        revGuides = it
                        prefs.reverseGuidesEnabled = it
                        status = if (it) "Guías ON" else "Guías OFF"
                    },
                )
            }
            var parkHud by remember { mutableStateOf(prefs.parkingHudEnabled) }
            var parkTts by remember { mutableStateOf(prefs.parkingTts) }
            var parkSim by remember { mutableStateOf(prefs.parkingSimEnabled) }
            val park by com.veplayer.app.vehicle.ParkingDistanceMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HUD distancia parking", color = Mist)
                Switch(
                    checked = parkHud,
                    onCheckedChange = {
                        parkHud = it
                        prefs.parkingHudEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS parking", color = Mist)
                Switch(
                    checked = parkTts,
                    onCheckedChange = {
                        parkTts = it
                        prefs.parkingTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sim USS (sin sensores)", color = Mist)
                Switch(
                    checked = parkSim,
                    onCheckedChange = {
                        parkSim = it
                        prefs.parkingSimEnabled = it
                    },
                )
            }
            Text(
                if (park.active) "PDC · ${park.label} · ${park.band}" else "PDC idle (activar reverse)",
                color = if (park.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var doorHud by remember { mutableStateOf(prefs.doorAjarEnabled) }
            var doorTts by remember { mutableStateOf(prefs.doorAjarTts) }
            var doorSim by remember { mutableStateOf(prefs.doorAjarSim) }
            val door by com.veplayer.app.vehicle.DoorAjarMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HUD puerta abierta", color = Mist)
                Switch(
                    checked = doorHud,
                    onCheckedChange = {
                        doorHud = it
                        prefs.doorAjarEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS puerta", color = Mist)
                Switch(
                    checked = doorTts,
                    onCheckedChange = {
                        doorTts = it
                        prefs.doorAjarTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sim puerta FL (mock)", color = Mist)
                Switch(
                    checked = doorSim,
                    onCheckedChange = {
                        doorSim = it
                        prefs.doorAjarSim = it
                        CanBusManager.rebind()
                        status = if (it) "Puerta FL sim ON" else "Puerta FL sim OFF"
                    },
                )
            }
            Text(
                if (door.label.isNotBlank()) "Puerta · ${door.label} · ${door.band}" else "Puertas cerradas",
                color = if (door.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var beltOn by remember { mutableStateOf(prefs.seatbeltEnabled) }
            var beltTts by remember { mutableStateOf(prefs.seatbeltTts) }
            var beltSim by remember { mutableStateOf(prefs.seatbeltSim) }
            val belt by com.veplayer.app.vehicle.SeatbeltMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HUD cinturón", color = Mist)
                Switch(
                    checked = beltOn,
                    onCheckedChange = {
                        beltOn = it
                        prefs.seatbeltEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS cinturón", color = Mist)
                Switch(
                    checked = beltTts,
                    onCheckedChange = {
                        beltTts = it
                        prefs.seatbeltTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sim cinturón suelto (mock)", color = Mist)
                Switch(
                    checked = beltSim,
                    onCheckedChange = {
                        beltSim = it
                        prefs.seatbeltSim = it
                        CanBusManager.rebind()
                        status = if (it) "Cinturón sim OFF (suelto)" else "Cinturón sim ON (abrochado)"
                    },
                )
            }
            Text(
                if (belt.label.isNotBlank()) "${belt.label} · ${belt.band}" else "Cinturón OK",
                color = if (belt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var harshOn by remember { mutableStateOf(prefs.harshEnabled) }
            var harshTts by remember { mutableStateOf(prefs.harshTts) }
            val harshSt by com.veplayer.app.vehicle.HarshDrivingMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Frenada / acel. brusca", color = Mist)
                Switch(
                    checked = harshOn,
                    onCheckedChange = {
                        harshOn = it
                        prefs.harshEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS harsh", color = Mist)
                Switch(
                    checked = harshTts,
                    onCheckedChange = {
                        harshTts = it
                        prefs.harshTts = it
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    com.veplayer.app.vehicle.HarshDrivingMonitor.armSim()
                    status = "Sim frenada armada (próximo tick)"
                },
            ) { Text("Sim frenada brusca") }
            Text(
                if (harshSt.showWarn) "${harshSt.label} · ${harshSt.band}" else "Harsh idle",
                color = if (harshSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var impactOn by remember { mutableStateOf(prefs.impactEnabled) }
            var impactTts by remember { mutableStateOf(prefs.impactTts) }
            val impactSt by com.veplayer.app.vehicle.ImpactDetectMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Detectar impacto", color = Mist)
                Switch(
                    checked = impactOn,
                    onCheckedChange = {
                        impactOn = it
                        prefs.impactEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS impacto", color = Mist)
                Switch(
                    checked = impactTts,
                    onCheckedChange = {
                        impactTts = it
                        prefs.impactTts = it
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    com.veplayer.app.vehicle.ImpactDetectMonitor.armSim()
                    status = "Sim impacto armado (próximo tick)"
                },
            ) { Text("Sim impacto") }
            Text(
                if (impactSt.showWarn) {
                    "${impactSt.label} · ${impactSt.band} · g≈${"%.2f".format(impactSt.gApprox)}"
                } else {
                    "Impact idle (decel ${prefs.impactDecelWarnKmhS.toInt()}/${prefs.impactDecelAlertKmhS.toInt()} · yaw ${prefs.impactYawWarnDegS.toInt()})"
                },
                color = if (impactSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var absOn by remember { mutableStateOf(prefs.absHudEnabled) }
            var absTtsOn by remember { mutableStateOf(prefs.absHudTts) }
            var absSimOn by remember { mutableStateOf(prefs.absSim) }
            val absSt by com.veplayer.app.vehicle.AbsHudMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HUD ABS", color = Mist)
                Switch(
                    checked = absOn,
                    onCheckedChange = {
                        absOn = it
                        prefs.absHudEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS ABS", color = Mist)
                Switch(
                    checked = absTtsOn,
                    onCheckedChange = {
                        absTtsOn = it
                        prefs.absHudTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sim ABS activo", color = Mist)
                Switch(
                    checked = absSimOn,
                    onCheckedChange = {
                        absSimOn = it
                        prefs.absSim = it
                        status = if (it) "ABS sim ON" else "ABS sim OFF"
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    com.veplayer.app.vehicle.AbsHudMonitor.armSim()
                    status = "Sim ABS armado (próximo tick)"
                },
            ) { Text("Pulso ABS") }
            Text(
                if (absSt.active || absSt.showWarn || absSt.events > 0) {
                    "${absSt.label.ifBlank { "ABS" }} · ${absSt.band} · ×${absSt.events}"
                } else {
                    "ABS idle (warn ${prefs.absWarnSec}s / alert ${prefs.absAlertSec}s · ×${prefs.absAlertEvents.toInt()})"
                },
                color = if (absSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
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
                prefs.fmBackend = fmBackend
                if (newPin.length >= 4) prefs.pin = newPin
                CanBusManager.rebind()
                status = "Guardado · fuente ${prefs.signalSource} · can ${prefs.canBackend} · fm ${prefs.fmBackend}"
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
