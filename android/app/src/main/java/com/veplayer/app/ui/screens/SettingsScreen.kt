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
