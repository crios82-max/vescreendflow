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
            Text("DBC (mapas CAN):", color = Mist)
            Text(com.veplayer.app.vehicle.can.CanSignalDecoder.statusLabel(prefs), color = Mute)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        prefs.dbcSource = "builtin"
                        com.veplayer.app.vehicle.can.CanSignalDecoder.reload(context)
                        if (signalSource == "can") CanBusManager.rebind()
                        status = "DBC demo cargado"
                    },
                ) { Text("Demo DBC") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            status = "Descargando DBC…"
                            val url = prefs.senseflowUrl.trimEnd('/') + "/dbc/veplayer_demo.dbc"
                            val r =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val body =
                                            okhttp3.OkHttpClient()
                                                .newCall(okhttp3.Request.Builder().url(url).build())
                                                .execute()
                                                .use { resp ->
                                                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                                                    resp.body?.string() ?: error("empty")
                                                }
                                        val key =
                                            com.veplayer.app.vehicle.can.dbc.DbcRepository.installCustom(
                                                context,
                                                body,
                                                "fleet_demo.dbc",
                                            )
                                        prefs.dbcSource = key
                                        com.veplayer.app.vehicle.can.CanSignalDecoder.reload(context)
                                        key
                                    }
                                }
                            r.onSuccess {
                                if (signalSource == "can") CanBusManager.rebind()
                                status = "DBC flota OK · $it"
                            }.onFailure { status = it.message ?: "DBC download fail" }
                        }
                    },
                ) { Text("Desde SenseFlow") }
            }
            Text("Marca OEM (white-label):", color = Mist)
            LaunchedEffect(Unit) { BrandBus.refresh(context) }
            val brand by BrandBus.state.collectAsState()
            Text(
                when {
                    brand.displayName.isNotBlank() -> "Marca · ${brand.displayName} (${brand.brandId})"
                    brand.brandId.isNotBlank() -> "Marca · ${brand.brandId}"
                    else -> "Sin marca OEM"
                },
                color = Color(brand.accentArgb),
            )
            if (brand.hasLogo) {
                BrandLogo(height = 40.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            status = "Descargando marca demo…"
                            val base = prefs.senseflowUrl.trimEnd('/')
                            val url = "$base/brands/demo/logo.png"
                            val r =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        BrandRepository.apply(
                                            context = context,
                                            prefs = prefs,
                                            brandId = "demo",
                                            name = "Marca Demo",
                                            logoUrl = url,
                                            accent = "#E11D48",
                                        )
                                    }
                                }
                            r.onSuccess {
                                BrandBus.refresh(context)
                                status = "Marca demo OK · $it"
                            }.onFailure { status = it.message ?: "brand fail" }
                        }
                    },
                ) { Text("Demo marca") }
                OutlinedButton(
                    onClick = {
                        BrandRepository.clear(context, prefs)
                        BrandBus.refresh(context)
                        status = "Marca OEM limpiada"
                    },
                ) { Text("Limpiar") }
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
            Text("FM radio:", color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.veplayer.app.radio.fm.FmBackend.entries.forEach { b ->
                    val selected = fmBackend == b.id
                    if (selected) {
                        Button(
                            onClick = {
                                fmBackend = b.id
                                prefs.fmBackend = b.id
                                status = "FM → ${b.label}"
                            },
                        ) { Text(b.id) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                fmBackend = b.id
                                prefs.fmBackend = b.id
                                status = "FM → ${b.label}"
                            },
                        ) { Text(b.id) }
                    }
                }
            }
            Text("Última freq ${com.veplayer.app.radio.fm.FmFreq.formatMhz(prefs.fmLastFreqKhz)}", color = Mute)
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
            var dtcOn by remember { mutableStateOf(prefs.dtcAlertsEnabled) }
            var dtcTts by remember { mutableStateOf(prefs.dtcTts) }
            var dtcSeed by remember { mutableStateOf(prefs.dtcDemoSeed) }
            val dtcSnap by com.veplayer.app.vehicle.DtcBus.snap.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Alertas DTC / MIL", color = Mist)
                Switch(
                    checked = dtcOn,
                    onCheckedChange = {
                        dtcOn = it
                        prefs.dtcAlertsEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS fallas", color = Mist)
                Switch(
                    checked = dtcTts,
                    onCheckedChange = {
                        dtcTts = it
                        prefs.dtcTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Seed demo (obd_sim)", color = Mist)
                Switch(
                    checked = dtcSeed,
                    onCheckedChange = {
                        dtcSeed = it
                        prefs.dtcDemoSeed = it
                        if (it) com.veplayer.app.vehicle.CanBusManager.readDtc()
                        else com.veplayer.app.vehicle.CanBusManager.clearDtc()
                    },
                )
            }
            Text(
                buildString {
                    if (dtcSnap.mil) append("MIL · ")
                    if (dtcSnap.codes.isEmpty()) append("Sin DTC")
                    else append(dtcSnap.codes.joinToString { "${it.code}(${it.status.take(1)})" })
                },
                color = if (dtcSnap.mil) Teal else Mute,
            )
            var milDistOn by remember { mutableStateOf(prefs.milDistEnabled) }
            var milDistTts by remember { mutableStateOf(prefs.milDistTts) }
            var milDistSim by remember {
                mutableStateOf(
                    if (prefs.milDistSimKm > 0f) prefs.milDistSimKm.toInt().toString() else "0",
                )
            }
            val milDistSt by com.veplayer.app.vehicle.MilDistanceMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso km con MIL (0121)", color = Mist)
                Switch(
                    checked = milDistOn,
                    onCheckedChange = {
                        milDistOn = it
                        prefs.milDistEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS km MIL", color = Mist)
                Switch(
                    checked = milDistTts,
                    onCheckedChange = {
                        milDistTts = it
                        prefs.milDistTts = it
                    },
                )
            }
            OutlinedTextField(
                value = milDistSim,
                onValueChange = { milDistSim = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Sim km MIL (0=OBD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.milDistSimKm = milDistSim.toFloatOrNull() ?: 0f
                    status =
                        "MIL dist sim ${prefs.milDistSimKm.toInt()} km · warn ${prefs.milDistWarnKm.toInt()} / alert ${prefs.milDistAlertKm.toInt()}"
                },
            ) { Text("Aplicar sim MIL km") }
            Text(
                if (milDistSt.distanceKm != null && milDistSt.milOn) {
                    "${milDistSt.label} · ${milDistSt.band}"
                } else {
                    "MIL dist idle (warn ${prefs.milDistWarnKm.toInt()} / alert ${prefs.milDistAlertKm.toInt()} km)"
                },
                color = if (milDistSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var clearOn by remember { mutableStateOf(prefs.distClearEnabled) }
            var clearTts by remember { mutableStateOf(prefs.distClearTts) }
            var clearSim by remember {
                mutableStateOf(
                    if (prefs.distClearSimKm > 0f) prefs.distClearSimKm.toInt().toString() else "0",
                )
            }
            val clearSt by com.veplayer.app.vehicle.DistSinceClearMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso km desde clear (0131)", color = Mist)
                Switch(
                    checked = clearOn,
                    onCheckedChange = {
                        clearOn = it
                        prefs.distClearEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS km clear", color = Mist)
                Switch(
                    checked = clearTts,
                    onCheckedChange = {
                        clearTts = it
                        prefs.distClearTts = it
                    },
                )
            }
            OutlinedTextField(
                value = clearSim,
                onValueChange = { clearSim = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Sim km clear (0=OBD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.distClearSimKm = clearSim.toFloatOrNull() ?: 0f
                    status =
                        "Clear sim ${prefs.distClearSimKm.toInt()} km · warn ${prefs.distClearWarnKm.toInt()} / alert ${prefs.distClearAlertKm.toInt()}"
                },
            ) { Text("Aplicar sim clear") }
            Text(
                if (clearSt.distanceKm != null && clearSt.faultActive) {
                    "${clearSt.label} · ${clearSt.band}"
                } else {
                    "Clear idle (warn ${prefs.distClearWarnKm.toInt()} / alert ${prefs.distClearAlertKm.toInt()} km)"
                },
                color = if (clearSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.vehicle.CanBusManager.readDtc()
                        status = "DTC leídos"
                    },
                ) { Text("Leer DTC") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.vehicle.DtcBus.seedDemo()
                        com.veplayer.app.vehicle.CanBusManager.readDtc()
                        status = "DTC sim P0420/P0301"
                    },
                ) { Text("Simular") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.vehicle.CanBusManager.clearDtc()
                        status = "DTC clear"
                    },
                ) { Text("Limpiar") }
            }
        }

        PanelBlock("Navegación") {
            var navOn by remember { mutableStateOf(prefs.navEnabled) }
            var destName by remember { mutableStateOf(prefs.navDestName) }
            var mapMode by remember { mutableStateOf(prefs.mapMode) }
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
            var navTts by remember { mutableStateOf(prefs.navTtsEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Guía por voz (TTS)", color = Mist)
                Switch(
                    checked = navTts,
                    onCheckedChange = {
                        navTts = it
                        com.veplayer.app.nav.NavTts.setEnabled(it)
                        status = if (it) "TTS ON" else "TTS OFF"
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    com.veplayer.app.nav.NavTts.speakNow(
                        "En 200 metros, girá a la izquierda. Ruta de prueba VePlayer.",
                    )
                    status = "TTS prueba"
                },
            ) { Text("Probar voz") }
            Text("Mapa cockpit:", color = Mist)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("native" to "Nativo", "web" to "WebView").forEach { (id, label) ->
                    if (mapMode == id) {
                        Button(
                            onClick = {
                                mapMode = id
                                prefs.mapMode = id
                                status = "Mapa → $label"
                            },
                        ) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = {
                                mapMode = id
                                prefs.mapMode = id
                                status = "Mapa → $label"
                            },
                        ) { Text(label) }
                    }
                }
            }
            var mapTiles by remember { mutableStateOf(prefs.mapTilesEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tiles OSM (mapa nativo)", color = Mist)
                Switch(
                    checked = mapTiles,
                    onCheckedChange = {
                        mapTiles = it
                        prefs.mapTilesEnabled = it
                        status = if (it) "Tiles OSM ON" else "Tiles OSM OFF"
                    },
                )
            }
            val prefetch by com.veplayer.app.ui.map.OsmPrefetch.state.collectAsState()
            LaunchedEffect(Unit) {
                com.veplayer.app.ui.map.OsmPrefetch.refreshStats(context)
            }
            Text(
                "Caché offline · ${"%.1f".format(prefetch.cacheMb)} MB · ${prefetch.cacheFiles} tiles",
                color = Mute,
                fontSize = 12.sp,
            )
            Text(prefetch.label, color = if (prefetch.running) Teal else Mute, fontSize = 12.sp)
            if (prefetch.running && prefetch.total > 0) {
                Text(
                    "${prefetch.done}/${prefetch.total} · ↓${prefetch.downloaded}",
                    color = Mute,
                    fontSize = 12.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.ui.map.OsmPrefetch.startAroundMe(context, prefs, scope)
                        status = "Prefetch alrededor…"
                    },
                    enabled = !prefetch.running,
                ) { Text("Prefetch zona") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.ui.map.OsmPrefetch.startRoute(context, prefs, scope)
                        status = "Prefetch ruta…"
                    },
                    enabled = !prefetch.running,
                ) { Text("Prefetch ruta") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.ui.map.OsmPrefetch.cancel()
                        status = "Prefetch cancel"
                    },
                    enabled = prefetch.running,
                ) { Text("Cancelar") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.ui.map.OsmPrefetch.clear(context)
                        status = "Tiles borrados"
                    },
                    enabled = !prefetch.running,
                ) { Text("Borrar caché") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.ui.map.OsmPrefetch.refreshStats(context)
                        status = "Caché ${"%.1f".format(com.veplayer.app.ui.map.OsmPrefetch.state.value.cacheMb)} MB"
                    },
                ) { Text("Refresh") }
            }
            Text(
                "Zoom prefetch ${prefs.mapPrefetchZMin}–${prefs.mapPrefetchZMax} · max ${prefs.mapPrefetchMaxTiles}",
                color = Mute,
                fontSize = 11.sp,
            )
            var mapCrowd by remember { mutableStateOf(prefs.mapCrowdEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Crowd SenseFlow en mapa", color = Mist)
                Switch(
                    checked = mapCrowd,
                    onCheckedChange = {
                        mapCrowd = it
                        prefs.mapCrowdEnabled = it
                        status = if (it) "Crowd ON" else "Crowd OFF"
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
            var routeDevOn by remember { mutableStateOf(prefs.routeDevEnabled) }
            var routeDevTts by remember { mutableStateOf(prefs.routeDevTts) }
            var routeDevSim by remember {
                mutableStateOf(
                    if (prefs.routeDevSimM > 0f) prefs.routeDevSimM.toInt().toString() else "0",
                )
            }
            val routeDevSt by com.veplayer.app.vehicle.RouteDeviationMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso desvío de ruta", color = Mist)
                Switch(
                    checked = routeDevOn,
                    onCheckedChange = {
                        routeDevOn = it
                        prefs.routeDevEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS desvío", color = Mist)
                Switch(
                    checked = routeDevTts,
                    onCheckedChange = {
                        routeDevTts = it
                        prefs.routeDevTts = it
                    },
                )
            }
            OutlinedTextField(
                value = routeDevSim,
                onValueChange = { routeDevSim = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Sim metros fuera ruta (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.routeDevSimM = routeDevSim.toFloatOrNull() ?: 0f
                    status =
                        "Route sim ${prefs.routeDevSimM.toInt()} m · warn ${prefs.routeDevWarnM.toInt()} / alert ${prefs.routeDevAlertM.toInt()} · hold ${prefs.routeDevHoldSec.toInt()} s"
                },
            ) { Text("Aplicar sim desvío") }
            Text(
                if (routeDevSt.hasRoute || routeDevSt.showWarn) {
                    "${routeDevSt.label} · ${routeDevSt.band}" +
                        if (routeDevSt.offRouteSec > 0f) " · ${routeDevSt.offRouteSec.toInt()}s" else ""
                } else {
                    "Route idle (warn ${prefs.routeDevWarnM.toInt()} / alert ${prefs.routeDevAlertM.toInt()} m · hold ${prefs.routeDevHoldSec.toInt()} s)"
                },
                color = if (routeDevSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("Speed HUD") {
            var hudOn by remember { mutableStateOf(prefs.speedHudEnabled) }
            var lim by remember { mutableStateOf(prefs.speedLimitKmh.toFloat()) }
            var ttsWarn by remember { mutableStateOf(prefs.speedTtsWarn) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Badge límite", color = Mist)
                Switch(
                    checked = hudOn,
                    onCheckedChange = {
                        hudOn = it
                        prefs.speedHudEnabled = it
                    },
                )
            }
            Text("Límite ${lim.toInt()} km/h", color = Mute)
            Slider(
                value = lim,
                onValueChange = {
                    lim = it
                    prefs.speedLimitKmh = it.toInt()
                },
                valueRange = 20f..120f,
                steps = 19,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS exceso", color = Mist)
                Switch(
                    checked = ttsWarn,
                    onCheckedChange = {
                        ttsWarn = it
                        prefs.speedTtsWarn = it
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(40, 50, 60, 80).forEach { v ->
                    OutlinedButton(
                        onClick = {
                            lim = v.toFloat()
                            prefs.speedLimitKmh = v
                            status = "Límite $v"
                        },
                    ) { Text("$v") }
                }
            }
            var zoneOn by remember { mutableStateOf(prefs.geofenceSpeedEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Límite por geofence", color = Mist)
                Switch(
                    checked = zoneOn,
                    onCheckedChange = {
                        zoneOn = it
                        prefs.geofenceSpeedEnabled = it
                    },
                )
            }
            val zone by com.veplayer.app.vehicle.SpeedZoneBus.zone.collectAsState()
            Text(
                zone?.let { "Zona activa · ${it.name} · ${it.maxKmh} km/h" } ?: "Sin zona de velocidad",
                color = Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("Fuel / Range HUD") {
            var fuelOn by remember { mutableStateOf(prefs.fuelHudEnabled) }
            var warnPct by remember { mutableStateOf(prefs.fuelWarnPct) }
            var critPct by remember { mutableStateOf(prefs.fuelCriticalPct) }
            var fuelTts by remember { mutableStateOf(prefs.fuelTtsWarn) }
            val liveFuel = live.fuelPct
            val liveSoc = live.batterySocPct
            val liveRange = live.rangeKm
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HUD energía", color = Mist)
                Switch(
                    checked = fuelOn,
                    onCheckedChange = {
                        fuelOn = it
                        prefs.fuelHudEnabled = it
                    },
                )
            }
            Text("Aviso ${warnPct.toInt()}% · crítico ${critPct.toInt()}%", color = Mute)
            Slider(
                value = warnPct,
                onValueChange = {
                    warnPct = it
                    prefs.fuelWarnPct = it
                    if (critPct > it) {
                        critPct = it
                        prefs.fuelCriticalPct = it
                    }
                },
                valueRange = 5f..40f,
                steps = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = critPct,
                onValueChange = {
                    critPct = it.coerceAtMost(warnPct)
                    prefs.fuelCriticalPct = critPct
                },
                valueRange = 2f..25f,
                steps = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS bajo", color = Mist)
                Switch(
                    checked = fuelTts,
                    onCheckedChange = {
                        fuelTts = it
                        prefs.fuelTtsWarn = it
                    },
                )
            }
            Text(
                buildString {
                    liveFuel?.let { append("Fuel ${it.toInt()}% · ") }
                    liveSoc?.let { append("SOC ${it.toInt()}% · ") }
                    append("rango ${liveRange?.toInt() ?: "—"} km")
                },
                color = Mute,
                fontSize = 12.sp,
            )
            OutlinedButton(
                onClick = {
                    val st =
                        com.veplayer.app.vehicle.FuelRangeHud.evaluate(
                            liveFuel,
                            liveSoc,
                            liveRange,
                            prefs.fuelWarnPct,
                            prefs.fuelCriticalPct,
                            prefs.rangeWarnKm,
                            prefs.rangeCriticalKm,
                        )
                    com.veplayer.app.nav.NavTts.speakNow(
                        com.veplayer.app.vehicle.FuelRangeHud.voicePhrase(st),
                    )
                    status = "TTS energía"
                },
            ) { Text("Probar voz energía") }
        }

        PanelBlock("Idle / ralentí") {
            var idleOn by remember { mutableStateOf(prefs.idleAlertEnabled) }
            var warnSec by remember { mutableStateOf(prefs.idleWarnSec.toFloat()) }
            var alertSec by remember { mutableStateOf(prefs.idleAlertSec.toFloat()) }
            var idleTts by remember { mutableStateOf(prefs.idleTtsWarn) }
            val idleSt by com.veplayer.app.vehicle.IdleMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso ralentí", color = Mist)
                Switch(
                    checked = idleOn,
                    onCheckedChange = {
                        idleOn = it
                        prefs.idleAlertEnabled = it
                    },
                )
            }
            Text(
                "Warn ${warnSec.toInt()}s · alert ${alertSec.toInt()}s",
                color = Mute,
            )
            Slider(
                value = warnSec,
                onValueChange = {
                    warnSec = it
                    prefs.idleWarnSec = it.toInt()
                    if (alertSec < it) {
                        alertSec = it
                        prefs.idleAlertSec = it.toInt()
                    }
                },
                valueRange = 30f..600f,
                steps = 18,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = alertSec,
                onValueChange = {
                    alertSec = it.coerceAtLeast(warnSec)
                    prefs.idleAlertSec = alertSec.toInt()
                },
                valueRange = 60f..900f,
                steps = 27,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS idle", color = Mist)
                Switch(
                    checked = idleTts,
                    onCheckedChange = {
                        idleTts = it
                        prefs.idleTtsWarn = it
                    },
                )
            }
            Text(
                com.veplayer.app.vehicle.IdleAlert.labelLine(idleSt).ifBlank { "—" },
                color = Mute,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(60, 120, 180, 300).forEach { v ->
                    OutlinedButton(
                        onClick = {
                            warnSec = v.toFloat()
                            alertSec = (v * 2).toFloat()
                            prefs.idleWarnSec = v
                            prefs.idleAlertSec = v * 2
                            status = "Idle warn ${v}s"
                        },
                    ) { Text("${v}s") }
                }
            }
        }

        PanelBlock("SOS / pánico") {
            var panicOn by remember { mutableStateOf(prefs.panicEnabled) }
            var clipOn by remember { mutableStateOf(prefs.sosClipEnabled) }
            var clipSim by remember { mutableStateOf(prefs.sosClipSim) }
            val panicSt by com.veplayer.app.fleet.PanicBus.state.collectAsState()
            val fleetLocal = remember { FleetClient(prefs) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Botón SOS (mantener 1.2s)", color = Mist)
                Switch(
                    checked = panicOn,
                    onCheckedChange = {
                        panicOn = it
                        prefs.panicEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Clip dashcam al SOS", color = Mist)
                Switch(
                    checked = clipOn,
                    onCheckedChange = {
                        clipOn = it
                        prefs.sosClipEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Clip sim (sin cámara)", color = Mist)
                Switch(
                    checked = clipSim,
                    onCheckedChange = {
                        clipSim = it
                        prefs.sosClipSim = it
                    },
                )
            }
            Text(
                if (panicSt.active) {
                    "SOS activo · id ${panicSt.alertId ?: "—"}" +
                        (panicSt.clipUrl?.let { " · clip $it" } ?: "")
                } else {
                    "Sin SOS abierto · buffer ${prefs.sosClipSec}s"
                },
                color = Mute,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            com.veplayer.app.fleet.PanicBus.trigger(prefs, fleetLocal, context)
                                .onSuccess {
                                    status =
                                        "SOS enviado" +
                                            (it.clipUrl?.let { u -> " · clip $u" } ?: "")
                                }
                                .onFailure { status = "SOS fail: ${it.message}" }
                        }
                    },
                ) { Text("Enviar SOS") }
                OutlinedButton(
                    onClick = {
                        com.veplayer.app.fleet.PanicBus.clear(speak = false)
                        status = "SOS limpiado local"
                    },
                ) { Text("Limpiar") }
            }
        }

        PanelBlock("Remolque / movimiento no autorizado") {
            var towOn by remember { mutableStateOf(prefs.towEnabled) }
            var towTts by remember { mutableStateOf(prefs.towTts) }
            var towSim by remember { mutableStateOf(prefs.towSim) }
            val towSt by com.veplayer.app.vehicle.UnauthorizedMoveMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Detectar remolque", color = Mist)
                Switch(
                    checked = towOn,
                    onCheckedChange = {
                        towOn = it
                        prefs.towEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS remolque", color = Mist)
                Switch(
                    checked = towTts,
                    onCheckedChange = {
                        towTts = it
                        prefs.towTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sim remolque (ign off + velocidad)", color = Mist)
                Switch(
                    checked = towSim,
                    onCheckedChange = {
                        towSim = it
                        prefs.towSim = it
                        status = if (it) "Tow sim ON" else "Tow sim OFF"
                    },
                )
            }
            Text(
                if (towSt.label.isNotBlank()) {
                    "${towSt.label} · ${towSt.band} · ${towSt.movingForSec.toInt()}s"
                } else {
                    "Tow idle (ign off + movimiento)"
                },
                color = if (towSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var pbrakeOn by remember { mutableStateOf(prefs.pbrakeEnabled) }
            var pbrakeTtsOn by remember { mutableStateOf(prefs.pbrakeTts) }
            var pbrakeSimOn by remember { mutableStateOf(prefs.pbrakeSim) }
            val pbrakeSt by com.veplayer.app.vehicle.ParkingBrakeMovingMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Freno estacionamiento en movimiento", color = Mist)
                Switch(
                    checked = pbrakeOn,
                    onCheckedChange = {
                        pbrakeOn = it
                        prefs.pbrakeEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS freno estacionamiento", color = Mist)
                Switch(
                    checked = pbrakeTtsOn,
                    onCheckedChange = {
                        pbrakeTtsOn = it
                        prefs.pbrakeTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sim freno + velocidad", color = Mist)
                Switch(
                    checked = pbrakeSimOn,
                    onCheckedChange = {
                        pbrakeSimOn = it
                        prefs.pbrakeSim = it
                        status =
                            if (it) {
                                "P-brake sim ON · ${prefs.pbrakeSimKmh.toInt()} km/h"
                            } else {
                                "P-brake sim OFF"
                            }
                    },
                )
            }
            Text(
                if (pbrakeSt.showWarn || pbrakeSt.parkingBrake) {
                    "${pbrakeSt.label.ifBlank { "Freno" }} · ${pbrakeSt.band}"
                } else {
                    "P-brake idle (warn ≥${prefs.pbrakeWarnKmh.toInt()} / alert ≥${prefs.pbrakeAlertKmh.toInt()} km/h)"
                },
                color = if (pbrakeSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var gearRollOn by remember { mutableStateOf(prefs.gearRollEnabled) }
            var gearRollTtsOn by remember { mutableStateOf(prefs.gearRollTts) }
            var gearRollSimOn by remember { mutableStateOf(prefs.gearRollSim) }
            val gearRollSt by com.veplayer.app.vehicle.GearRollMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Rodando en P/N", color = Mist)
                Switch(
                    checked = gearRollOn,
                    onCheckedChange = {
                        gearRollOn = it
                        prefs.gearRollEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS marcha P/N", color = Mist)
                Switch(
                    checked = gearRollTtsOn,
                    onCheckedChange = {
                        gearRollTtsOn = it
                        prefs.gearRollTts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sim P/N + velocidad", color = Mist)
                Switch(
                    checked = gearRollSimOn,
                    onCheckedChange = {
                        gearRollSimOn = it
                        prefs.gearRollSim = it
                        status =
                            if (it) {
                                "Gear roll sim ${prefs.gearRollSimGear} · ${prefs.gearRollSimKmh.toInt()} km/h"
                            } else {
                                "Gear roll sim OFF"
                            }
                    },
                )
            }
            Text(
                if (gearRollSt.showWarn || gearRollSt.band == "idle" && gearRollSt.gear.isNotBlank()) {
                    "${gearRollSt.label.ifBlank { gearRollSt.gear }} · ${gearRollSt.band}"
                } else {
                    "Gear roll idle (warn ≥${prefs.gearRollWarnKmh.toInt()} / alert ≥${prefs.gearRollAlertKmh.toInt()} km/h en P/N)"
                },
                color = if (gearRollSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var turnStuckOn by remember { mutableStateOf(prefs.turnStuckEnabled) }
            var turnStuckTtsOn by remember { mutableStateOf(prefs.turnStuckTts) }
            var turnStuckSim by remember {
                mutableStateOf(
                    if (prefs.turnStuckSimSec > 0f) prefs.turnStuckSimSec.toInt().toString() else "0",
                )
            }
            val turnStuckSt by com.veplayer.app.vehicle.TurnStuckMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Intermitente olvidado", color = Mist)
                Switch(
                    checked = turnStuckOn,
                    onCheckedChange = {
                        turnStuckOn = it
                        prefs.turnStuckEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS intermitente", color = Mist)
                Switch(
                    checked = turnStuckTtsOn,
                    onCheckedChange = {
                        turnStuckTtsOn = it
                        prefs.turnStuckTts = it
                    },
                )
            }
            OutlinedTextField(
                value = turnStuckSim,
                onValueChange = { turnStuckSim = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Sim segundos inter (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.turnStuckSimSec = turnStuckSim.toFloatOrNull() ?: 0f
                    status =
                        "Turn stuck sim ${prefs.turnStuckSimSec.toInt()}s · warn ${prefs.turnStuckWarnSec.toInt()} / alert ${prefs.turnStuckAlertSec.toInt()}"
                },
            ) { Text("Aplicar sim intermitente") }
            Text(
                if (turnStuckSt.side.isNotBlank()) {
                    "${turnStuckSt.label} · ${turnStuckSt.band}"
                } else {
                    "Inter idle (warn ${prefs.turnStuckWarnSec.toInt()}s / alert ${prefs.turnStuckAlertSec.toInt()}s)"
                },
                color = if (turnStuckSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var hazardOn by remember { mutableStateOf(prefs.hazardStuckEnabled) }
            var hazardTtsOn by remember { mutableStateOf(prefs.hazardStuckTts) }
            var hazardSim by remember {
                mutableStateOf(
                    if (prefs.hazardStuckSimSec > 0f) prefs.hazardStuckSimSec.toInt().toString() else "0",
                )
            }
            val hazardSt by com.veplayer.app.vehicle.HazardStuckMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hazard olvidado", color = Mist)
                Switch(
                    checked = hazardOn,
                    onCheckedChange = {
                        hazardOn = it
                        prefs.hazardStuckEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS hazard", color = Mist)
                Switch(
                    checked = hazardTtsOn,
                    onCheckedChange = {
                        hazardTtsOn = it
                        prefs.hazardStuckTts = it
                    },
                )
            }
            OutlinedTextField(
                value = hazardSim,
                onValueChange = { hazardSim = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Sim segundos hazard (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.hazardStuckSimSec = hazardSim.toFloatOrNull() ?: 0f
                    status =
                        "Hazard sim ${prefs.hazardStuckSimSec.toInt()}s · warn ${prefs.hazardStuckWarnSec.toInt()} / alert ${prefs.hazardStuckAlertSec.toInt()}"
                },
            ) { Text("Aplicar sim hazard") }
            Text(
                if (hazardSt.active) {
                    "${hazardSt.label} · ${hazardSt.band}"
                } else {
                    "Hazard idle (warn ${prefs.hazardStuckWarnSec.toInt()}s / alert ${prefs.hazardStuckAlertSec.toInt()}s)"
                },
                color = if (hazardSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("Caída brusca de combustible") {
            var dropOn by remember { mutableStateOf(prefs.fuelDropEnabled) }
            var dropTts by remember { mutableStateOf(prefs.fuelDropTts) }
            var dropSim by remember {
                mutableStateOf(
                    if (prefs.fuelDropSimDropPct > 0f) prefs.fuelDropSimDropPct.toInt().toString() else "0",
                )
            }
            val dropSt by com.veplayer.app.vehicle.SuddenFuelDropMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Detectar caída brusca", color = Mist)
                Switch(
                    checked = dropOn,
                    onCheckedChange = {
                        dropOn = it
                        prefs.fuelDropEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS caída combustible", color = Mist)
                Switch(
                    checked = dropTts,
                    onCheckedChange = {
                        dropTts = it
                        prefs.fuelDropTts = it
                    },
                )
            }
            OutlinedTextField(
                value = dropSim,
                onValueChange = { dropSim = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                label = { Text("Sim drop % (0 = off)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.fuelDropSimDropPct = dropSim.toFloatOrNull() ?: 0f
                    status =
                        "Fuel drop sim −${prefs.fuelDropSimDropPct.toInt()}% · warn ${prefs.fuelDropWarnPct.toInt()} / alert ${prefs.fuelDropAlertPct.toInt()} · ${prefs.fuelDropWindowSec.toInt()}s"
                },
            ) { Text("Aplicar sim") }
            Text(
                if (dropSt.showWarn || dropSt.dropPct > 0f) {
                    "${dropSt.label} · ${dropSt.band} · ventana ${dropSt.windowSec.toInt()}s"
                } else {
                    "Fuel drop idle (pico→actual en ventana)"
                },
                color = if (dropSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("TPMS por rueda") {
            var tpmsOn by remember { mutableStateOf(prefs.tpmsHudEnabled) }
            var tpmsTts by remember { mutableStateOf(prefs.tpmsTts) }
            var tpmsSim by remember {
                mutableStateOf(
                    if (prefs.tpmsSimFlPsi > 0f) prefs.tpmsSimFlPsi.toInt().toString() else "0",
                )
            }
            val tpmsSt by com.veplayer.app.vehicle.TpmsHudMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HUD TPMS", color = Mist)
                Switch(
                    checked = tpmsOn,
                    onCheckedChange = {
                        tpmsOn = it
                        prefs.tpmsHudEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS TPMS", color = Mist)
                Switch(
                    checked = tpmsTts,
                    onCheckedChange = {
                        tpmsTts = it
                        prefs.tpmsTts = it
                    },
                )
            }
            OutlinedTextField(
                value = tpmsSim,
                onValueChange = { tpmsSim = it.filter { ch -> ch.isDigit() || ch == '.' }.take(4) },
                label = { Text("Sim FL psi (0 = live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.tpmsSimFlPsi = tpmsSim.toFloatOrNull() ?: 0f
                    status =
                        "TPMS sim FL ${prefs.tpmsSimFlPsi.toInt()} · warn ${prefs.tpmsWarnPsi.toInt()} / alert ${prefs.tpmsAlertPsi.toInt()} psi"
                },
            ) { Text("Aplicar sim FL") }
            Text(
                if (tpmsSt.detail.isNotBlank()) {
                    "${tpmsSt.detail} · ${tpmsSt.band}"
                } else {
                    "TPMS idle (FL/FR/RL/RR)"
                },
                color = if (tpmsSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("Batería 12V") {
            var battOn by remember { mutableStateOf(prefs.battVoltEnabled) }
            var battTts by remember { mutableStateOf(prefs.battVoltTts) }
            var battSim by remember {
                mutableStateOf(
                    if (prefs.battVoltSimV > 0f) String.format("%.1f", prefs.battVoltSimV) else "0",
                )
            }
            val battSt by com.veplayer.app.vehicle.BatteryVoltageMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso voltaje 12V", color = Mist)
                Switch(
                    checked = battOn,
                    onCheckedChange = {
                        battOn = it
                        prefs.battVoltEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS batería 12V", color = Mist)
                Switch(
                    checked = battTts,
                    onCheckedChange = {
                        battTts = it
                        prefs.battVoltTts = it
                    },
                )
            }
            OutlinedTextField(
                value = battSim,
                onValueChange = { battSim = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
                label = { Text("Sim V (0 = live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.battVoltSimV = battSim.toFloatOrNull() ?: 0f
                    status =
                        "Batt sim ${prefs.battVoltSimV} V · warn ${prefs.battVoltWarnV} / alert ${prefs.battVoltAlertV}"
                },
            ) { Text("Aplicar sim V") }
            Text(
                if (battSt.volts != null) {
                    "12V · ${battSt.label} · ${battSt.band}"
                } else {
                    "12V idle (OBD 0142 / CAN)"
                },
                color = if (battSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("Incidente (reporte flota)") {
            var incOn by remember { mutableStateOf(prefs.incidentEnabled) }
            var incClip by remember { mutableStateOf(prefs.incidentClipEnabled) }
            var incNote by remember { mutableStateOf("") }
            var incCat by remember { mutableStateOf("other") }
            val incSt by com.veplayer.app.fleet.IncidentBus.state.collectAsState()
            val fleetInc = remember { FleetClient(prefs) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Reportes de incidente", color = Mist)
                Switch(
                    checked = incOn,
                    onCheckedChange = {
                        incOn = it
                        prefs.incidentEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Adjuntar clip", color = Mist)
                Switch(
                    checked = incClip,
                    onCheckedChange = {
                        incClip = it
                        prefs.incidentClipEnabled = it
                    },
                )
            }
            Text("Categoría", color = Mute, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((key, label) in com.veplayer.app.fleet.IncidentBus.categories) {
                    val selected = incCat == key
                    OutlinedButton(
                        onClick = { incCat = key },
                    ) {
                        Text(
                            label,
                            color = if (selected) Teal else Mist,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = incNote,
                onValueChange = { incNote = it.take(280) },
                label = { Text("Nota (opcional)") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    scope.launch {
                        com.veplayer.app.fleet.IncidentBus.report(
                            prefs = prefs,
                            fleet = fleetInc,
                            context = context,
                            category = incCat,
                            note = incNote.ifBlank { null },
                            withClip = incClip,
                        ).onSuccess {
                            status =
                                "Incidente #${it.lastAlertId ?: "—"}" +
                                    (it.lastClipUrl?.let { u -> " · $u" } ?: "")
                            incNote = ""
                        }.onFailure { status = "Incidente fail: ${it.message}" }
                    }
                },
            ) { Text("Enviar incidente") }
            Text(
                if (incSt.lastAlertId != null) {
                    "Último · ${incSt.lastCategory} · #${incSt.lastAlertId}" +
                        (incSt.lastClipUrl?.let { " · clip" } ?: "")
                } else {
                    "Sin reportes en esta sesión"
                },
                color = Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("Mantenimiento odómetro") {
            var maintOn by remember { mutableStateOf(prefs.maintenanceEnabled) }
            var maintTts by remember { mutableStateOf(prefs.maintenanceTts) }
            val odo = VehicleState.state.collectAsState().value.odometerKm
            var items by remember {
                mutableStateOf(com.veplayer.app.vehicle.Maintenance.parseJson(prefs.maintenanceJson))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recordatorios", color = Mist)
                Switch(
                    checked = maintOn,
                    onCheckedChange = {
                        maintOn = it
                        prefs.maintenanceEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS servicio", color = Mist)
                Switch(
                    checked = maintTts,
                    onCheckedChange = {
                        maintTts = it
                        prefs.maintenanceTts = it
                    },
                )
            }
            Text(
                "Odómetro ${odo?.toInt()?.toString() ?: "—"} km",
                color = Mute,
            )
            items.forEach { item ->
                val st = com.veplayer.app.vehicle.Maintenance.evaluate(item, odo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.label} · cada ${item.intervalKm.toInt()} km", color = Mist)
                        Text(
                            when (st.band) {
                                "due" -> "Vencido (${st.remainingKm?.toInt()?.let { kotlin.math.abs(it) } ?: "—"} km)"
                                "warn" -> "En ${st.remainingKm?.toInt() ?: "—"} km"
                                "off" -> "Off"
                                else -> "OK · vence ${st.dueAtKm.toInt()}"
                            },
                            color = Mute,
                            fontSize = 12.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val at = odo ?: item.lastServiceOdoKm
                            items =
                                com.veplayer.app.vehicle.Maintenance.recordService(items, item.kind, at)
                            prefs.maintenanceJson =
                                com.veplayer.app.vehicle.Maintenance.toJson(items)
                            status = "Servicio ${item.label} @ ${at.toInt()} km"
                        },
                    ) { Text("Hecho") }
                }
            }
            OutlinedButton(
                onClick = {
                    val base = odo ?: 0f
                    items = com.veplayer.app.vehicle.Maintenance.defaults(base)
                    prefs.maintenanceJson =
                        com.veplayer.app.vehicle.Maintenance.toJson(items)
                    status = "Intervalos por defecto @ ${base.toInt()} km"
                },
            ) { Text("Restablecer intervalos") }
        }

        PanelBlock("Flota voz / inbox") {
            var alertsOn by remember { mutableStateOf(prefs.fleetAlertsEnabled) }
            var ttsAlerts by remember { mutableStateOf(prefs.fleetTtsAlerts) }
            var ttsMsgs by remember { mutableStateOf(prefs.fleetTtsMessages) }
            val inbox by com.veplayer.app.fleet.FleetInbox.items.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Inbox alertas", color = Mist)
                Switch(
                    checked = alertsOn,
                    onCheckedChange = {
                        alertsOn = it
                        prefs.fleetAlertsEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS alertas (geofence/ABS…)", color = Mist)
                Switch(
                    checked = ttsAlerts,
                    onCheckedChange = {
                        ttsAlerts = it
                        prefs.fleetTtsAlerts = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS mensajes flota", color = Mist)
                Switch(
                    checked = ttsMsgs,
                    onCheckedChange = {
                        ttsMsgs = it
                        prefs.fleetTtsMessages = it
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    com.veplayer.app.fleet.FleetInbox.push(
                        prefs,
                        kind = "message",
                        text = "Prueba de inbox VePlayer",
                        speak = true,
                    )
                    status = "Inbox prueba"
                },
            ) { Text("Probar voz inbox") }
            var msgReplyOn by remember { mutableStateOf(prefs.messageReplyEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ack / reply mensajes", color = Mist)
                Switch(
                    checked = msgReplyOn,
                    onCheckedChange = {
                        msgReplyOn = it
                        prefs.messageReplyEnabled = it
                    },
                )
            }
            val pendingMsg by com.veplayer.app.fleet.MessageReplyBus.pending.collectAsState()
            val fleetMsg = remember { FleetClient(prefs) }
            Text(
                pendingMsg?.let { com.veplayer.app.fleet.MessageReplyBus.label(it) }
                    ?: "Sin mensaje pendiente",
                color = if (pendingMsg?.status == "pending") Teal else Mute,
                fontSize = 12.sp,
            )
            if (pendingMsg != null && pendingMsg!!.status == "pending") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            val p = pendingMsg ?: return@OutlinedButton
                            scope.launch {
                                val r =
                                    withContext(Dispatchers.IO) {
                                        fleetMsg.ackMessage(p.alertId)
                                    }
                                r.onSuccess {
                                    com.veplayer.app.fleet.MessageReplyBus.markAcked()
                                    if (prefs.messageReplyTts) {
                                        com.veplayer.app.nav.NavTts.speakNow("Mensaje confirmado.")
                                    }
                                    status = "Msg acked #${p.alertId}"
                                }.onFailure { status = "Ack fail: ${it.message}" }
                            }
                        },
                    ) { Text("Ack") }
                    for ((key, label) in com.veplayer.app.fleet.MessageReplyBus.canned.take(3)) {
                        OutlinedButton(
                            onClick = {
                                val p = pendingMsg ?: return@OutlinedButton
                                scope.launch {
                                    val r =
                                        withContext(Dispatchers.IO) {
                                            fleetMsg.replyMessage(canned = key, alertId = p.alertId)
                                        }
                                    r.onSuccess { reply ->
                                        com.veplayer.app.fleet.MessageReplyBus.markReplied(reply)
                                        com.veplayer.app.fleet.FleetInbox.push(
                                            prefs,
                                            kind = "message_reply",
                                            text = reply,
                                            id = "reply:${p.alertId}",
                                        )
                                        if (prefs.messageReplyTts) {
                                            com.veplayer.app.nav.NavTts.speakNow("Respuesta enviada. $reply.")
                                        }
                                        status = "Reply: $reply"
                                    }.onFailure { status = "Reply fail: ${it.message}" }
                                }
                            },
                        ) { Text(label) }
                    }
                }
            }
            Text("Últimos ${inbox.size.coerceAtMost(5)}", color = Mute)
            inbox.take(5).forEach { item ->
                Text(
                    "· [${item.severity}] ${item.text.take(72)}",
                    color = Mute,
                )
            }
        }

        PanelBlock("Conductor") {
            var driverCode by remember { mutableStateOf(prefs.driverCode) }
            var driverPin by remember { mutableStateOf("") }
            var driverLabel by remember {
                mutableStateOf(
                    if (prefs.driverId > 0) "${prefs.driverCode} · ${prefs.driverName}" else "Sin conductor",
                )
            }
            Text(driverLabel, color = Mist, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = driverCode,
                onValueChange = { driverCode = it.take(16) },
                label = { Text("Código (D001)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = driverPin,
                onValueChange = { driverPin = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("PIN (si aplica)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val r =
                                withContext(Dispatchers.IO) {
                                    com.veplayer.app.fleet.DriverSession.login(
                                        prefs,
                                        driverCode,
                                        driverPin.ifBlank { null },
                                        scope,
                                    )
                                }
                            r.onSuccess {
                                driverLabel = "${it.code} · ${it.name}"
                                status = "Conductor → ${it.name}"
                            }.onFailure {
                                status = "Login conductor: ${it.message}"
                            }
                        }
                    },
                ) { Text("Entrar") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                com.veplayer.app.fleet.DriverSession.logout(prefs)
                            }
                            driverLabel = "Sin conductor"
                            driverCode = ""
                            driverPin = ""
                            status = "Conductor cerrado"
                        }
                    },
                ) { Text("Salir") }
            }
            Text("Demo: D001 / 1234 · D002 / 5678 · D003 sin PIN", color = Mute)
            val shift by com.veplayer.app.fleet.ShiftTracker.shift.collectAsState()
            val shiftSum by com.veplayer.app.fleet.ShiftTracker.summary.collectAsState()
            Text(
                if (shift.status == "open") {
                    "Turno #${shift.id} · ${"%.1f".format(shift.distanceKm)} km" +
                        (shift.ecoScore?.let { " · eco $it (${shift.ecoBand})" } ?: "")
                } else if (shiftSum.show) {
                    shiftSum.message
                } else if (shift.status == "closed") {
                    "Último turno cerrado · ${"%.1f".format(shift.distanceKm)} km" +
                        (shift.ecoScore?.let { " · eco $it" } ?: "")
                } else {
                    "Turno: —"
                },
                color = if (shiftSum.show) Teal else Mute,
            )
            var sumOn by remember { mutableStateOf(prefs.shiftSummaryEnabled) }
            var sumTts by remember { mutableStateOf(prefs.shiftSummaryTts) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Resumen al cerrar turno", color = Mist)
                Switch(
                    checked = sumOn,
                    onCheckedChange = {
                        sumOn = it
                        prefs.shiftSummaryEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS resumen turno", color = Mist)
                Switch(
                    checked = sumTts,
                    onCheckedChange = {
                        sumTts = it
                        prefs.shiftSummaryTts = it
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val r =
                                withContext(Dispatchers.IO) {
                                    com.veplayer.app.fleet.ShiftTracker.start(prefs)
                                }
                            r.onSuccess { status = "Turno #${it.id} abierto" }
                                .onFailure { status = "Turno start: ${it.message}" }
                        }
                    },
                ) { Text("Abrir turno") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val r =
                                withContext(Dispatchers.IO) {
                                    com.veplayer.app.fleet.ShiftTracker.end(prefs)
                                }
                            r.onSuccess {
                                val sum = com.veplayer.app.fleet.ShiftTracker.summary.value
                                status =
                                    if (sum.show) {
                                        sum.message
                                    } else {
                                        "Turno cerrado · ${"%.1f".format(it.distanceKm)} km"
                                    }
                            }.onFailure { status = "Turno end: ${it.message}" }
                        }
                    },
                ) { Text("Cerrar turno") }
            }
            var scoreOn by remember { mutableStateOf(prefs.driverScoreEnabled) }
            var scoreTts by remember { mutableStateOf(prefs.driverScoreTts) }
            var scoreSim by remember {
                mutableStateOf(
                    if (prefs.driverScoreSimScore > 0f) prefs.driverScoreSimScore.toInt().toString() else "0",
                )
            }
            val scoreSt by com.veplayer.app.vehicle.DriverScoreMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Scorecard seguridad", color = Mist)
                Switch(
                    checked = scoreOn,
                    onCheckedChange = {
                        scoreOn = it
                        prefs.driverScoreEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS scorecard", color = Mist)
                Switch(
                    checked = scoreTts,
                    onCheckedChange = {
                        scoreTts = it
                        prefs.driverScoreTts = it
                    },
                )
            }
            OutlinedTextField(
                value = scoreSim,
                onValueChange = { scoreSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim score 1–100 (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.driverScoreSimScore = scoreSim.toFloatOrNull() ?: 0f
                    status =
                        "Score sim ${prefs.driverScoreSimScore.toInt()} · warn ${prefs.driverScoreWarn.toInt()} / alert ${prefs.driverScoreAlert.toInt()}"
                },
            ) { Text("Aplicar sim score") }
            Text(
                if (scoreSt.active) {
                    "${scoreSt.label}" +
                        if (scoreSt.harshBrakeEvents + scoreSt.harshAccelEvents + scoreSt.seatbeltEvents + scoreSt.impactEvents > 0) {
                            " · h${scoreSt.harshBrakeEvents}/${scoreSt.harshAccelEvents} b${scoreSt.seatbeltEvents} i${scoreSt.impactEvents}"
                        } else {
                            ""
                        }
                } else {
                    "Score idle (abrir turno · warn ${prefs.driverScoreWarn.toInt()} / alert ${prefs.driverScoreAlert.toInt()})"
                },
                color = if (scoreSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var ecoLiveOn by remember { mutableStateOf(prefs.ecoLiveEnabled) }
            var ecoLiveTtsOn by remember { mutableStateOf(prefs.ecoLiveTts) }
            var ecoLiveSim by remember {
                mutableStateOf(
                    if (prefs.ecoLiveSimScore > 0f) prefs.ecoLiveSimScore.toInt().toString() else "0",
                )
            }
            val ecoLiveSt by com.veplayer.app.vehicle.EcoLiveMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso eco en vivo", color = Mist)
                Switch(
                    checked = ecoLiveOn,
                    onCheckedChange = {
                        ecoLiveOn = it
                        prefs.ecoLiveEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS eco", color = Mist)
                Switch(
                    checked = ecoLiveTtsOn,
                    onCheckedChange = {
                        ecoLiveTtsOn = it
                        prefs.ecoLiveTts = it
                    },
                )
            }
            OutlinedTextField(
                value = ecoLiveSim,
                onValueChange = { ecoLiveSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim eco 1–100 (0=turno)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.ecoLiveSimScore = ecoLiveSim.toFloatOrNull() ?: 0f
                    status =
                        "Eco sim ${prefs.ecoLiveSimScore.toInt()} · warn ${prefs.ecoLiveWarn.toInt()} / alert ${prefs.ecoLiveAlert.toInt()}"
                },
            ) { Text("Aplicar sim eco") }
            Text(
                if (ecoLiveSt.active) {
                    ecoLiveSt.label
                } else {
                    "Eco idle (abrir turno · warn ${prefs.ecoLiveWarn.toInt()} / alert ${prefs.ecoLiveAlert.toInt()})"
                },
                color = if (ecoLiveSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var runtimeOn by remember { mutableStateOf(prefs.engineRuntimeEnabled) }
            var runtimeTtsOn by remember { mutableStateOf(prefs.engineRuntimeTts) }
            var runtimeSim by remember {
                mutableStateOf(
                    if (prefs.engineRuntimeSimHours > 0f) {
                        String.format("%.1f", prefs.engineRuntimeSimHours)
                    } else {
                        "0"
                    },
                )
            }
            val runtimeSt by com.veplayer.app.vehicle.EngineRuntimeMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso tiempo de motor (011F)", color = Mist)
                Switch(
                    checked = runtimeOn,
                    onCheckedChange = {
                        runtimeOn = it
                        prefs.engineRuntimeEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS motor", color = Mist)
                Switch(
                    checked = runtimeTtsOn,
                    onCheckedChange = {
                        runtimeTtsOn = it
                        prefs.engineRuntimeTts = it
                    },
                )
            }
            OutlinedTextField(
                value = runtimeSim,
                onValueChange = {
                    runtimeSim = it.filter { c -> c.isDigit() || c == '.' }.take(5)
                },
                label = { Text("Sim horas motor (0=OBD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.engineRuntimeSimHours = runtimeSim.toFloatOrNull() ?: 0f
                    status =
                        "Runtime sim ${prefs.engineRuntimeSimHours} h · warn ${prefs.engineRuntimeWarnHours} / alert ${prefs.engineRuntimeAlertHours}"
                },
            ) { Text("Aplicar sim motor") }
            Text(
                if (runtimeSt.runtimeSec != null) {
                    runtimeSt.label +
                        if (runtimeSt.showWarn) " · ${runtimeSt.band}" else ""
                } else {
                    "Motor idle (OBD 011F · warn ${prefs.engineRuntimeWarnHours} h / alert ${prefs.engineRuntimeAlertHours} h)"
                },
                color = if (runtimeSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var fatigueOn by remember { mutableStateOf(prefs.fatigueEnabled) }
            var fatigueTts by remember { mutableStateOf(prefs.fatigueTts) }
            var fatigueSim by remember { mutableStateOf(prefs.fatigueSimHours.toInt().toString()) }
            val fatigue by com.veplayer.app.vehicle.ShiftFatigueMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso fatiga (turno largo)", color = Mist)
                Switch(
                    checked = fatigueOn,
                    onCheckedChange = {
                        fatigueOn = it
                        prefs.fatigueEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS fatiga", color = Mist)
                Switch(
                    checked = fatigueTts,
                    onCheckedChange = {
                        fatigueTts = it
                        prefs.fatigueTts = it
                    },
                )
            }
            OutlinedTextField(
                value = fatigueSim,
                onValueChange = { fatigueSim = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                label = { Text("Sim horas turno (0=real)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.fatigueSimHours = fatigueSim.toFloatOrNull() ?: 0f
                    status = "Fatiga sim ${prefs.fatigueSimHours} h · umbrales ${prefs.fatigueWarnHours}/${prefs.fatigueAlertHours} h"
                },
            ) { Text("Aplicar sim fatiga") }
            Text(
                if (fatigue.open) {
                    "Fatiga · ${fatigue.label} · ${fatigue.band}"
                } else {
                    "Fatiga idle (abrir turno o sim horas)"
                },
                color = if (fatigue.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var restOn by remember { mutableStateOf(prefs.restBreakEnabled) }
            var restTts by remember { mutableStateOf(prefs.restBreakTts) }
            var restSim by remember {
                mutableStateOf(
                    if (prefs.restSimDriveMin > 0f) prefs.restSimDriveMin.toInt().toString() else "0",
                )
            }
            val restSt by com.veplayer.app.vehicle.RestBreakMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso descanso (conducción continua)", color = Mist)
                Switch(
                    checked = restOn,
                    onCheckedChange = {
                        restOn = it
                        prefs.restBreakEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS descanso", color = Mist)
                Switch(
                    checked = restTts,
                    onCheckedChange = {
                        restTts = it
                        prefs.restBreakTts = it
                    },
                )
            }
            OutlinedTextField(
                value = restSim,
                onValueChange = { restSim = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                label = { Text("Sim min conduciendo (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.restSimDriveMin = restSim.toFloatOrNull() ?: 0f
                    status =
                        "Rest sim ${prefs.restSimDriveMin.toInt()} min · warn ${prefs.restDriveWarnMin.toInt()} / alert ${prefs.restDriveAlertMin.toInt()} · reset ${prefs.restResetMin.toInt()} min"
                },
            ) { Text("Aplicar sim descanso") }
            Text(
                if (restSt.drivingSec > 0f || restSt.showWarn) {
                    "${restSt.label} · ${restSt.band}"
                } else {
                    "Rest idle (reset tras ${prefs.restResetMin.toInt()} min parado)"
                },
                color = if (restSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
        }

        PanelBlock("Clima HVAC") {
            var hvacOn by remember { mutableStateOf(prefs.hvacPanelEnabled) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Panel clima", color = Mist)
                Switch(
                    checked = hvacOn,
                    onCheckedChange = {
                        hvacOn = it
                        prefs.hvacPanelEnabled = it
                    },
                )
            }
            Text(
                "Target ± · AC · fan (override local en mock/obd_sim). Cabina deriva al objetivo.",
                color = Mute,
                fontSize = 12.sp,
            )
            if (hvacOn) {
                com.veplayer.app.ui.climate.HvacClimatePanel()
            }
            var cabinOn by remember { mutableStateOf(prefs.cabinOvertempEnabled) }
            var cabinTts by remember { mutableStateOf(prefs.cabinOvertempTts) }
            var cabinSim by remember { mutableStateOf(if (prefs.cabinOvertempSimC > 0f) prefs.cabinOvertempSimC.toInt().toString() else "0") }
            val cabinHot by com.veplayer.app.vehicle.CabinOvertempMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso cabina caliente", color = Mist)
                Switch(
                    checked = cabinOn,
                    onCheckedChange = {
                        cabinOn = it
                        prefs.cabinOvertempEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS overtemp", color = Mist)
                Switch(
                    checked = cabinTts,
                    onCheckedChange = {
                        cabinTts = it
                        prefs.cabinOvertempTts = it
                    },
                )
            }
            OutlinedTextField(
                value = cabinSim,
                onValueChange = { cabinSim = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                label = { Text("Sim cabina °C (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.cabinOvertempSimC = cabinSim.toFloatOrNull() ?: 0f
                    status =
                        "Cabina sim ${prefs.cabinOvertempSimC}° · warn ${prefs.cabinWarnC.toInt()} / alert ${prefs.cabinAlertC.toInt()}"
                },
            ) { Text("Aplicar sim cabina") }
            Text(
                if (cabinHot.cabinC != null) {
                    "Overtemp · ${cabinHot.label} · ${cabinHot.band}"
                } else {
                    "Overtemp idle"
                },
                color = if (cabinHot.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var iceOn by remember { mutableStateOf(prefs.iceEnabled) }
            var iceTtsOn by remember { mutableStateOf(prefs.iceTts) }
            var iceSimTxt by remember {
                mutableStateOf(if (prefs.iceSimOn) prefs.iceSimC.toInt().toString() else "")
            }
            val iceSt by com.veplayer.app.vehicle.IceFrostMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso hielo / escarcha", color = Mist)
                Switch(
                    checked = iceOn,
                    onCheckedChange = {
                        iceOn = it
                        prefs.iceEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS hielo", color = Mist)
                Switch(
                    checked = iceTtsOn,
                    onCheckedChange = {
                        iceTtsOn = it
                        prefs.iceTts = it
                    },
                )
            }
            OutlinedTextField(
                value = iceSimTxt,
                onValueChange = {
                    iceSimTxt = it.filter { c -> c.isDigit() || c == '-' || c == '.' }.take(5)
                },
                label = { Text("Sim exterior °C (vacío=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val v = iceSimTxt.toFloatOrNull()
                        if (v != null) {
                            prefs.iceSimC = v
                            prefs.iceSimOn = true
                            status =
                                "Hielo sim ${prefs.iceSimC}° · warn ≤${prefs.iceWarnC.toInt()} / alert ≤${prefs.iceAlertC.toInt()}"
                        } else {
                            prefs.iceSimOn = false
                            status = "Hielo sim off (live)"
                        }
                    },
                ) { Text("Aplicar sim hielo") }
                OutlinedButton(
                    onClick = {
                        prefs.iceSimOn = false
                        iceSimTxt = ""
                        status = "Hielo sim off"
                    },
                ) { Text("Live") }
            }
            Text(
                if (iceSt.outdoorC != null) {
                    "Ext · ${iceSt.label} · ${iceSt.band}"
                } else {
                    "Hielo idle (warn ≤${prefs.iceWarnC.toInt()} / alert ≤${prefs.iceAlertC.toInt()} °C)"
                },
                color = if (iceSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var coolOn by remember { mutableStateOf(prefs.coolantEnabled) }
            var coolTts by remember { mutableStateOf(prefs.coolantTts) }
            var coolSim by remember {
                mutableStateOf(if (prefs.coolantSimC > 0f) prefs.coolantSimC.toInt().toString() else "0")
            }
            val coolHot by com.veplayer.app.vehicle.CoolantOverheatMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso refrigerante motor", color = Mist)
                Switch(
                    checked = coolOn,
                    onCheckedChange = {
                        coolOn = it
                        prefs.coolantEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS refrigerante", color = Mist)
                Switch(
                    checked = coolTts,
                    onCheckedChange = {
                        coolTts = it
                        prefs.coolantTts = it
                    },
                )
            }
            OutlinedTextField(
                value = coolSim,
                onValueChange = { coolSim = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                label = { Text("Sim refrigerante °C (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.coolantSimC = coolSim.toFloatOrNull() ?: 0f
                    status =
                        "Coolant sim ${prefs.coolantSimC}° · warn ${prefs.coolantWarnC.toInt()} / alert ${prefs.coolantAlertC.toInt()}"
                },
            ) { Text("Aplicar sim refrigerante") }
            Text(
                if (coolHot.coolantC != null) {
                    "Coolant · ${coolHot.label} · ${coolHot.band}"
                } else {
                    "Coolant idle"
                },
                color = if (coolHot.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var oilOn by remember { mutableStateOf(prefs.oilTempEnabled) }
            var oilTts by remember { mutableStateOf(prefs.oilTempTts) }
            var oilSim by remember {
                mutableStateOf(
                    if (prefs.oilTempSimC > 0f) prefs.oilTempSimC.toInt().toString() else "0",
                )
            }
            val oilSt by com.veplayer.app.vehicle.OilTempMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso aceite motor (015C)", color = Mist)
                Switch(
                    checked = oilOn,
                    onCheckedChange = {
                        oilOn = it
                        prefs.oilTempEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS aceite", color = Mist)
                Switch(
                    checked = oilTts,
                    onCheckedChange = {
                        oilTts = it
                        prefs.oilTempTts = it
                    },
                )
            }
            OutlinedTextField(
                value = oilSim,
                onValueChange = { oilSim = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                label = { Text("Sim aceite °C (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.oilTempSimC = oilSim.toFloatOrNull() ?: 0f
                    status =
                        "Oil sim ${prefs.oilTempSimC}° · warn ${prefs.oilTempWarnC.toInt()} / alert ${prefs.oilTempAlertC.toInt()}"
                },
            ) { Text("Aplicar sim aceite") }
            Text(
                if (oilSt.oilTempC != null) {
                    "Aceite · ${oilSt.label} · ${oilSt.band}"
                } else {
                    "Aceite idle (warn ${prefs.oilTempWarnC.toInt()}° / alert ${prefs.oilTempAlertC.toInt()}°)"
                },
                color = if (oilSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var catOn by remember { mutableStateOf(prefs.catalystEnabled) }
            var catTts by remember { mutableStateOf(prefs.catalystTts) }
            var catSim by remember {
                mutableStateOf(
                    if (prefs.catalystSimC > 0f) prefs.catalystSimC.toInt().toString() else "0",
                )
            }
            val catSt by com.veplayer.app.vehicle.CatalystTempMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso catalizador (0134)", color = Mist)
                Switch(
                    checked = catOn,
                    onCheckedChange = {
                        catOn = it
                        prefs.catalystEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS catalizador", color = Mist)
                Switch(
                    checked = catTts,
                    onCheckedChange = {
                        catTts = it
                        prefs.catalystTts = it
                    },
                )
            }
            OutlinedTextField(
                value = catSim,
                onValueChange = { catSim = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Sim catalizador °C (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.catalystSimC = catSim.toFloatOrNull() ?: 0f
                    status =
                        "Cat sim ${prefs.catalystSimC.toInt()}° · warn ${prefs.catalystWarnC.toInt()} / alert ${prefs.catalystAlertC.toInt()}"
                },
            ) { Text("Aplicar sim catalizador") }
            Text(
                if (catSt.catalystTempC != null) {
                    "${catSt.label} · ${catSt.band}"
                } else {
                    "Cat idle (warn ${prefs.catalystWarnC.toInt()}° / alert ${prefs.catalystAlertC.toInt()}°)"
                },
                color = if (catSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var iatOn by remember { mutableStateOf(prefs.intakeAirEnabled) }
            var iatTts by remember { mutableStateOf(prefs.intakeAirTts) }
            var iatSim by remember {
                mutableStateOf(
                    if (prefs.intakeAirSimC > 0f) prefs.intakeAirSimC.toInt().toString() else "0",
                )
            }
            val iatSt by com.veplayer.app.vehicle.IntakeAirMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso admisión (010F)", color = Mist)
                Switch(
                    checked = iatOn,
                    onCheckedChange = {
                        iatOn = it
                        prefs.intakeAirEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS admisión", color = Mist)
                Switch(
                    checked = iatTts,
                    onCheckedChange = {
                        iatTts = it
                        prefs.intakeAirTts = it
                    },
                )
            }
            OutlinedTextField(
                value = iatSim,
                onValueChange = { iatSim = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                label = { Text("Sim IAT °C (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.intakeAirSimC = iatSim.toFloatOrNull() ?: 0f
                    status =
                        "IAT sim ${prefs.intakeAirSimC}° · warn ${prefs.intakeAirWarnC.toInt()} / alert ${prefs.intakeAirAlertC.toInt()}"
                },
            ) { Text("Aplicar sim IAT") }
            Text(
                if (iatSt.intakeAirC != null) {
                    "IAT · ${iatSt.label} · ${iatSt.band}"
                } else {
                    "IAT idle (warn ${prefs.intakeAirWarnC.toInt()}° / alert ${prefs.intakeAirAlertC.toInt()}°)"
                },
                color = if (iatSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var fuelRateOn by remember { mutableStateOf(prefs.fuelRateEnabled) }
            var fuelRateTts by remember { mutableStateOf(prefs.fuelRateTts) }
            var fuelRateSim by remember {
                mutableStateOf(
                    if (prefs.fuelRateSimLph > 0f) prefs.fuelRateSimLph.toInt().toString() else "0",
                )
            }
            val fuelRateSt by com.veplayer.app.vehicle.FuelRateMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso consumo (015E)", color = Mist)
                Switch(
                    checked = fuelRateOn,
                    onCheckedChange = {
                        fuelRateOn = it
                        prefs.fuelRateEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS consumo", color = Mist)
                Switch(
                    checked = fuelRateTts,
                    onCheckedChange = {
                        fuelRateTts = it
                        prefs.fuelRateTts = it
                    },
                )
            }
            OutlinedTextField(
                value = fuelRateSim,
                onValueChange = { fuelRateSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim L/h (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.fuelRateSimLph = fuelRateSim.toFloatOrNull() ?: 0f
                    status =
                        "Fuel rate sim ${prefs.fuelRateSimLph.toInt()} L/h · warn ${prefs.fuelRateWarnLph.toInt()} / alert ${prefs.fuelRateAlertLph.toInt()}"
                },
            ) { Text("Aplicar sim consumo") }
            Text(
                if (fuelRateSt.fuelRateLph != null) {
                    "${fuelRateSt.label} · ${fuelRateSt.band}"
                } else {
                    "Consumo idle (warn ${prefs.fuelRateWarnLph.toInt()} / alert ${prefs.fuelRateAlertLph.toInt()} L/h)"
                },
                color = if (fuelRateSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var mafOn by remember { mutableStateOf(prefs.mafEnabled) }
            var mafTts by remember { mutableStateOf(prefs.mafTts) }
            var mafSim by remember {
                mutableStateOf(
                    if (prefs.mafSimGps > 0f) prefs.mafSimGps.toInt().toString() else "0",
                )
            }
            val mafSt by com.veplayer.app.vehicle.MafAirflowMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso flujo MAF (0110)", color = Mist)
                Switch(
                    checked = mafOn,
                    onCheckedChange = {
                        mafOn = it
                        prefs.mafEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS MAF", color = Mist)
                Switch(
                    checked = mafTts,
                    onCheckedChange = {
                        mafTts = it
                        prefs.mafTts = it
                    },
                )
            }
            OutlinedTextField(
                value = mafSim,
                onValueChange = { mafSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim MAF g/s (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.mafSimGps = mafSim.toFloatOrNull() ?: 0f
                    status =
                        "MAF sim ${prefs.mafSimGps.toInt()} g/s · warn ${prefs.mafWarnGps.toInt()} / alert ${prefs.mafAlertGps.toInt()}"
                },
            ) { Text("Aplicar sim MAF") }
            Text(
                if (mafSt.mafGps != null) {
                    "${mafSt.label} · ${mafSt.band}"
                } else {
                    "MAF idle (warn ${prefs.mafWarnGps.toInt()} / alert ${prefs.mafAlertGps.toInt()} g/s)"
                },
                color = if (mafSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var fuelPressOn by remember { mutableStateOf(prefs.fuelPressEnabled) }
            var fuelPressTts by remember { mutableStateOf(prefs.fuelPressTts) }
            var fuelPressSim by remember {
                mutableStateOf(
                    if (prefs.fuelPressSimKpa > 0f) prefs.fuelPressSimKpa.toInt().toString() else "0",
                )
            }
            val fuelPressSt by com.veplayer.app.vehicle.FuelPressureMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso presión combustible (010A)", color = Mist)
                Switch(
                    checked = fuelPressOn,
                    onCheckedChange = {
                        fuelPressOn = it
                        prefs.fuelPressEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS presión combustible", color = Mist)
                Switch(
                    checked = fuelPressTts,
                    onCheckedChange = {
                        fuelPressTts = it
                        prefs.fuelPressTts = it
                    },
                )
            }
            OutlinedTextField(
                value = fuelPressSim,
                onValueChange = { fuelPressSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim presión kPa (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.fuelPressSimKpa = fuelPressSim.toFloatOrNull() ?: 0f
                    status =
                        "FuelP sim ${prefs.fuelPressSimKpa.toInt()} kPa · warn ≤${prefs.fuelPressWarnKpa.toInt()} / alert ≤${prefs.fuelPressAlertKpa.toInt()}"
                },
            ) { Text("Aplicar sim presión") }
            Text(
                if (fuelPressSt.pressureKpa != null) {
                    "${fuelPressSt.label} · ${fuelPressSt.band}"
                } else {
                    "FuelP idle (warn ≤${prefs.fuelPressWarnKpa.toInt()} / alert ≤${prefs.fuelPressAlertKpa.toInt()} kPa)"
                },
                color = if (fuelPressSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var baroOn by remember { mutableStateOf(prefs.baroEnabled) }
            var baroTts by remember { mutableStateOf(prefs.baroTts) }
            var baroSim by remember {
                mutableStateOf(
                    if (prefs.baroSimKpa > 0f) prefs.baroSimKpa.toInt().toString() else "0",
                )
            }
            val baroSt by com.veplayer.app.vehicle.BarometricPressureMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso barométrica (0133)", color = Mist)
                Switch(
                    checked = baroOn,
                    onCheckedChange = {
                        baroOn = it
                        prefs.baroEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS barométrica", color = Mist)
                Switch(
                    checked = baroTts,
                    onCheckedChange = {
                        baroTts = it
                        prefs.baroTts = it
                    },
                )
            }
            OutlinedTextField(
                value = baroSim,
                onValueChange = { baroSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim baro kPa (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.baroSimKpa = baroSim.toFloatOrNull() ?: 0f
                    status =
                        "Baro sim ${prefs.baroSimKpa.toInt()} kPa · low ${prefs.baroAlertLowKpa.toInt()}/${prefs.baroWarnLowKpa.toInt()} · high ${prefs.baroWarnHighKpa.toInt()}/${prefs.baroAlertHighKpa.toInt()}"
                },
            ) { Text("Aplicar sim baro") }
            Text(
                if (baroSt.baroKpa != null) {
                    "${baroSt.label} · ${baroSt.band}"
                } else {
                    "Baro idle (fuera de rango)"
                },
                color = if (baroSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var timingOn by remember { mutableStateOf(prefs.timingEnabled) }
            var timingTts by remember { mutableStateOf(prefs.timingTts) }
            var timingSim by remember {
                mutableStateOf(
                    if (prefs.timingSimDeg != 0f) prefs.timingSimDeg.toInt().toString() else "0",
                )
            }
            val timingSt by com.veplayer.app.vehicle.TimingAdvanceMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso timing advance (010E)", color = Mist)
                Switch(
                    checked = timingOn,
                    onCheckedChange = {
                        timingOn = it
                        prefs.timingEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS timing", color = Mist)
                Switch(
                    checked = timingTts,
                    onCheckedChange = {
                        timingTts = it
                        prefs.timingTts = it
                    },
                )
            }
            OutlinedTextField(
                value = timingSim,
                onValueChange = { timingSim = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                label = { Text("Sim timing ° (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.timingSimDeg = timingSim.toFloatOrNull() ?: 0f
                    status =
                        "Timing sim ${prefs.timingSimDeg.toInt()}° · warn ${prefs.timingWarnDeg.toInt()} / alert ${prefs.timingAlertDeg.toInt()}"
                },
            ) { Text("Aplicar sim timing") }
            Text(
                if (timingSt.timingDeg != null) {
                    "${timingSt.label} · ${timingSt.band}"
                } else {
                    "Timing idle (warn ≥${prefs.timingWarnDeg.toInt()}° / alert ≥${prefs.timingAlertDeg.toInt()}°)"
                },
                color = if (timingSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var o2On by remember { mutableStateOf(prefs.o2Enabled) }
            var o2Tts by remember { mutableStateOf(prefs.o2Tts) }
            var o2Sim by remember {
                mutableStateOf(
                    if (prefs.o2SimVolts > 0f) prefs.o2SimVolts.toString() else "0",
                )
            }
            val o2St by com.veplayer.app.vehicle.O2VoltageMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso O2 voltaje (014A)", color = Mist)
                Switch(
                    checked = o2On,
                    onCheckedChange = {
                        o2On = it
                        prefs.o2Enabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS O2", color = Mist)
                Switch(
                    checked = o2Tts,
                    onCheckedChange = {
                        o2Tts = it
                        prefs.o2Tts = it
                    },
                )
            }
            OutlinedTextField(
                value = o2Sim,
                onValueChange = { o2Sim = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                label = { Text("Sim O2 V (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.o2SimVolts = o2Sim.toFloatOrNull() ?: 0f
                    status =
                        "O2 sim ${prefs.o2SimVolts} V · low ${prefs.o2AlertLowV}/${prefs.o2WarnLowV} · high ${prefs.o2WarnHighV}/${prefs.o2AlertHighV}"
                },
            ) { Text("Aplicar sim O2") }
            Text(
                if (o2St.o2Volts != null) {
                    "${o2St.label} · ${o2St.band}"
                } else {
                    "O2 idle (stuck lean/rich)"
                },
                color = if (o2St.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            Text("Fase 16 OBD (0143/45/49/4B/4D):", color = Mist)
            val absLoadSt by com.veplayer.app.vehicle.AbsoluteLoadMonitor.state.collectAsState()
            val relThrSt by com.veplayer.app.vehicle.RelativeThrottleMonitor.state.collectAsState()
            val accelPedalSt by com.veplayer.app.vehicle.AccelPedalMonitor.state.collectAsState()
            val o2B2St by com.veplayer.app.vehicle.O2B2VoltageMonitor.state.collectAsState()
            val egrSt by com.veplayer.app.vehicle.EgrErrorMonitor.state.collectAsState()
            var f16Abs by remember {
                mutableStateOf(if (prefs.absLoadSimPct > 0f) prefs.absLoadSimPct.toInt().toString() else "0")
            }
            var f16Rel by remember {
                mutableStateOf(if (prefs.relThrSimPct > 0f) prefs.relThrSimPct.toInt().toString() else "0")
            }
            var f16Ped by remember {
                mutableStateOf(if (prefs.accelPedalSimPct > 0f) prefs.accelPedalSimPct.toInt().toString() else "0")
            }
            var f16O2b2 by remember {
                mutableStateOf(if (prefs.o2B2SimVolts > 0f) prefs.o2B2SimVolts.toString() else "0")
            }
            var f16Egr by remember {
                mutableStateOf(if (prefs.egrSimPct != 0f) prefs.egrSimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f16Abs,
                    onValueChange = { f16Abs = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("AbsL %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f16Rel,
                    onValueChange = { f16Rel = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("RelT %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f16Ped,
                    onValueChange = { f16Ped = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Pedal %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f16O2b2,
                    onValueChange = { f16O2b2 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2B2 V") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f16Egr,
                    onValueChange = { f16Egr = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("EGR %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.absLoadSimPct = f16Abs.toFloatOrNull() ?: 0f
                    prefs.relThrSimPct = f16Rel.toFloatOrNull() ?: 0f
                    prefs.accelPedalSimPct = f16Ped.toFloatOrNull() ?: 0f
                    prefs.o2B2SimVolts = f16O2b2.toFloatOrNull() ?: 0f
                    prefs.egrSimPct = f16Egr.toFloatOrNull() ?: 0f
                    status = "Fase 16 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 16") }
            Text(
                listOfNotNull(
                    absLoadSt.label.takeIf { it.isNotBlank() },
                    relThrSt.label.takeIf { it.isNotBlank() },
                    accelPedalSt.label.takeIf { it.isNotBlank() },
                    o2B2St.label.takeIf { it.isNotBlank() },
                    egrSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 16 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 17 OBD (0144/4E/52/53/59):", color = Mist)
            val equivSt by com.veplayer.app.vehicle.EquivRatioMonitor.state.collectAsState()
            val evapPurSt by com.veplayer.app.vehicle.EvapPurgeMonitor.state.collectAsState()
            val ethanolSt by com.veplayer.app.vehicle.EthanolPctMonitor.state.collectAsState()
            val evapVapSt by com.veplayer.app.vehicle.EvapVaporMonitor.state.collectAsState()
            val railAbsSt by com.veplayer.app.vehicle.FuelRailAbsMonitor.state.collectAsState()
            var f17Lambda by remember {
                mutableStateOf(if (prefs.equivSimRatio > 0f) prefs.equivSimRatio.toString() else "0")
            }
            var f17Evap by remember {
                mutableStateOf(if (prefs.evapPurgeSimPct > 0f) prefs.evapPurgeSimPct.toInt().toString() else "0")
            }
            var f17Eth by remember {
                mutableStateOf(if (prefs.ethanolSimPct > 0f) prefs.ethanolSimPct.toInt().toString() else "0")
            }
            var f17Vap by remember {
                mutableStateOf(if (prefs.evapVaporSimPa != 0f) prefs.evapVaporSimPa.toInt().toString() else "0")
            }
            var f17Rail by remember {
                mutableStateOf(if (prefs.railAbsSimKpa > 0f) prefs.railAbsSimKpa.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f17Lambda,
                    onValueChange = { f17Lambda = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("Lambda") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f17Evap,
                    onValueChange = { f17Evap = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Evap %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f17Eth,
                    onValueChange = { f17Eth = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Etanol %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f17Vap,
                    onValueChange = { f17Vap = it.filter { c -> c.isDigit() || c == '-' }.take(6) },
                    label = { Text("Vapor Pa") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f17Rail,
                    onValueChange = { f17Rail = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Rail kPa") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.equivSimRatio = f17Lambda.toFloatOrNull() ?: 0f
                    prefs.evapPurgeSimPct = f17Evap.toFloatOrNull() ?: 0f
                    prefs.ethanolSimPct = f17Eth.toFloatOrNull() ?: 0f
                    prefs.evapVaporSimPa = f17Vap.toFloatOrNull() ?: 0f
                    prefs.railAbsSimKpa = f17Rail.toFloatOrNull() ?: 0f
                    status = "Fase 17 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 17") }
            Text(
                listOfNotNull(
                    equivSt.label.takeIf { it.isNotBlank() },
                    evapPurSt.label.takeIf { it.isNotBlank() },
                    ethanolSt.label.takeIf { it.isNotBlank() },
                    evapVapSt.label.takeIf { it.isNotBlank() },
                    railAbsSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 17 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 18 OBD (014C/5A/61/62/70):", color = Mist)
            val egrCmdSt by com.veplayer.app.vehicle.CommandedEgrMonitor.state.collectAsState()
            val relApedSt by com.veplayer.app.vehicle.RelAccelPedalMonitor.state.collectAsState()
            val drvTorqueSt by com.veplayer.app.vehicle.DriverTorqueMonitor.state.collectAsState()
            val actTorqueSt by com.veplayer.app.vehicle.ActualTorqueMonitor.state.collectAsState()
            val catB2St by com.veplayer.app.vehicle.CatalystB2Monitor.state.collectAsState()
            var f18EgrCmd by remember {
                mutableStateOf(if (prefs.egrCmdSimPct > 0f) prefs.egrCmdSimPct.toInt().toString() else "0")
            }
            var f18RelAp by remember {
                mutableStateOf(if (prefs.relApedSimPct > 0f) prefs.relApedSimPct.toInt().toString() else "0")
            }
            var f18DrvT by remember {
                mutableStateOf(if (prefs.drvTorqueSimPct != 0f) prefs.drvTorqueSimPct.toInt().toString() else "0")
            }
            var f18ActT by remember {
                mutableStateOf(if (prefs.actTorqueSimPct != 0f) prefs.actTorqueSimPct.toInt().toString() else "0")
            }
            var f18CatB2 by remember {
                mutableStateOf(if (prefs.catB2SimC > 0f) prefs.catB2SimC.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f18EgrCmd,
                    onValueChange = { f18EgrCmd = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("EGRcmd %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f18RelAp,
                    onValueChange = { f18RelAp = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("RelAP %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f18DrvT,
                    onValueChange = { f18DrvT = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("DrvT %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f18ActT,
                    onValueChange = { f18ActT = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("ActT %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f18CatB2,
                    onValueChange = { f18CatB2 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("CatB2 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.egrCmdSimPct = f18EgrCmd.toFloatOrNull() ?: 0f
                    prefs.relApedSimPct = f18RelAp.toFloatOrNull() ?: 0f
                    prefs.drvTorqueSimPct = f18DrvT.toFloatOrNull() ?: 0f
                    prefs.actTorqueSimPct = f18ActT.toFloatOrNull() ?: 0f
                    prefs.catB2SimC = f18CatB2.toFloatOrNull() ?: 0f
                    status = "Fase 18 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 18") }
            Text(
                listOfNotNull(
                    egrCmdSt.label.takeIf { it.isNotBlank() },
                    relApedSt.label.takeIf { it.isNotBlank() },
                    drvTorqueSt.label.takeIf { it.isNotBlank() },
                    actTorqueSt.label.takeIf { it.isNotBlank() },
                    catB2St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 18 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 19 OBD (0171/72/73/74/75):", color = Mist)
            val catB1s2St by com.veplayer.app.vehicle.CatalystB1S2Monitor.state.collectAsState()
            val catB2s2St by com.veplayer.app.vehicle.CatalystB2S2Monitor.state.collectAsState()
            val catB1s3St by com.veplayer.app.vehicle.CatalystB1S3Monitor.state.collectAsState()
            val catB2s3St by com.veplayer.app.vehicle.CatalystB2S3Monitor.state.collectAsState()
            val catB1s4St by com.veplayer.app.vehicle.CatalystB1S4Monitor.state.collectAsState()
            var f19B1s2 by remember {
                mutableStateOf(if (prefs.catB1s2SimC > 0f) prefs.catB1s2SimC.toInt().toString() else "0")
            }
            var f19B2s2 by remember {
                mutableStateOf(if (prefs.catB2s2SimC > 0f) prefs.catB2s2SimC.toInt().toString() else "0")
            }
            var f19B1s3 by remember {
                mutableStateOf(if (prefs.catB1s3SimC > 0f) prefs.catB1s3SimC.toInt().toString() else "0")
            }
            var f19B2s3 by remember {
                mutableStateOf(if (prefs.catB2s3SimC > 0f) prefs.catB2s3SimC.toInt().toString() else "0")
            }
            var f19B1s4 by remember {
                mutableStateOf(if (prefs.catB1s4SimC > 0f) prefs.catB1s4SimC.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f19B1s2,
                    onValueChange = { f19B1s2 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S2 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f19B2s2,
                    onValueChange = { f19B2s2 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S2 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f19B1s3,
                    onValueChange = { f19B1s3 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S3 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f19B2s3,
                    onValueChange = { f19B2s3 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S3 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f19B1s4,
                    onValueChange = { f19B1s4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S4 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s2SimC = f19B1s2.toFloatOrNull() ?: 0f
                    prefs.catB2s2SimC = f19B2s2.toFloatOrNull() ?: 0f
                    prefs.catB1s3SimC = f19B1s3.toFloatOrNull() ?: 0f
                    prefs.catB2s3SimC = f19B2s3.toFloatOrNull() ?: 0f
                    prefs.catB1s4SimC = f19B1s4.toFloatOrNull() ?: 0f
                    status = "Fase 19 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 19") }
            Text(
                listOfNotNull(
                    catB1s2St.label.takeIf { it.isNotBlank() },
                    catB2s2St.label.takeIf { it.isNotBlank() },
                    catB1s3St.label.takeIf { it.isNotBlank() },
                    catB2s3St.label.takeIf { it.isNotBlank() },
                    catB1s4St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 19 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 20 OBD (0176/55/56/57/58):", color = Mist)
            val catB2s4St by com.veplayer.app.vehicle.CatalystB2S4Monitor.state.collectAsState()
            val stft2B1St by com.veplayer.app.vehicle.FuelTrimStft2B1Monitor.state.collectAsState()
            val ltft2B1St by com.veplayer.app.vehicle.FuelTrimLtft2B1Monitor.state.collectAsState()
            val stft2B2St by com.veplayer.app.vehicle.FuelTrimStft2B2Monitor.state.collectAsState()
            val ltft2B2St by com.veplayer.app.vehicle.FuelTrimLtft2B2Monitor.state.collectAsState()
            var f20B2s4 by remember {
                mutableStateOf(if (prefs.catB2s4SimC > 0f) prefs.catB2s4SimC.toInt().toString() else "0")
            }
            var f20St2B1 by remember {
                mutableStateOf(if (prefs.stft2B1SimPct != 0f) prefs.stft2B1SimPct.toInt().toString() else "0")
            }
            var f20Lt2B1 by remember {
                mutableStateOf(if (prefs.ltft2B1SimPct != 0f) prefs.ltft2B1SimPct.toInt().toString() else "0")
            }
            var f20St2B2 by remember {
                mutableStateOf(if (prefs.stft2B2SimPct != 0f) prefs.stft2B2SimPct.toInt().toString() else "0")
            }
            var f20Lt2B2 by remember {
                mutableStateOf(if (prefs.ltft2B2SimPct != 0f) prefs.ltft2B2SimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f20B2s4,
                    onValueChange = { f20B2s4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S4 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f20St2B1,
                    onValueChange = { f20St2B1 = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("ST2B1 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f20Lt2B1,
                    onValueChange = { f20Lt2B1 = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("LT2B1 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f20St2B2,
                    onValueChange = { f20St2B2 = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("ST2B2 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f20Lt2B2,
                    onValueChange = { f20Lt2B2 = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("LT2B2 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB2s4SimC = f20B2s4.toFloatOrNull() ?: 0f
                    prefs.stft2B1SimPct = f20St2B1.toFloatOrNull() ?: 0f
                    prefs.ltft2B1SimPct = f20Lt2B1.toFloatOrNull() ?: 0f
                    prefs.stft2B2SimPct = f20St2B2.toFloatOrNull() ?: 0f
                    prefs.ltft2B2SimPct = f20Lt2B2.toFloatOrNull() ?: 0f
                    status = "Fase 20 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 20") }
            Text(
                listOfNotNull(
                    catB2s4St.label.takeIf { it.isNotBlank() },
                    stft2B1St.label.takeIf { it.isNotBlank() },
                    ltft2B1St.label.takeIf { it.isNotBlank() },
                    stft2B2St.label.takeIf { it.isNotBlank() },
                    ltft2B2St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 20 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 21 OBD (0177/78/5D/5B/63):", color = Mist)
            val catB1s5St by com.veplayer.app.vehicle.CatalystB1S5Monitor.state.collectAsState()
            val catB2s5St by com.veplayer.app.vehicle.CatalystB2S5Monitor.state.collectAsState()
            val injectSt by com.veplayer.app.vehicle.FuelInjectTimingMonitor.state.collectAsState()
            val hybridSt by com.veplayer.app.vehicle.HybridBattLifeMonitor.state.collectAsState()
            val refTorqueSt by com.veplayer.app.vehicle.EngineRefTorqueMonitor.state.collectAsState()
            var f21B1s5 by remember {
                mutableStateOf(if (prefs.catB1s5SimC > 0f) prefs.catB1s5SimC.toInt().toString() else "0")
            }
            var f21B2s5 by remember {
                mutableStateOf(if (prefs.catB2s5SimC > 0f) prefs.catB2s5SimC.toInt().toString() else "0")
            }
            var f21Inject by remember {
                mutableStateOf(if (prefs.injectSimDeg != 0f) prefs.injectSimDeg.toInt().toString() else "0")
            }
            var f21Hybrid by remember {
                mutableStateOf(if (prefs.hybridSimPct > 0f) prefs.hybridSimPct.toInt().toString() else "0")
            }
            var f21RefT by remember {
                mutableStateOf(if (prefs.refTorqueSimNm > 0f) prefs.refTorqueSimNm.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f21B1s5,
                    onValueChange = { f21B1s5 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S5 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f21B2s5,
                    onValueChange = { f21B2s5 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S5 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f21Inject,
                    onValueChange = { f21Inject = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("Inject °") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f21Hybrid,
                    onValueChange = { f21Hybrid = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("HyBatt %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f21RefT,
                    onValueChange = { f21RefT = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("RefT Nm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s5SimC = f21B1s5.toFloatOrNull() ?: 0f
                    prefs.catB2s5SimC = f21B2s5.toFloatOrNull() ?: 0f
                    prefs.injectSimDeg = f21Inject.toFloatOrNull() ?: 0f
                    prefs.hybridSimPct = f21Hybrid.toFloatOrNull() ?: 0f
                    prefs.refTorqueSimNm = f21RefT.toFloatOrNull() ?: 0f
                    status = "Fase 21 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 21") }
            Text(
                listOfNotNull(
                    catB1s5St.label.takeIf { it.isNotBlank() },
                    catB2s5St.label.takeIf { it.isNotBlank() },
                    injectSt.label.takeIf { it.isNotBlank() },
                    hybridSt.label.takeIf { it.isNotBlank() },
                    refTorqueSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 21 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 22 OBD (0179/7A/47/48/54):", color = Mist)
            val catB1s6St by com.veplayer.app.vehicle.CatalystB1S6Monitor.state.collectAsState()
            val catB2s6St by com.veplayer.app.vehicle.CatalystB2S6Monitor.state.collectAsState()
            val thrBSt by com.veplayer.app.vehicle.ThrottleBMonitor.state.collectAsState()
            val thrCSt by com.veplayer.app.vehicle.ThrottleCMonitor.state.collectAsState()
            val milTimeSt by com.veplayer.app.vehicle.MilTimeOnMonitor.state.collectAsState()
            var f22B1s6 by remember {
                mutableStateOf(if (prefs.catB1s6SimC > 0f) prefs.catB1s6SimC.toInt().toString() else "0")
            }
            var f22B2s6 by remember {
                mutableStateOf(if (prefs.catB2s6SimC > 0f) prefs.catB2s6SimC.toInt().toString() else "0")
            }
            var f22ThrB by remember {
                mutableStateOf(if (prefs.thrBSimPct > 0f) prefs.thrBSimPct.toInt().toString() else "0")
            }
            var f22ThrC by remember {
                mutableStateOf(if (prefs.thrCSimPct > 0f) prefs.thrCSimPct.toInt().toString() else "0")
            }
            var f22MilT by remember {
                mutableStateOf(if (prefs.milTimeSimMin > 0) prefs.milTimeSimMin.toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f22B1s6,
                    onValueChange = { f22B1s6 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S6 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f22B2s6,
                    onValueChange = { f22B2s6 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S6 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f22ThrB,
                    onValueChange = { f22ThrB = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("ThrB %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f22ThrC,
                    onValueChange = { f22ThrC = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("ThrC %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f22MilT,
                    onValueChange = { f22MilT = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("MILt min") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s6SimC = f22B1s6.toFloatOrNull() ?: 0f
                    prefs.catB2s6SimC = f22B2s6.toFloatOrNull() ?: 0f
                    prefs.thrBSimPct = f22ThrB.toFloatOrNull() ?: 0f
                    prefs.thrCSimPct = f22ThrC.toFloatOrNull() ?: 0f
                    prefs.milTimeSimMin = f22MilT.toIntOrNull() ?: 0
                    status = "Fase 22 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 22") }
            Text(
                listOfNotNull(
                    catB1s6St.label.takeIf { it.isNotBlank() },
                    catB2s6St.label.takeIf { it.isNotBlank() },
                    thrBSt.label.takeIf { it.isNotBlank() },
                    thrCSt.label.takeIf { it.isNotBlank() },
                    milTimeSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 22 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 23 OBD (017B/7C/51/4F/50):", color = Mist)
            val catB1s7St by com.veplayer.app.vehicle.CatalystB1S7Monitor.state.collectAsState()
            val catB2s7St by com.veplayer.app.vehicle.CatalystB2S7Monitor.state.collectAsState()
            val fuelTypeSt by com.veplayer.app.vehicle.FuelTypeMonitor.state.collectAsState()
            val maxEquivSt by com.veplayer.app.vehicle.MaxEquivRatioMonitor.state.collectAsState()
            val maxMafSt by com.veplayer.app.vehicle.MaxMafGpsMonitor.state.collectAsState()
            var f23B1s7 by remember {
                mutableStateOf(if (prefs.catB1s7SimC > 0f) prefs.catB1s7SimC.toInt().toString() else "0")
            }
            var f23B2s7 by remember {
                mutableStateOf(if (prefs.catB2s7SimC > 0f) prefs.catB2s7SimC.toInt().toString() else "0")
            }
            var f23Fuel by remember {
                mutableStateOf(if (prefs.fuelTypeSimCode > 0) prefs.fuelTypeSimCode.toString() else "0")
            }
            var f23MaxL by remember {
                mutableStateOf(if (prefs.maxEquivSimRatio != 0f) prefs.maxEquivSimRatio.toString() else "0")
            }
            var f23MaxMaf by remember {
                mutableStateOf(if (prefs.maxMafSimGps > 0f) prefs.maxMafSimGps.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f23B1s7,
                    onValueChange = { f23B1s7 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S7 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f23B2s7,
                    onValueChange = { f23B2s7 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S7 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f23Fuel,
                    onValueChange = { f23Fuel = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Fuel code") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f23MaxL,
                    onValueChange = { f23MaxL = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("Maxλ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f23MaxMaf,
                    onValueChange = { f23MaxMaf = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("MaxMAF") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s7SimC = f23B1s7.toFloatOrNull() ?: 0f
                    prefs.catB2s7SimC = f23B2s7.toFloatOrNull() ?: 0f
                    prefs.fuelTypeSimCode = f23Fuel.toIntOrNull() ?: 0
                    prefs.maxEquivSimRatio = f23MaxL.toFloatOrNull() ?: 0f
                    prefs.maxMafSimGps = f23MaxMaf.toFloatOrNull() ?: 0f
                    status = "Fase 23 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 23") }
            Text(
                listOfNotNull(
                    catB1s7St.label.takeIf { it.isNotBlank() },
                    catB2s7St.label.takeIf { it.isNotBlank() },
                    fuelTypeSt.label.takeIf { it.isNotBlank() },
                    maxEquivSt.label.takeIf { it.isNotBlank() },
                    maxMafSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 23 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 24 OBD (017D/7E/64/66/65):", color = Mist)
            val catB1s8St by com.veplayer.app.vehicle.CatalystB1S8Monitor.state.collectAsState()
            val catB2s8St by com.veplayer.app.vehicle.CatalystB2S8Monitor.state.collectAsState()
            val maxAvailTorqueSt by com.veplayer.app.vehicle.MaxAvailTorqueMonitor.state.collectAsState()
            val mafIatSt by com.veplayer.app.vehicle.MafSensorIatMonitor.state.collectAsState()
            val auxInputSt by com.veplayer.app.vehicle.AuxInputStatusMonitor.state.collectAsState()
            var f24B1s8 by remember {
                mutableStateOf(if (prefs.catB1s8SimC > 0f) prefs.catB1s8SimC.toInt().toString() else "0")
            }
            var f24B2s8 by remember {
                mutableStateOf(if (prefs.catB2s8SimC > 0f) prefs.catB2s8SimC.toInt().toString() else "0")
            }
            var f24MaxTq by remember {
                mutableStateOf(if (prefs.maxAvailTorqueSimPct != 0f) prefs.maxAvailTorqueSimPct.toInt().toString() else "0")
            }
            var f24MafIat by remember {
                mutableStateOf(if (prefs.mafIatSimC > 0f) prefs.mafIatSimC.toInt().toString() else "0")
            }
            var f24Aux by remember {
                mutableStateOf(if (prefs.auxInputSimCode > 0) prefs.auxInputSimCode.toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f24B1s8,
                    onValueChange = { f24B1s8 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S8 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f24B2s8,
                    onValueChange = { f24B2s8 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S8 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f24MaxTq,
                    onValueChange = { f24MaxTq = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("MaxTq %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f24MafIat,
                    onValueChange = { f24MafIat = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("MafIAT °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f24Aux,
                    onValueChange = { f24Aux = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Aux code") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s8SimC = f24B1s8.toFloatOrNull() ?: 0f
                    prefs.catB2s8SimC = f24B2s8.toFloatOrNull() ?: 0f
                    prefs.maxAvailTorqueSimPct = f24MaxTq.toFloatOrNull() ?: 0f
                    prefs.mafIatSimC = f24MafIat.toFloatOrNull() ?: 0f
                    prefs.auxInputSimCode = f24Aux.toIntOrNull() ?: 0
                    status = "Fase 24 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 24") }
            Text(
                listOfNotNull(
                    catB1s8St.label.takeIf { it.isNotBlank() },
                    catB2s8St.label.takeIf { it.isNotBlank() },
                    maxAvailTorqueSt.label.takeIf { it.isNotBlank() },
                    mafIatSt.label.takeIf { it.isNotBlank() },
                    auxInputSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 24 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 25 OBD (017F/80/67/68/6F):", color = Mist)
            val catB1s9St by com.veplayer.app.vehicle.CatalystB1S9Monitor.state.collectAsState()
            val catB2s9St by com.veplayer.app.vehicle.CatalystB2S9Monitor.state.collectAsState()
            val ect2St by com.veplayer.app.vehicle.CoolantEct2Monitor.state.collectAsState()
            val iat2St by com.veplayer.app.vehicle.IatSensor2Monitor.state.collectAsState()
            val turboInletSt by com.veplayer.app.vehicle.TurboInletPressureMonitor.state.collectAsState()
            var f25B1s9 by remember {
                mutableStateOf(if (prefs.catB1s9SimC > 0f) prefs.catB1s9SimC.toInt().toString() else "0")
            }
            var f25B2s9 by remember {
                mutableStateOf(if (prefs.catB2s9SimC > 0f) prefs.catB2s9SimC.toInt().toString() else "0")
            }
            var f25Ect2 by remember {
                mutableStateOf(if (prefs.ect2SimC > 0f) prefs.ect2SimC.toInt().toString() else "0")
            }
            var f25Iat2 by remember {
                mutableStateOf(if (prefs.iat2SimC > 0f) prefs.iat2SimC.toInt().toString() else "0")
            }
            var f25Turbo by remember {
                mutableStateOf(if (prefs.turboInletSimKpa > 0f) prefs.turboInletSimKpa.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f25B1s9,
                    onValueChange = { f25B1s9 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S9 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f25B2s9,
                    onValueChange = { f25B2s9 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S9 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f25Ect2,
                    onValueChange = { f25Ect2 = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("ECT2 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f25Iat2,
                    onValueChange = { f25Iat2 = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("IAT2 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f25Turbo,
                    onValueChange = { f25Turbo = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("TurboIn") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s9SimC = f25B1s9.toFloatOrNull() ?: 0f
                    prefs.catB2s9SimC = f25B2s9.toFloatOrNull() ?: 0f
                    prefs.ect2SimC = f25Ect2.toFloatOrNull() ?: 0f
                    prefs.iat2SimC = f25Iat2.toFloatOrNull() ?: 0f
                    prefs.turboInletSimKpa = f25Turbo.toFloatOrNull() ?: 0f
                    status = "Fase 25 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 25") }
            Text(
                listOfNotNull(
                    catB1s9St.label.takeIf { it.isNotBlank() },
                    catB2s9St.label.takeIf { it.isNotBlank() },
                    ect2St.label.takeIf { it.isNotBlank() },
                    iat2St.label.takeIf { it.isNotBlank() },
                    turboInletSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 25 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 26 OBD (181/82/6B/6A/6C):", color = Mist)
            val catB1s10St by com.veplayer.app.vehicle.CatalystB1S10Monitor.state.collectAsState()
            val catB2s10St by com.veplayer.app.vehicle.CatalystB2S10Monitor.state.collectAsState()
            val egrTempSt by com.veplayer.app.vehicle.EgrTemperatureMonitor.state.collectAsState()
            val dieselIafSt by com.veplayer.app.vehicle.DieselIntakeAirflowMonitor.state.collectAsState()
            val thrActSt by com.veplayer.app.vehicle.ThrottleActuatorMonitor.state.collectAsState()
            var f26B1s10 by remember {
                mutableStateOf(if (prefs.catB1s10SimC > 0f) prefs.catB1s10SimC.toInt().toString() else "0")
            }
            var f26B2s10 by remember {
                mutableStateOf(if (prefs.catB2s10SimC > 0f) prefs.catB2s10SimC.toInt().toString() else "0")
            }
            var f26EgrT by remember {
                mutableStateOf(if (prefs.egrTempSimC > 0f) prefs.egrTempSimC.toInt().toString() else "0")
            }
            var f26Dsl by remember {
                mutableStateOf(if (prefs.dieselIafSimPct > 0f) prefs.dieselIafSimPct.toInt().toString() else "0")
            }
            var f26ThrAct by remember {
                mutableStateOf(if (prefs.thrActSimPct > 0f) prefs.thrActSimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f26B1s10,
                    onValueChange = { f26B1s10 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S10 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f26B2s10,
                    onValueChange = { f26B2s10 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S10 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f26EgrT,
                    onValueChange = { f26EgrT = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EgrT °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f26Dsl,
                    onValueChange = { f26Dsl = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("DslIAF %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f26ThrAct,
                    onValueChange = { f26ThrAct = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("ThrAct %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s10SimC = f26B1s10.toFloatOrNull() ?: 0f
                    prefs.catB2s10SimC = f26B2s10.toFloatOrNull() ?: 0f
                    prefs.egrTempSimC = f26EgrT.toFloatOrNull() ?: 0f
                    prefs.dieselIafSimPct = f26Dsl.toFloatOrNull() ?: 0f
                    prefs.thrActSimPct = f26ThrAct.toFloatOrNull() ?: 0f
                    status = "Fase 26 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 26") }
            Text(
                listOfNotNull(
                    catB1s10St.label.takeIf { it.isNotBlank() },
                    catB2s10St.label.takeIf { it.isNotBlank() },
                    egrTempSt.label.takeIf { it.isNotBlank() },
                    dieselIafSt.label.takeIf { it.isNotBlank() },
                    thrActSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 26 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 27 OBD (183/84/69/6E/6D):", color = Mist)
            val catB1s11St by com.veplayer.app.vehicle.CatalystB1S11Monitor.state.collectAsState()
            val catB2s11St by com.veplayer.app.vehicle.CatalystB2S11Monitor.state.collectAsState()
            val egrActualSt by com.veplayer.app.vehicle.ActualEgrMonitor.state.collectAsState()
            val injectCtrlSt by com.veplayer.app.vehicle.InjectPressureControlMonitor.state.collectAsState()
            val fuelCtrlSt by com.veplayer.app.vehicle.FuelPressureControlMonitor.state.collectAsState()
            var f27B1s11 by remember {
                mutableStateOf(if (prefs.catB1s11SimC > 0f) prefs.catB1s11SimC.toInt().toString() else "0")
            }
            var f27B2s11 by remember {
                mutableStateOf(if (prefs.catB2s11SimC > 0f) prefs.catB2s11SimC.toInt().toString() else "0")
            }
            var f27EgrAct by remember {
                mutableStateOf(if (prefs.egrActualSimPct > 0f) prefs.egrActualSimPct.toInt().toString() else "0")
            }
            var f27Inject by remember {
                mutableStateOf(if (prefs.injectCtrlSimKpa > 0f) prefs.injectCtrlSimKpa.toInt().toString() else "0")
            }
            var f27FuelCtrl by remember {
                mutableStateOf(if (prefs.fuelCtrlSimKpa > 0f) prefs.fuelCtrlSimKpa.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f27B1s11,
                    onValueChange = { f27B1s11 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S11 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f27B2s11,
                    onValueChange = { f27B2s11 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S11 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f27EgrAct,
                    onValueChange = { f27EgrAct = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("EgrAct %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f27Inject,
                    onValueChange = { f27Inject = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Inject kPa") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f27FuelCtrl,
                    onValueChange = { f27FuelCtrl = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("FuelCtrl kPa") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s11SimC = f27B1s11.toFloatOrNull() ?: 0f
                    prefs.catB2s11SimC = f27B2s11.toFloatOrNull() ?: 0f
                    prefs.egrActualSimPct = f27EgrAct.toFloatOrNull() ?: 0f
                    prefs.injectCtrlSimKpa = f27Inject.toFloatOrNull() ?: 0f
                    prefs.fuelCtrlSimKpa = f27FuelCtrl.toFloatOrNull() ?: 0f
                    status = "Fase 27 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 27") }
            Text(
                listOfNotNull(
                    catB1s11St.label.takeIf { it.isNotBlank() },
                    catB2s11St.label.takeIf { it.isNotBlank() },
                    egrActualSt.label.takeIf { it.isNotBlank() },
                    injectCtrlSt.label.takeIf { it.isNotBlank() },
                    fuelCtrlSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 27 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 28 OBD (185/86/08/09):", color = Mist)
            val catB1s12St by com.veplayer.app.vehicle.CatalystB1S12Monitor.state.collectAsState()
            val catB2s12St by com.veplayer.app.vehicle.CatalystB2S12Monitor.state.collectAsState()
            val stftB2St by com.veplayer.app.vehicle.FuelTrimStftB2Monitor.state.collectAsState()
            val ltftB2St by com.veplayer.app.vehicle.FuelTrimLtftB2Monitor.state.collectAsState()
            var f28B1s12 by remember {
                mutableStateOf(if (prefs.catB1s12SimC > 0f) prefs.catB1s12SimC.toInt().toString() else "0")
            }
            var f28B2s12 by remember {
                mutableStateOf(if (prefs.catB2s12SimC > 0f) prefs.catB2s12SimC.toInt().toString() else "0")
            }
            var f28StB2 by remember {
                mutableStateOf(if (prefs.stftB2SimPct != 0f) prefs.stftB2SimPct.toInt().toString() else "0")
            }
            var f28LtB2 by remember {
                mutableStateOf(if (prefs.ltftB2SimPct != 0f) prefs.ltftB2SimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f28B1s12,
                    onValueChange = { f28B1s12 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S12 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f28B2s12,
                    onValueChange = { f28B2s12 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S12 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f28StB2,
                    onValueChange = { f28StB2 = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("STB2 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f28LtB2,
                    onValueChange = { f28LtB2 = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("LTB2 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s12SimC = f28B1s12.toFloatOrNull() ?: 0f
                    prefs.catB2s12SimC = f28B2s12.toFloatOrNull() ?: 0f
                    prefs.stftB2SimPct = f28StB2.toFloatOrNull() ?: 0f
                    prefs.ltftB2SimPct = f28LtB2.toFloatOrNull() ?: 0f
                    status = "Fase 28 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 28") }
            Text(
                listOfNotNull(
                    catB1s12St.label.takeIf { it.isNotBlank() },
                    catB2s12St.label.takeIf { it.isNotBlank() },
                    stftB2St.label.takeIf { it.isNotBlank() },
                    ltftB2St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 28 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 29 OBD (187/88/8B/8D/8E):", color = Mist)
            val catB1s13St by com.veplayer.app.vehicle.CatalystB1S13Monitor.state.collectAsState()
            val catB2s13St by com.veplayer.app.vehicle.CatalystB2S13Monitor.state.collectAsState()
            val dpfTrigSt by com.veplayer.app.vehicle.DpfAftertreatmentMonitor.state.collectAsState()
            val thrGSt by com.veplayer.app.vehicle.ThrottleGMonitor.state.collectAsState()
            val engFrictionSt by com.veplayer.app.vehicle.EngineFrictionTorqueMonitor.state.collectAsState()
            var f29B1s13 by remember {
                mutableStateOf(if (prefs.catB1s13SimC > 0f) prefs.catB1s13SimC.toInt().toString() else "0")
            }
            var f29B2s13 by remember {
                mutableStateOf(if (prefs.catB2s13SimC > 0f) prefs.catB2s13SimC.toInt().toString() else "0")
            }
            var f29Dpf by remember {
                mutableStateOf(if (prefs.dpfTrigSimPct > 0f) prefs.dpfTrigSimPct.toInt().toString() else "0")
            }
            var f29ThrG by remember {
                mutableStateOf(if (prefs.thrGSimPct > 0f) prefs.thrGSimPct.toInt().toString() else "0")
            }
            var f29Frict by remember {
                mutableStateOf(if (prefs.engFrictionSimPct != 0f) prefs.engFrictionSimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f29B1s13,
                    onValueChange = { f29B1s13 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S13 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f29B2s13,
                    onValueChange = { f29B2s13 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S13 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f29Dpf,
                    onValueChange = { f29Dpf = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("DpfTrig %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f29ThrG,
                    onValueChange = { f29ThrG = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("ThrG %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f29Frict,
                    onValueChange = { f29Frict = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("Frict %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s13SimC = f29B1s13.toFloatOrNull() ?: 0f
                    prefs.catB2s13SimC = f29B2s13.toFloatOrNull() ?: 0f
                    prefs.dpfTrigSimPct = f29Dpf.toFloatOrNull() ?: 0f
                    prefs.thrGSimPct = f29ThrG.toFloatOrNull() ?: 0f
                    prefs.engFrictionSimPct = f29Frict.toFloatOrNull() ?: 0f
                    status = "Fase 29 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 29") }
            Text(
                listOfNotNull(
                    catB1s13St.label.takeIf { it.isNotBlank() },
                    catB2s13St.label.takeIf { it.isNotBlank() },
                    dpfTrigSt.label.takeIf { it.isNotBlank() },
                    thrGSt.label.takeIf { it.isNotBlank() },
                    engFrictionSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 29 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 30 OBD (189/8A/8C/8F):", color = Mist)
            val catB1s14St by com.veplayer.app.vehicle.CatalystB1S14Monitor.state.collectAsState()
            val catB2s14St by com.veplayer.app.vehicle.CatalystB2S14Monitor.state.collectAsState()
            val o2LambdaSt by com.veplayer.app.vehicle.O2LambdaB1Monitor.state.collectAsState()
            val pmB1St by com.veplayer.app.vehicle.PmSensorB1Monitor.state.collectAsState()
            val pmB2St by com.veplayer.app.vehicle.PmSensorB2Monitor.state.collectAsState()
            var f30B1s14 by remember {
                mutableStateOf(if (prefs.catB1s14SimC > 0f) prefs.catB1s14SimC.toInt().toString() else "0")
            }
            var f30B2s14 by remember {
                mutableStateOf(if (prefs.catB2s14SimC > 0f) prefs.catB2s14SimC.toInt().toString() else "0")
            }
            var f30O2 by remember {
                mutableStateOf(if (prefs.o2LambdaSim > 0f) prefs.o2LambdaSim.toString() else "0")
            }
            var f30PmB1 by remember {
                mutableStateOf(if (prefs.pmB1SimPct > 0f) prefs.pmB1SimPct.toInt().toString() else "0")
            }
            var f30PmB2 by remember {
                mutableStateOf(if (prefs.pmB2SimPct > 0f) prefs.pmB2SimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f30B1s14,
                    onValueChange = { f30B1s14 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B1S14 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f30B2s14,
                    onValueChange = { f30B2s14 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("B2S14 °C") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f30O2,
                    onValueChange = { f30O2 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2λ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f30PmB1,
                    onValueChange = { f30PmB1 = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("PMB1 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f30PmB2,
                    onValueChange = { f30PmB2 = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("PMB2 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.catB1s14SimC = f30B1s14.toFloatOrNull() ?: 0f
                    prefs.catB2s14SimC = f30B2s14.toFloatOrNull() ?: 0f
                    prefs.o2LambdaSim = f30O2.toFloatOrNull() ?: 0f
                    prefs.pmB1SimPct = f30PmB1.toFloatOrNull() ?: 0f
                    prefs.pmB2SimPct = f30PmB2.toFloatOrNull() ?: 0f
                    status = "Fase 30 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 30") }
            Text(
                listOfNotNull(
                    catB1s14St.label.takeIf { it.isNotBlank() },
                    catB2s14St.label.takeIf { it.isNotBlank() },
                    o2LambdaSt.label.takeIf { it.isNotBlank() },
                    pmB1St.label.takeIf { it.isNotBlank() },
                    pmB2St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 30 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 31 OBD (198/99/9C/94):", color = Mist)
            val egtB1s5St by com.veplayer.app.vehicle.EgtB1S5Monitor.state.collectAsState()
            val egtB2s5St by com.veplayer.app.vehicle.EgtB2S5Monitor.state.collectAsState()
            val o2LmbB1s3St by com.veplayer.app.vehicle.O2LambdaB1S3Monitor.state.collectAsState()
            val o2LmbB2s3St by com.veplayer.app.vehicle.O2LambdaB2S3Monitor.state.collectAsState()
            val noxReqSt by com.veplayer.app.vehicle.NoxReagentQualityMonitor.state.collectAsState()
            var f31EgtB1 by remember {
                mutableStateOf(if (prefs.egtB1s5SimC > 0f) prefs.egtB1s5SimC.toInt().toString() else "0")
            }
            var f31EgtB2 by remember {
                mutableStateOf(if (prefs.egtB2s5SimC > 0f) prefs.egtB2s5SimC.toInt().toString() else "0")
            }
            var f31O2B1s3 by remember {
                mutableStateOf(if (prefs.o2LambdaB1s3Sim > 0f) prefs.o2LambdaB1s3Sim.toString() else "0")
            }
            var f31O2B2s3 by remember {
                mutableStateOf(if (prefs.o2LambdaB2s3Sim > 0f) prefs.o2LambdaB2s3Sim.toString() else "0")
            }
            var f31Nox by remember {
                mutableStateOf(if (prefs.noxReqSimH > 0f) prefs.noxReqSimH.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f31EgtB1,
                    onValueChange = { f31EgtB1 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB1S5") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f31EgtB2,
                    onValueChange = { f31EgtB2 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB2S5") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f31O2B1s3,
                    onValueChange = { f31O2B1s3 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2λ3") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f31O2B2s3,
                    onValueChange = { f31O2B2s3 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2λ23") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f31Nox,
                    onValueChange = { f31Nox = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("NOxReq h") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.egtB1s5SimC = f31EgtB1.toFloatOrNull() ?: 0f
                    prefs.egtB2s5SimC = f31EgtB2.toFloatOrNull() ?: 0f
                    prefs.o2LambdaB1s3Sim = f31O2B1s3.toFloatOrNull() ?: 0f
                    prefs.o2LambdaB2s3Sim = f31O2B2s3.toFloatOrNull() ?: 0f
                    prefs.noxReqSimH = f31Nox.toFloatOrNull() ?: 0f
                    status = "Fase 31 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 31") }
            Text(
                listOfNotNull(
                    egtB1s5St.label.takeIf { it.isNotBlank() },
                    egtB2s5St.label.takeIf { it.isNotBlank() },
                    o2LmbB1s3St.label.takeIf { it.isNotBlank() },
                    o2LmbB2s3St.label.takeIf { it.isNotBlank() },
                    noxReqSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 31 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 32 OBD (198/99/9C/9B):", color = Mist)
            val egtB1s6St by com.veplayer.app.vehicle.EgtB1S6Monitor.state.collectAsState()
            val egtB2s6St by com.veplayer.app.vehicle.EgtB2S6Monitor.state.collectAsState()
            val o2LmbB1s4St by com.veplayer.app.vehicle.O2LambdaB1S4Monitor.state.collectAsState()
            val o2LmbB2s4St by com.veplayer.app.vehicle.O2LambdaB2S4Monitor.state.collectAsState()
            val defFluidSt by com.veplayer.app.vehicle.DefFluidMonitor.state.collectAsState()
            var f32EgtB1 by remember {
                mutableStateOf(if (prefs.egtB1s6SimC > 0f) prefs.egtB1s6SimC.toInt().toString() else "0")
            }
            var f32EgtB2 by remember {
                mutableStateOf(if (prefs.egtB2s6SimC > 0f) prefs.egtB2s6SimC.toInt().toString() else "0")
            }
            var f32O2B1s4 by remember {
                mutableStateOf(if (prefs.o2LambdaB1s4Sim > 0f) prefs.o2LambdaB1s4Sim.toString() else "0")
            }
            var f32O2B2s4 by remember {
                mutableStateOf(if (prefs.o2LambdaB2s4Sim > 0f) prefs.o2LambdaB2s4Sim.toString() else "0")
            }
            var f32Def by remember {
                mutableStateOf(if (prefs.defFluidSimPct > 0f) prefs.defFluidSimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f32EgtB1,
                    onValueChange = { f32EgtB1 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB1S6") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f32EgtB2,
                    onValueChange = { f32EgtB2 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB2S6") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f32O2B1s4,
                    onValueChange = { f32O2B1s4 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2λ4") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f32O2B2s4,
                    onValueChange = { f32O2B2s4 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2λ24") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f32Def,
                    onValueChange = { f32Def = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("DEF %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.egtB1s6SimC = f32EgtB1.toFloatOrNull() ?: 0f
                    prefs.egtB2s6SimC = f32EgtB2.toFloatOrNull() ?: 0f
                    prefs.o2LambdaB1s4Sim = f32O2B1s4.toFloatOrNull() ?: 0f
                    prefs.o2LambdaB2s4Sim = f32O2B2s4.toFloatOrNull() ?: 0f
                    prefs.defFluidSimPct = f32Def.toFloatOrNull() ?: 0f
                    status = "Fase 32 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 32") }
            Text(
                listOfNotNull(
                    egtB1s6St.label.takeIf { it.isNotBlank() },
                    egtB2s6St.label.takeIf { it.isNotBlank() },
                    o2LmbB1s4St.label.takeIf { it.isNotBlank() },
                    o2LmbB2s4St.label.takeIf { it.isNotBlank() },
                    defFluidSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 32 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 33 OBD (198/99/9C):", color = Mist)
            val egtB1s7St by com.veplayer.app.vehicle.EgtB1S7Monitor.state.collectAsState()
            val egtB2s7St by com.veplayer.app.vehicle.EgtB2S7Monitor.state.collectAsState()
            val egtB1s8St by com.veplayer.app.vehicle.EgtB1S8Monitor.state.collectAsState()
            val egtB2s8St by com.veplayer.app.vehicle.EgtB2S8Monitor.state.collectAsState()
            val o2ConcB1s3St by com.veplayer.app.vehicle.O2ConcB1S3Monitor.state.collectAsState()
            var f33EgtB1 by remember {
                mutableStateOf(if (prefs.egtB1s7SimC > 0f) prefs.egtB1s7SimC.toInt().toString() else "0")
            }
            var f33EgtB2 by remember {
                mutableStateOf(if (prefs.egtB2s7SimC > 0f) prefs.egtB2s7SimC.toInt().toString() else "0")
            }
            var f33EgtB1s8 by remember {
                mutableStateOf(if (prefs.egtB1s8SimC > 0f) prefs.egtB1s8SimC.toInt().toString() else "0")
            }
            var f33EgtB2s8 by remember {
                mutableStateOf(if (prefs.egtB2s8SimC > 0f) prefs.egtB2s8SimC.toInt().toString() else "0")
            }
            var f33O2Conc by remember {
                mutableStateOf(if (prefs.o2ConcB1s3Sim > 0f) prefs.o2ConcB1s3Sim.toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f33EgtB1,
                    onValueChange = { f33EgtB1 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB1S7") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f33EgtB2,
                    onValueChange = { f33EgtB2 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB2S7") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f33EgtB1s8,
                    onValueChange = { f33EgtB1s8 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB1S8") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f33EgtB2s8,
                    onValueChange = { f33EgtB2s8 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGTB2S8") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f33O2Conc,
                    onValueChange = { f33O2Conc = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2C3 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.egtB1s7SimC = f33EgtB1.toFloatOrNull() ?: 0f
                    prefs.egtB2s7SimC = f33EgtB2.toFloatOrNull() ?: 0f
                    prefs.egtB1s8SimC = f33EgtB1s8.toFloatOrNull() ?: 0f
                    prefs.egtB2s8SimC = f33EgtB2s8.toFloatOrNull() ?: 0f
                    prefs.o2ConcB1s3Sim = f33O2Conc.toFloatOrNull() ?: 0f
                    status = "Fase 33 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 33") }
            Text(
                listOfNotNull(
                    egtB1s7St.label.takeIf { it.isNotBlank() },
                    egtB2s7St.label.takeIf { it.isNotBlank() },
                    egtB1s8St.label.takeIf { it.isNotBlank() },
                    egtB2s8St.label.takeIf { it.isNotBlank() },
                    o2ConcB1s3St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 33 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 34 OBD (9C/A5/A1):", color = Mist)
            val o2ConcB1s4St by com.veplayer.app.vehicle.O2ConcB1S4Monitor.state.collectAsState()
            val o2ConcB2s3St by com.veplayer.app.vehicle.O2ConcB2S3Monitor.state.collectAsState()
            val o2ConcB2s4St by com.veplayer.app.vehicle.O2ConcB2S4Monitor.state.collectAsState()
            val defDoseSt by com.veplayer.app.vehicle.DefDosingCmdMonitor.state.collectAsState()
            val noxCorrB1s1St by com.veplayer.app.vehicle.NoxCorrectedB1S1Monitor.state.collectAsState()
            var f34O2C4 by remember {
                mutableStateOf(if (prefs.o2ConcB1s4Sim > 0f) prefs.o2ConcB1s4Sim.toString() else "0")
            }
            var f34O2C23 by remember {
                mutableStateOf(if (prefs.o2ConcB2s3Sim > 0f) prefs.o2ConcB2s3Sim.toString() else "0")
            }
            var f34O2C24 by remember {
                mutableStateOf(if (prefs.o2ConcB2s4Sim > 0f) prefs.o2ConcB2s4Sim.toString() else "0")
            }
            var f34DefDose by remember {
                mutableStateOf(if (prefs.defDoseSimPct > 0f) prefs.defDoseSimPct.toInt().toString() else "0")
            }
            var f34NoxCorr by remember {
                mutableStateOf(if (prefs.noxCorrB1s1Sim > 0f) prefs.noxCorrB1s1Sim.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f34O2C4,
                    onValueChange = { f34O2C4 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2C4 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f34O2C23,
                    onValueChange = { f34O2C23 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2C23 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f34O2C24,
                    onValueChange = { f34O2C24 = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("O2C24 %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f34DefDose,
                    onValueChange = { f34DefDose = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("DEFDose %") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f34NoxCorr,
                    onValueChange = { f34NoxCorr = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOxC1 ppm") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.o2ConcB1s4Sim = f34O2C4.toFloatOrNull() ?: 0f
                    prefs.o2ConcB2s3Sim = f34O2C23.toFloatOrNull() ?: 0f
                    prefs.o2ConcB2s4Sim = f34O2C24.toFloatOrNull() ?: 0f
                    prefs.defDoseSimPct = f34DefDose.toFloatOrNull() ?: 0f
                    prefs.noxCorrB1s1Sim = f34NoxCorr.toFloatOrNull() ?: 0f
                    status = "Fase 34 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 34") }
            Text(
                listOfNotNull(
                    o2ConcB1s4St.label.takeIf { it.isNotBlank() },
                    o2ConcB2s3St.label.takeIf { it.isNotBlank() },
                    o2ConcB2s4St.label.takeIf { it.isNotBlank() },
                    defDoseSt.label.takeIf { it.isNotBlank() },
                    noxCorrB1s1St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 34 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 35 OBD (A1/A7):", color = Mist)
            val noxCorrB1s2St by com.veplayer.app.vehicle.NoxCorrectedB1S2Monitor.state.collectAsState()
            val noxCorrB2s1St by com.veplayer.app.vehicle.NoxCorrectedB2S1Monitor.state.collectAsState()
            val noxCorrB2s2St by com.veplayer.app.vehicle.NoxCorrectedB2S2Monitor.state.collectAsState()
            val noxConcS3St by com.veplayer.app.vehicle.NoxConcS3Monitor.state.collectAsState()
            val noxConcS4St by com.veplayer.app.vehicle.NoxConcS4Monitor.state.collectAsState()
            var f35NoxC2 by remember {
                mutableStateOf(if (prefs.noxCorrB1s2Sim > 0f) prefs.noxCorrB1s2Sim.toInt().toString() else "0")
            }
            var f35NoxC21 by remember {
                mutableStateOf(if (prefs.noxCorrB2s1Sim > 0f) prefs.noxCorrB2s1Sim.toInt().toString() else "0")
            }
            var f35NoxC22 by remember {
                mutableStateOf(if (prefs.noxCorrB2s2Sim > 0f) prefs.noxCorrB2s2Sim.toInt().toString() else "0")
            }
            var f35Nox3 by remember {
                mutableStateOf(if (prefs.noxConcS3Sim > 0f) prefs.noxConcS3Sim.toInt().toString() else "0")
            }
            var f35Nox4 by remember {
                mutableStateOf(if (prefs.noxConcS4Sim > 0f) prefs.noxConcS4Sim.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f35NoxC2,
                    onValueChange = { f35NoxC2 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOxC2") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f35NoxC21,
                    onValueChange = { f35NoxC21 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOxC21") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f35NoxC22,
                    onValueChange = { f35NoxC22 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOxC22") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f35Nox3,
                    onValueChange = { f35Nox3 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOx3") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f35Nox4,
                    onValueChange = { f35Nox4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOx4") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.noxCorrB1s2Sim = f35NoxC2.toFloatOrNull() ?: 0f
                    prefs.noxCorrB2s1Sim = f35NoxC21.toFloatOrNull() ?: 0f
                    prefs.noxCorrB2s2Sim = f35NoxC22.toFloatOrNull() ?: 0f
                    prefs.noxConcS3Sim = f35Nox3.toFloatOrNull() ?: 0f
                    prefs.noxConcS4Sim = f35Nox4.toFloatOrNull() ?: 0f
                    status = "Fase 35 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 35") }
            Text(
                listOfNotNull(
                    noxCorrB1s2St.label.takeIf { it.isNotBlank() },
                    noxCorrB2s1St.label.takeIf { it.isNotBlank() },
                    noxCorrB2s2St.label.takeIf { it.isNotBlank() },
                    noxConcS3St.label.takeIf { it.isNotBlank() },
                    noxConcS4St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 35 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 36 OBD (A8/A2/A3/A4):", color = Mist)
            val noxCorrS3St by com.veplayer.app.vehicle.NoxCorrectedS3Monitor.state.collectAsState()
            val noxCorrS4St by com.veplayer.app.vehicle.NoxCorrectedS4Monitor.state.collectAsState()
            val cylFuelSt by com.veplayer.app.vehicle.CylinderFuelRateMonitor.state.collectAsState()
            val evapSysVaporSt by com.veplayer.app.vehicle.EvapSysVaporMonitor.state.collectAsState()
            val transGearSt by com.veplayer.app.vehicle.TransGearRatioMonitor.state.collectAsState()
            var f36NoxC3 by remember {
                mutableStateOf(if (prefs.noxCorrS3Sim > 0f) prefs.noxCorrS3Sim.toInt().toString() else "0")
            }
            var f36NoxC4 by remember {
                mutableStateOf(if (prefs.noxCorrS4Sim > 0f) prefs.noxCorrS4Sim.toInt().toString() else "0")
            }
            var f36CylFuel by remember {
                mutableStateOf(if (prefs.cylFuelSimMg > 0f) prefs.cylFuelSimMg.toInt().toString() else "0")
            }
            var f36EvapVp by remember {
                mutableStateOf(if (prefs.evapSysVaporSimPa != 0f) prefs.evapSysVaporSimPa.toInt().toString() else "0")
            }
            var f36Gear by remember {
                mutableStateOf(if (prefs.transGearSimRatio > 0f) prefs.transGearSimRatio.toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f36NoxC3,
                    onValueChange = { f36NoxC3 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOxC3") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f36NoxC4,
                    onValueChange = { f36NoxC4 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOxC4") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f36CylFuel,
                    onValueChange = { f36CylFuel = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("CylFuel") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f36EvapVp,
                    onValueChange = { f36EvapVp = it.filter { c -> c.isDigit() || c == '-' }.take(6) },
                    label = { Text("EvapVP") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f36Gear,
                    onValueChange = { f36Gear = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("Gear") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.noxCorrS3Sim = f36NoxC3.toFloatOrNull() ?: 0f
                    prefs.noxCorrS4Sim = f36NoxC4.toFloatOrNull() ?: 0f
                    prefs.cylFuelSimMg = f36CylFuel.toFloatOrNull() ?: 0f
                    prefs.evapSysVaporSimPa = f36EvapVp.toFloatOrNull() ?: 0f
                    prefs.transGearSimRatio = f36Gear.toFloatOrNull() ?: 0f
                    status = "Fase 36 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 36") }
            Text(
                listOfNotNull(
                    noxCorrS3St.label.takeIf { it.isNotBlank() },
                    noxCorrS4St.label.takeIf { it.isNotBlank() },
                    cylFuelSt.label.takeIf { it.isNotBlank() },
                    evapSysVaporSt.label.takeIf { it.isNotBlank() },
                    transGearSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 36 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 37 OBD (A6/A9/C5/C7):", color = Mist)
            val obdOdoSt by com.veplayer.app.vehicle.ObdOdometerMonitor.state.collectAsState()
            val absDisableSt by com.veplayer.app.vehicle.AbsDisableMonitor.state.collectAsState()
            val fuelPressASt by com.veplayer.app.vehicle.FuelPressAMonitor.state.collectAsState()
            val fuelPressBSt by com.veplayer.app.vehicle.FuelPressBMonitor.state.collectAsState()
            val reflashDistSt by com.veplayer.app.vehicle.ReflashDistanceMonitor.state.collectAsState()
            var f37Odo by remember {
                mutableStateOf(if (prefs.obdOdoSimKm > 0f) prefs.obdOdoSimKm.toInt().toString() else "0")
            }
            var f37Abs by remember { mutableStateOf(if (prefs.absDisableSim) "1" else "0") }
            var f37Fpa by remember {
                mutableStateOf(if (prefs.fuelPressASimKpa > 0f) prefs.fuelPressASimKpa.toInt().toString() else "0")
            }
            var f37Fpb by remember {
                mutableStateOf(if (prefs.fuelPressBSimKpa > 0f) prefs.fuelPressBSimKpa.toInt().toString() else "0")
            }
            var f37Reflash by remember {
                mutableStateOf(if (prefs.reflashDistSimKm > 0f) prefs.reflashDistSimKm.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f37Odo,
                    onValueChange = { f37Odo = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Odo") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f37Abs,
                    onValueChange = { f37Abs = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("ABSoff") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f37Fpa,
                    onValueChange = { f37Fpa = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("FPa") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f37Fpb,
                    onValueChange = { f37Fpb = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("FPb") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f37Reflash,
                    onValueChange = { f37Reflash = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Reflash") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.obdOdoSimKm = f37Odo.toFloatOrNull() ?: 0f
                    prefs.absDisableSim = f37Abs == "1"
                    prefs.fuelPressASimKpa = f37Fpa.toFloatOrNull() ?: 0f
                    prefs.fuelPressBSimKpa = f37Fpb.toFloatOrNull() ?: 0f
                    prefs.reflashDistSimKm = f37Reflash.toFloatOrNull() ?: 0f
                    status = "Fase 37 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 37") }
            Text(
                listOfNotNull(
                    obdOdoSt.label.takeIf { it.isNotBlank() },
                    absDisableSt.label.takeIf { it.isNotBlank() },
                    fuelPressASt.label.takeIf { it.isNotBlank() },
                    fuelPressBSt.label.takeIf { it.isNotBlank() },
                    reflashDistSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 37 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 38 OBD (C3/C4/C8):", color = Mist)
            val fuelLvlASt by com.veplayer.app.vehicle.FuelLevelInputAMonitor.state.collectAsState()
            val fuelLvlBSt by com.veplayer.app.vehicle.FuelLevelInputBMonitor.state.collectAsState()
            val epcsTimeSt by com.veplayer.app.vehicle.EpcsDiagTimeMonitor.state.collectAsState()
            val epcsCountSt by com.veplayer.app.vehicle.EpcsDiagCountMonitor.state.collectAsState()
            val noxPcdLampSt by com.veplayer.app.vehicle.NoxPcdLampMonitor.state.collectAsState()
            var f38FuelA by remember {
                mutableStateOf(if (prefs.fuelLvlASimPct > 0f) prefs.fuelLvlASimPct.toInt().toString() else "0")
            }
            var f38FuelB by remember {
                mutableStateOf(if (prefs.fuelLvlBSimPct > 0f) prefs.fuelLvlBSimPct.toInt().toString() else "0")
            }
            var f38EpcsT by remember {
                mutableStateOf(if (prefs.epcsTimeSimSec > 0f) prefs.epcsTimeSimSec.toInt().toString() else "0")
            }
            var f38EpcsN by remember {
                mutableStateOf(if (prefs.epcsCountSim > 0f) prefs.epcsCountSim.toInt().toString() else "0")
            }
            var f38Lamp by remember { mutableStateOf(if (prefs.noxPcdLampSim) "1" else "0") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f38FuelA,
                    onValueChange = { f38FuelA = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("FuelA") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f38FuelB,
                    onValueChange = { f38FuelB = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("FuelB") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f38EpcsT,
                    onValueChange = { f38EpcsT = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("EPCS") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f38EpcsN,
                    onValueChange = { f38EpcsN = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("EPCSn") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f38Lamp,
                    onValueChange = { f38Lamp = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("NCD/PCD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.fuelLvlASimPct = f38FuelA.toFloatOrNull() ?: 0f
                    prefs.fuelLvlBSimPct = f38FuelB.toFloatOrNull() ?: 0f
                    prefs.epcsTimeSimSec = f38EpcsT.toFloatOrNull() ?: 0f
                    prefs.epcsCountSim = f38EpcsN.toFloatOrNull() ?: 0f
                    prefs.noxPcdLampSim = f38Lamp == "1"
                    status = "Fase 38 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 38") }
            Text(
                listOfNotNull(
                    fuelLvlASt.label.takeIf { it.isNotBlank() },
                    fuelLvlBSt.label.takeIf { it.isNotBlank() },
                    epcsTimeSt.label.takeIf { it.isNotBlank() },
                    epcsCountSt.label.takeIf { it.isNotBlank() },
                    noxPcdLampSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 38 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 39 OBD (C6):", color = Mist)
            val induceWarnSt by com.veplayer.app.vehicle.ParticulateInduceWarnMonitor.state.collectAsState()
            val induceAlertSt by com.veplayer.app.vehicle.ParticulateInduceAlertMonitor.state.collectAsState()
            val dpfRemovalSt by com.veplayer.app.vehicle.DpfRemovalCounterMonitor.state.collectAsState()
            val reagentFailSt by com.veplayer.app.vehicle.ReagentInjectionFailCounterMonitor.state.collectAsState()
            val particulateMalfSt by com.veplayer.app.vehicle.ParticulateMonitorMalfunctionCounterMonitor.state.collectAsState()
            var f39Induce by remember {
                mutableStateOf(if (prefs.particulateInduceSimStatus > 0f) prefs.particulateInduceSimStatus.toInt().toString() else "0")
            }
            var f39DpfRem by remember {
                mutableStateOf(if (prefs.dpfRemovalSimCount > 0f) prefs.dpfRemovalSimCount.toInt().toString() else "0")
            }
            var f39Reag by remember {
                mutableStateOf(if (prefs.reagentFailSimCount > 0f) prefs.reagentFailSimCount.toInt().toString() else "0")
            }
            var f39Malf by remember {
                mutableStateOf(if (prefs.particulateMalfSimCount > 0f) prefs.particulateMalfSimCount.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f39Induce,
                    onValueChange = { f39Induce = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Induce") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f39DpfRem,
                    onValueChange = { f39DpfRem = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("DpfRem") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f39Reag,
                    onValueChange = { f39Reag = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("ReagFail") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = f39Malf,
                onValueChange = { f39Malf = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("PCMmal") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.particulateInduceSimStatus = f39Induce.toFloatOrNull() ?: 0f
                    prefs.dpfRemovalSimCount = f39DpfRem.toFloatOrNull() ?: 0f
                    prefs.reagentFailSimCount = f39Reag.toFloatOrNull() ?: 0f
                    prefs.particulateMalfSimCount = f39Malf.toFloatOrNull() ?: 0f
                    status = "Fase 39 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 39") }
            Text(
                listOfNotNull(
                    induceWarnSt.label.takeIf { it.isNotBlank() },
                    induceAlertSt.label.takeIf { it.isNotBlank() },
                    dpfRemovalSt.label.takeIf { it.isNotBlank() },
                    reagentFailSt.label.takeIf { it.isNotBlank() },
                    particulateMalfSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 39 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 40 OBD (9D/9E/9F):", color = Mist)
            val fuelGpsSt by com.veplayer.app.vehicle.EngineFuelRateGpsMonitor.state.collectAsState()
            val exhFlowSt by com.veplayer.app.vehicle.EngineExhaustFlowMonitor.state.collectAsState()
            val fsu1St by com.veplayer.app.vehicle.FuelSysUsePct1Monitor.state.collectAsState()
            val fsu2St by com.veplayer.app.vehicle.FuelSysUsePct2Monitor.state.collectAsState()
            val fsu3St by com.veplayer.app.vehicle.FuelSysUsePct3Monitor.state.collectAsState()
            var f40FuelGps by remember {
                mutableStateOf(if (prefs.engineFuelRateGpsSim > 0f) prefs.engineFuelRateGpsSim.toString() else "0")
            }
            var f40Exh by remember {
                mutableStateOf(if (prefs.exhaustFlowSimKgh > 0f) prefs.exhaustFlowSimKgh.toInt().toString() else "0")
            }
            var f40Fsu1 by remember {
                mutableStateOf(if (prefs.fuelSysUse1SimPct > 0f) prefs.fuelSysUse1SimPct.toInt().toString() else "0")
            }
            var f40Fsu2 by remember {
                mutableStateOf(if (prefs.fuelSysUse2SimPct > 0f) prefs.fuelSysUse2SimPct.toInt().toString() else "0")
            }
            var f40Fsu3 by remember {
                mutableStateOf(if (prefs.fuelSysUse3SimPct > 0f) prefs.fuelSysUse3SimPct.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f40FuelGps,
                    onValueChange = { f40FuelGps = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("FuelGPS") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f40Exh,
                    onValueChange = { f40Exh = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("ExhFlow") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f40Fsu1,
                    onValueChange = { f40Fsu1 = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("FSu1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f40Fsu2,
                    onValueChange = { f40Fsu2 = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("FSu2") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f40Fsu3,
                    onValueChange = { f40Fsu3 = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("FSu3") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.engineFuelRateGpsSim = f40FuelGps.toFloatOrNull() ?: 0f
                    prefs.exhaustFlowSimKgh = f40Exh.toFloatOrNull() ?: 0f
                    prefs.fuelSysUse1SimPct = f40Fsu1.toFloatOrNull() ?: 0f
                    prefs.fuelSysUse2SimPct = f40Fsu2.toFloatOrNull() ?: 0f
                    prefs.fuelSysUse3SimPct = f40Fsu3.toFloatOrNull() ?: 0f
                    status = "Fase 40 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 40") }
            Text(
                listOfNotNull(
                    fuelGpsSt.label.takeIf { it.isNotBlank() },
                    exhFlowSt.label.takeIf { it.isNotBlank() },
                    fsu1St.label.takeIf { it.isNotBlank() },
                    fsu2St.label.takeIf { it.isNotBlank() },
                    fsu3St.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 40 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 41 OBD (90/91/92/93/9A):", color = Mist)
            val wwhCmiSt by com.veplayer.app.vehicle.WwhObdContinuousMiMonitor.state.collectAsState()
            val wwhB1St by com.veplayer.app.vehicle.WwhObdEcuB1HoursMonitor.state.collectAsState()
            val fscSt by com.veplayer.app.vehicle.FuelSysCtlClosedMonitor.state.collectAsState()
            val wwhCumSt by com.veplayer.app.vehicle.WwhObdCumulativeMiMonitor.state.collectAsState()
            val hevVSt by com.veplayer.app.vehicle.HybridEvBattVoltageMonitor.state.collectAsState()
            var f41WwhCmi by remember {
                mutableStateOf(if (prefs.wwhContMiSimH > 0f) prefs.wwhContMiSimH.toInt().toString() else "0")
            }
            var f41WwhB1 by remember {
                mutableStateOf(if (prefs.wwhEcuB1SimH > 0f) prefs.wwhEcuB1SimH.toInt().toString() else "0")
            }
            var f41Fsc by remember {
                mutableStateOf(if (prefs.fuelSysCtlSimCount > 0f) prefs.fuelSysCtlSimCount.toInt().toString() else "0")
            }
            var f41WwhCum by remember {
                mutableStateOf(if (prefs.wwhCumMiSimH > 0f) prefs.wwhCumMiSimH.toInt().toString() else "0")
            }
            var f41HevV by remember {
                mutableStateOf(if (prefs.hevVoltSimV > 0f) prefs.hevVoltSimV.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f41WwhCmi,
                    onValueChange = { f41WwhCmi = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("WwhCMI") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f41WwhB1,
                    onValueChange = { f41WwhB1 = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("WwhB1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f41Fsc,
                    onValueChange = { f41Fsc = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("FSCctl") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f41WwhCum,
                    onValueChange = { f41WwhCum = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("WwhCum") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f41HevV,
                    onValueChange = { f41HevV = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("HevV") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.wwhContMiSimH = f41WwhCmi.toFloatOrNull() ?: 0f
                    prefs.wwhEcuB1SimH = f41WwhB1.toFloatOrNull() ?: 0f
                    prefs.fuelSysCtlSimCount = f41Fsc.toFloatOrNull() ?: 0f
                    prefs.wwhCumMiSimH = f41WwhCum.toFloatOrNull() ?: 0f
                    prefs.hevVoltSimV = f41HevV.toFloatOrNull() ?: 0f
                    status = "Fase 41 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 41") }
            Text(
                listOfNotNull(
                    wwhCmiSt.label.takeIf { it.isNotBlank() },
                    wwhB1St.label.takeIf { it.isNotBlank() },
                    fscSt.label.takeIf { it.isNotBlank() },
                    wwhCumSt.label.takeIf { it.isNotBlank() },
                    hevVSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 41 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 42 OBD (0194 inducement):", color = Mist)
            val noxWarnSt by com.veplayer.app.vehicle.NoxWarnActiveMonitor.state.collectAsState()
            val noxIndL1St by com.veplayer.app.vehicle.NoxInduceLevel1Monitor.state.collectAsState()
            val noxIndL2St by com.veplayer.app.vehicle.NoxInduceLevel2Monitor.state.collectAsState()
            val noxEgrSt by com.veplayer.app.vehicle.NoxEgrCounterMonitor.state.collectAsState()
            val noxMalSt by com.veplayer.app.vehicle.NoxMonitorMalfunctionMonitor.state.collectAsState()
            var f42NoxWarn by remember { mutableStateOf(prefs.noxWarnSim) }
            var f42IndL1 by remember { mutableStateOf(if (prefs.noxIndL1Sim > 0) prefs.noxIndL1Sim.toString() else "0") }
            var f42IndL2 by remember { mutableStateOf(if (prefs.noxIndL2Sim > 0) prefs.noxIndL2Sim.toString() else "0") }
            var f42Egr by remember { mutableStateOf(if (prefs.noxEgrSimH > 0f) prefs.noxEgrSimH.toInt().toString() else "0") }
            var f42Mal by remember { mutableStateOf(if (prefs.noxMalSimH > 0f) prefs.noxMalSimH.toInt().toString() else "0") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { f42NoxWarn = !f42NoxWarn }) {
                    Text(if (f42NoxWarn) "NOxWarn ON" else "NOxWarn OFF")
                }
                OutlinedTextField(
                    value = f42IndL1,
                    onValueChange = { f42IndL1 = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("IndL1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f42IndL2,
                    onValueChange = { f42IndL2 = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("IndL2") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f42Egr,
                    onValueChange = { f42Egr = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("EGRcnt") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f42Mal,
                    onValueChange = { f42Mal = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("NOxMal") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.noxWarnSim = f42NoxWarn
                    prefs.noxIndL1Sim = f42IndL1.toIntOrNull() ?: 0
                    prefs.noxIndL2Sim = f42IndL2.toIntOrNull() ?: 0
                    prefs.noxEgrSimH = f42Egr.toFloatOrNull() ?: 0f
                    prefs.noxMalSimH = f42Mal.toFloatOrNull() ?: 0f
                    status = "Fase 42 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 42") }
            Text(
                listOfNotNull(
                    noxWarnSt.label.takeIf { it.isNotBlank() },
                    noxIndL1St.label.takeIf { it.isNotBlank() },
                    noxIndL2St.label.takeIf { it.isNotBlank() },
                    noxEgrSt.label.takeIf { it.isNotBlank() },
                    noxMalSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 42 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 43 OBD (01B2–B7 HVESS):", color = Mist)
            val hvSohSt by com.veplayer.app.vehicle.HvBattSohMonitor.state.collectAsState()
            val hvessTempSt by com.veplayer.app.vehicle.HvessTempMonitor.state.collectAsState()
            val hvessCurSt by com.veplayer.app.vehicle.HvessCurrentMonitor.state.collectAsState()
            val hvessVoltSt by com.veplayer.app.vehicle.HvessPackVoltageMonitor.state.collectAsState()
            val hvCellMaxSt by com.veplayer.app.vehicle.HvCellMaxTempMonitor.state.collectAsState()
            var f43Soh by remember {
                mutableStateOf(if (prefs.hvSohSimPct > 0f) prefs.hvSohSimPct.toInt().toString() else "0")
            }
            var f43Temp by remember {
                mutableStateOf(if (prefs.hvessTempSimC > 0f) prefs.hvessTempSimC.toInt().toString() else "0")
            }
            var f43Cur by remember {
                mutableStateOf(if (prefs.hvessCurSimA != 0f) prefs.hvessCurSimA.toInt().toString() else "0")
            }
            var f43Volt by remember {
                mutableStateOf(if (prefs.hvessVoltSimV > 0f) prefs.hvessVoltSimV.toInt().toString() else "0")
            }
            var f43Max by remember {
                mutableStateOf(if (prefs.hvCellMaxSimC > 0f) prefs.hvCellMaxSimC.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f43Soh,
                    onValueChange = { f43Soh = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("HySOH") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f43Temp,
                    onValueChange = { f43Temp = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("HvTemp") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f43Cur,
                    onValueChange = { f43Cur = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("HvCur") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f43Volt,
                    onValueChange = { f43Volt = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("HvV6") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f43Max,
                    onValueChange = { f43Max = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("HvMax") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.hvSohSimPct = f43Soh.toFloatOrNull() ?: 0f
                    prefs.hvessTempSimC = f43Temp.toFloatOrNull() ?: 0f
                    prefs.hvessCurSimA = f43Cur.toFloatOrNull() ?: 0f
                    prefs.hvessVoltSimV = f43Volt.toFloatOrNull() ?: 0f
                    prefs.hvCellMaxSimC = f43Max.toFloatOrNull() ?: 0f
                    status = "Fase 43 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 43") }
            Text(
                listOfNotNull(
                    hvSohSt.label.takeIf { it.isNotBlank() },
                    hvessTempSt.label.takeIf { it.isNotBlank() },
                    hvessCurSt.label.takeIf { it.isNotBlank() },
                    hvessVoltSt.label.takeIf { it.isNotBlank() },
                    hvCellMaxSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 43 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 44 OBD (01B8–BA HVESS):", color = Mist)
            val hvBalSt by com.veplayer.app.vehicle.HvBalHoursMonitor.state.collectAsState()
            val hvCellMinVSt by com.veplayer.app.vehicle.HvCellMinVoltMonitor.state.collectAsState()
            val hvCellMaxVSt by com.veplayer.app.vehicle.HvCellMaxVoltMonitor.state.collectAsState()
            val hvPwrSt by com.veplayer.app.vehicle.HvPwrAvailMonitor.state.collectAsState()
            val hvChgSt by com.veplayer.app.vehicle.HvChgLimitMonitor.state.collectAsState()
            var f44Bal by remember {
                mutableStateOf(if (prefs.hvBalSimH > 0f) prefs.hvBalSimH.toInt().toString() else "0")
            }
            var f44MinV by remember {
                mutableStateOf(if (prefs.hvCellMinVSimV > 0f) prefs.hvCellMinVSimV.toString() else "0")
            }
            var f44MaxV by remember {
                mutableStateOf(if (prefs.hvCellMaxVSimV > 0f) prefs.hvCellMaxVSimV.toString() else "0")
            }
            var f44Pwr by remember {
                mutableStateOf(if (prefs.hvPwrSimPct > 0f) prefs.hvPwrSimPct.toInt().toString() else "0")
            }
            var f44Chg by remember {
                mutableStateOf(if (prefs.hvChgSimA > 0f) prefs.hvChgSimA.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f44Bal,
                    onValueChange = { f44Bal = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("HvBal") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f44MinV,
                    onValueChange = { f44MinV = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("HvMinV") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f44MaxV,
                    onValueChange = { f44MaxV = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text("HvMaxV") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f44Pwr,
                    onValueChange = { f44Pwr = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("HvPwr") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f44Chg,
                    onValueChange = { f44Chg = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("HvChg") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.hvBalSimH = f44Bal.toFloatOrNull() ?: 0f
                    prefs.hvCellMinVSimV = f44MinV.toFloatOrNull() ?: 0f
                    prefs.hvCellMaxVSimV = f44MaxV.toFloatOrNull() ?: 0f
                    prefs.hvPwrSimPct = f44Pwr.toFloatOrNull() ?: 0f
                    prefs.hvChgSimA = f44Chg.toFloatOrNull() ?: 0f
                    status = "Fase 44 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 44") }
            Text(
                listOfNotNull(
                    hvBalSt.label.takeIf { it.isNotBlank() },
                    hvCellMinVSt.label.takeIf { it.isNotBlank() },
                    hvCellMaxVSt.label.takeIf { it.isNotBlank() },
                    hvPwrSt.label.takeIf { it.isNotBlank() },
                    hvChgSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 44 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            Text("Fase 45 OBD (01B7/BA/BB–BD HVESS):", color = Mist)
            val hvCellMinTSt by com.veplayer.app.vehicle.HvCellMinTempMonitor.state.collectAsState()
            val hvDisSt by com.veplayer.app.vehicle.HvDisLimitMonitor.state.collectAsState()
            val hvEnrgInSt by com.veplayer.app.vehicle.HvEnrgInMonitor.state.collectAsState()
            val hvEnrgOutSt by com.veplayer.app.vehicle.HvEnrgOutMonitor.state.collectAsState()
            val hvEnrgTputSt by com.veplayer.app.vehicle.HvEnrgTputMonitor.state.collectAsState()
            var f45MinT by remember {
                mutableStateOf(if (prefs.hvCellMinTSimC != 0f) prefs.hvCellMinTSimC.toInt().toString() else "0")
            }
            var f45Dis by remember {
                mutableStateOf(if (prefs.hvDisSimA > 0f) prefs.hvDisSimA.toInt().toString() else "0")
            }
            var f45In by remember {
                mutableStateOf(if (prefs.hvEnrgInSimKwh > 0f) prefs.hvEnrgInSimKwh.toInt().toString() else "0")
            }
            var f45Out by remember {
                mutableStateOf(if (prefs.hvEnrgOutSimKwh > 0f) prefs.hvEnrgOutSimKwh.toInt().toString() else "0")
            }
            var f45Tput by remember {
                mutableStateOf(if (prefs.hvEnrgTputSimWh > 0f) prefs.hvEnrgTputSimWh.toInt().toString() else "0")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f45MinT,
                    onValueChange = { f45MinT = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                    label = { Text("HvMinT") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f45Dis,
                    onValueChange = { f45Dis = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("HvDis") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f45In,
                    onValueChange = { f45In = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("HvIn") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = f45Out,
                    onValueChange = { f45Out = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("HvOut") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = f45Tput,
                    onValueChange = { f45Tput = it.filter { c -> c.isDigit() }.take(8) },
                    label = { Text("HvTput") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    prefs.hvCellMinTSimC = f45MinT.toFloatOrNull() ?: 0f
                    prefs.hvDisSimA = f45Dis.toFloatOrNull() ?: 0f
                    prefs.hvEnrgInSimKwh = f45In.toFloatOrNull() ?: 0f
                    prefs.hvEnrgOutSimKwh = f45Out.toFloatOrNull() ?: 0f
                    prefs.hvEnrgTputSimWh = f45Tput.toFloatOrNull() ?: 0f
                    status = "Fase 45 sim aplicado"
                },
            ) { Text("Aplicar sim Fase 45") }
            Text(
                listOfNotNull(
                    hvCellMinTSt.label.takeIf { it.isNotBlank() },
                    hvDisSt.label.takeIf { it.isNotBlank() },
                    hvEnrgInSt.label.takeIf { it.isNotBlank() },
                    hvEnrgOutSt.label.takeIf { it.isNotBlank() },
                    hvEnrgTputSt.label.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { "Fase 45 idle" },
                color = Mute,
                fontSize = 12.sp,
            )
            var rpmOn by remember { mutableStateOf(prefs.rpmEnabled) }
            var rpmTts by remember { mutableStateOf(prefs.rpmTts) }
            var rpmSim by remember {
                mutableStateOf(if (prefs.rpmSim > 0f) prefs.rpmSim.toInt().toString() else "0")
            }
            val rpmSt by com.veplayer.app.vehicle.RpmOverRevMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso RPM altas", color = Mist)
                Switch(
                    checked = rpmOn,
                    onCheckedChange = {
                        rpmOn = it
                        prefs.rpmEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS RPM", color = Mist)
                Switch(
                    checked = rpmTts,
                    onCheckedChange = {
                        rpmTts = it
                        prefs.rpmTts = it
                    },
                )
            }
            OutlinedTextField(
                value = rpmSim,
                onValueChange = { rpmSim = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Sim RPM (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.rpmSim = rpmSim.toFloatOrNull() ?: 0f
                    status =
                        "RPM sim ${prefs.rpmSim.toInt()} · warn ${prefs.rpmWarn.toInt()} / alert ${prefs.rpmAlert.toInt()}"
                },
            ) { Text("Aplicar sim RPM") }
            Text(
                if (rpmSt.rpm != null) {
                    "RPM · ${rpmSt.label} · ${rpmSt.band}"
                } else {
                    "RPM idle (warn ${prefs.rpmWarn.toInt()} / alert ${prefs.rpmAlert.toInt()})"
                },
                color = if (rpmSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var loadOn by remember { mutableStateOf(prefs.engineLoadEnabled) }
            var loadTts by remember { mutableStateOf(prefs.engineLoadTts) }
            var loadSim by remember {
                mutableStateOf(
                    if (prefs.engineLoadSimPct > 0f) prefs.engineLoadSimPct.toInt().toString() else "0",
                )
            }
            val loadSt by com.veplayer.app.vehicle.EngineLoadMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso carga motor (0104)", color = Mist)
                Switch(
                    checked = loadOn,
                    onCheckedChange = {
                        loadOn = it
                        prefs.engineLoadEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS carga motor", color = Mist)
                Switch(
                    checked = loadTts,
                    onCheckedChange = {
                        loadTts = it
                        prefs.engineLoadTts = it
                    },
                )
            }
            OutlinedTextField(
                value = loadSim,
                onValueChange = { loadSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim carga % (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.engineLoadSimPct = loadSim.toFloatOrNull() ?: 0f
                    status =
                        "Load sim ${prefs.engineLoadSimPct.toInt()}% · warn ${prefs.engineLoadWarnPct.toInt()} / alert ${prefs.engineLoadAlertPct.toInt()}"
                },
            ) { Text("Aplicar sim carga") }
            Text(
                if (loadSt.loadPct != null) {
                    "Carga · ${loadSt.label} · ${loadSt.band}"
                } else {
                    "Carga idle (warn ${prefs.engineLoadWarnPct.toInt()}% / alert ${prefs.engineLoadAlertPct.toInt()}%)"
                },
                color = if (loadSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var stftOn by remember { mutableStateOf(prefs.stftEnabled) }
            var stftTts by remember { mutableStateOf(prefs.stftTts) }
            var stftSim by remember {
                mutableStateOf(
                    if (prefs.stftSimPct != 0f) prefs.stftSimPct.toInt().toString() else "0",
                )
            }
            val stftSt by com.veplayer.app.vehicle.FuelTrimStftMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso fuel trim STFT (0106)", color = Mist)
                Switch(
                    checked = stftOn,
                    onCheckedChange = {
                        stftOn = it
                        prefs.stftEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS STFT", color = Mist)
                Switch(
                    checked = stftTts,
                    onCheckedChange = {
                        stftTts = it
                        prefs.stftTts = it
                    },
                )
            }
            OutlinedTextField(
                value = stftSim,
                onValueChange = { stftSim = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                label = { Text("Sim STFT % (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.stftSimPct = stftSim.toFloatOrNull() ?: 0f
                    status =
                        "STFT sim ${prefs.stftSimPct.toInt()}% · warn ±${prefs.stftWarnPct.toInt()} / alert ±${prefs.stftAlertPct.toInt()}"
                },
            ) { Text("Aplicar sim STFT") }
            Text(
                if (stftSt.trimPct != null) {
                    "STFT · ${stftSt.label} · ${stftSt.band}"
                } else {
                    "STFT idle (warn ±${prefs.stftWarnPct.toInt()}% / alert ±${prefs.stftAlertPct.toInt()}%)"
                },
                color = if (stftSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var ltftOn by remember { mutableStateOf(prefs.ltftEnabled) }
            var ltftTts by remember { mutableStateOf(prefs.ltftTts) }
            var ltftSim by remember {
                mutableStateOf(
                    if (prefs.ltftSimPct != 0f) prefs.ltftSimPct.toInt().toString() else "0",
                )
            }
            val ltftSt by com.veplayer.app.vehicle.FuelTrimLtftMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso fuel trim LTFT (0107)", color = Mist)
                Switch(
                    checked = ltftOn,
                    onCheckedChange = {
                        ltftOn = it
                        prefs.ltftEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS LTFT", color = Mist)
                Switch(
                    checked = ltftTts,
                    onCheckedChange = {
                        ltftTts = it
                        prefs.ltftTts = it
                    },
                )
            }
            OutlinedTextField(
                value = ltftSim,
                onValueChange = { ltftSim = it.filter { c -> c.isDigit() || c == '-' }.take(4) },
                label = { Text("Sim LTFT % (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.ltftSimPct = ltftSim.toFloatOrNull() ?: 0f
                    status =
                        "LTFT sim ${prefs.ltftSimPct.toInt()}% · warn ±${prefs.ltftWarnPct.toInt()} / alert ±${prefs.ltftAlertPct.toInt()}"
                },
            ) { Text("Aplicar sim LTFT") }
            Text(
                if (ltftSt.trimPct != null) {
                    "LTFT · ${ltftSt.label} · ${ltftSt.band}"
                } else {
                    "LTFT idle (warn ±${prefs.ltftWarnPct.toInt()}% / alert ±${prefs.ltftAlertPct.toInt()}%)"
                },
                color = if (ltftSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var mapOn by remember { mutableStateOf(prefs.mapEnabled) }
            var mapTts by remember { mutableStateOf(prefs.mapTts) }
            var mapSim by remember {
                mutableStateOf(
                    if (prefs.mapSimKpa > 0f) prefs.mapSimKpa.toInt().toString() else "0",
                )
            }
            val mapSt by com.veplayer.app.vehicle.MapPressureMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso presión MAP (010B)", color = Mist)
                Switch(
                    checked = mapOn,
                    onCheckedChange = {
                        mapOn = it
                        prefs.mapEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS MAP", color = Mist)
                Switch(
                    checked = mapTts,
                    onCheckedChange = {
                        mapTts = it
                        prefs.mapTts = it
                    },
                )
            }
            OutlinedTextField(
                value = mapSim,
                onValueChange = { mapSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim MAP kPa (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.mapSimKpa = mapSim.toFloatOrNull() ?: 0f
                    status =
                        "MAP sim ${prefs.mapSimKpa.toInt()} kPa · warn ${prefs.mapWarnKpa.toInt()} / alert ${prefs.mapAlertKpa.toInt()}"
                },
            ) { Text("Aplicar sim MAP") }
            Text(
                if (mapSt.mapKpa != null) {
                    "MAP · ${mapSt.label} · ${mapSt.band}"
                } else {
                    "MAP idle (warn ${prefs.mapWarnKpa.toInt()} / alert ${prefs.mapAlertKpa.toInt()} kPa)"
                },
                color = if (mapSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
            )
            var thrOn by remember { mutableStateOf(prefs.throttleEnabled) }
            var thrTtsOn by remember { mutableStateOf(prefs.throttleTts) }
            var thrSim by remember {
                mutableStateOf(
                    if (prefs.throttleSimPct > 0f) prefs.throttleSimPct.toInt().toString() else "0",
                )
            }
            val thrSt by com.veplayer.app.vehicle.HighThrottleMonitor.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Aviso acelerador alto", color = Mist)
                Switch(
                    checked = thrOn,
                    onCheckedChange = {
                        thrOn = it
                        prefs.throttleEnabled = it
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("TTS acelerador", color = Mist)
                Switch(
                    checked = thrTtsOn,
                    onCheckedChange = {
                        thrTtsOn = it
                        prefs.throttleTts = it
                    },
                )
            }
            OutlinedTextField(
                value = thrSim,
                onValueChange = { thrSim = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Sim throttle % (0=live)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    prefs.throttleSimPct = thrSim.toFloatOrNull() ?: 0f
                    status =
                        "Throttle sim ${prefs.throttleSimPct.toInt()}% · warn ${prefs.throttleWarnPct.toInt()} / alert ${prefs.throttleAlertPct.toInt()} · hold ${prefs.throttleAlertHoldSec.toInt()}s"
                },
            ) { Text("Aplicar sim acelerador") }
            Text(
                if (thrSt.throttlePct != null) {
                    "${thrSt.label} · ${thrSt.band}" +
                        if (thrSt.highForSec > 0f) " · ${thrSt.highForSec.toInt()}s" else ""
                } else {
                    "Throttle idle (warn ≥${prefs.throttleWarnPct.toInt()}% · min ${prefs.throttleSpeedMinKmh.toInt()} km/h)"
                },
                color = if (thrSt.showWarn) Teal else Mute,
                fontSize = 12.sp,
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
