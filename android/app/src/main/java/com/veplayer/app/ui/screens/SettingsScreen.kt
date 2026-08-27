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
            Text(
                if (shift.status == "open") {
                    "Turno #${shift.id} · ${"%.1f".format(shift.distanceKm)} km"
                } else if (shift.status == "closed") {
                    "Último turno cerrado · ${"%.1f".format(shift.distanceKm)} km"
                } else {
                    "Turno: —"
                },
                color = Mute,
            )
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
                            r.onSuccess { status = "Turno cerrado · ${"%.1f".format(it.distanceKm)} km" }
                                .onFailure { status = "Turno end: ${it.message}" }
                        }
                    },
                ) { Text("Cerrar turno") }
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
