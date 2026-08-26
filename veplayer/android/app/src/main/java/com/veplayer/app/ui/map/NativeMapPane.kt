package com.veplayer.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.data.VePrefs
import com.veplayer.app.nav.GeoProjection
import com.veplayer.app.nav.LatLng
import com.veplayer.app.nav.MapBounds
import com.veplayer.app.nav.NavEngine
import com.veplayer.app.nav.TileKey
import com.veplayer.app.nav.WebMercator
import com.veplayer.app.surround.ActorKind
import com.veplayer.app.surround.SurroundEngine
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Night
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.vehicle.VehicleState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Native Compose map: OSM raster tiles (Web Mercator) + route polyline from [NavEngine].
 */
@Composable
fun NativeMapPane() {
    val context = LocalContext.current
    val prefs = remember { VePrefs(context) }
    val scope = rememberCoroutineScope()
    val route by NavEngine.route.collectAsState()
    val destinations by NavEngine.destinations.collectAsState()
    val vehicle by VehicleState.state.collectAsState()
    val surround by SurroundEngine.snapshot.collectAsState()

    val ego = LatLng(prefs.navFromLat, prefs.navFromLng)
    val dest = LatLng(prefs.navToLat, prefs.navToLng)
    val tilesOn = prefs.mapTilesEnabled
    var crowdOn by remember { mutableStateOf(prefs.mapCrowdEnabled) }
    val tileTemplate = prefs.mapTileUrl
    val pathPts =
        remember(route.geometry, ego, dest) {
            val g = route.geometry.map { LatLng(it.first, it.second) }
            if (g.size >= 2) g else listOf(ego, dest)
        }
    val bounds =
        remember(pathPts) {
            MapBounds.fromPoints(pathPts)?.padded(0.18)
                ?: MapBounds(ego.lat - 0.02, ego.lat + 0.02, ego.lng - 0.02, ego.lng + 0.02)
        }
    val progress = remember(pathPts, ego) { GeoProjection.progressAlong(pathPts, ego) }

    var canvasW by remember { mutableIntStateOf(0) }
    var canvasH by remember { mutableIntStateOf(0) }
    val tileBitmaps = remember { mutableStateMapOf<TileKey, ImageBitmap>() }
    var tileZoom by remember { mutableIntStateOf(0) }

    val viewport =
        remember(bounds, canvasW, canvasH) {
            if (canvasW < 8 || canvasH < 8) {
                null
            } else {
                WebMercator.viewportFor(bounds, canvasW.toFloat(), canvasH.toFloat())
            }
        }

    val neededTiles =
        remember(viewport, canvasW, canvasH, tilesOn) {
            if (!tilesOn || viewport == null) {
                emptyList()
            } else {
                WebMercator.tilesCovering(viewport, canvasW.toFloat(), canvasH.toFloat())
            }
        }

    LaunchedEffect(neededTiles, tileTemplate) {
        if (neededTiles.isEmpty()) {
            tileBitmaps.clear()
            tileZoom = 0
            return@LaunchedEffect
        }
        OsmTileStore.urlTemplate = tileTemplate
        tileZoom = neededTiles.first().z
        val keep = neededTiles.toSet()
        tileBitmaps.keys.filter { it !in keep }.forEach { tileBitmaps.remove(it) }
        neededTiles.forEach { key ->
            if (tileBitmaps.containsKey(key)) return@forEach
            launch {
                val bmp = OsmTileStore.get(context, key, tileTemplate) ?: return@launch
                tileBitmaps[key] = bmp
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Destino", color = Mute, fontSize = 12.sp)
            val chips =
                destinations.ifEmpty {
                    listOf(
                        com.veplayer.app.nav.NavDestination("altamira", "Altamira", 10.4965, -66.8492),
                        com.veplayer.app.nav.NavDestination("chacao", "Chacao", 10.4958, -66.8756),
                        com.veplayer.app.nav.NavDestination("bellas-artes", "Bellas Artes", 10.4989, -66.8986),
                    )
                }
            chips.forEach { d ->
                val selected = prefs.navDestName == d.name
                if (selected) {
                    Button(
                        onClick = { NavEngine.setDestination(d, scope) },
                    ) { Text(d.name, fontSize = 12.sp) }
                } else {
                    OutlinedButton(
                        onClick = { NavEngine.setDestination(d, scope) },
                    ) { Text(d.name, fontSize = 12.sp) }
                }
            }
            if (crowdOn) {
                Button(
                    onClick = {
                        crowdOn = false
                        prefs.mapCrowdEnabled = false
                    },
                ) { Text("Crowd", fontSize = 12.sp) }
            } else {
                OutlinedButton(
                    onClick = {
                        crowdOn = true
                        prefs.mapCrowdEnabled = true
                    },
                ) { Text("Crowd", fontSize = 12.sp) }
            }
        }

        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Night)
                    .onSizeChanged {
                        canvasW = it.width
                        canvasH = it.height
                    },
        ) {
            val vp = viewport
            val bitmaps = tileBitmaps.toMap()
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            listOf(Color(0xFF0B1220), Color(0xFF0F1C18), Color(0xFF0A1018)),
                        ),
                )

                if (vp != null && bitmaps.isNotEmpty()) {
                    for ((key, bmp) in bitmaps.entries) {
                        val rect = WebMercator.tileScreenRect(key, vp)
                        val dstSize =
                            IntSize(
                                rect.width.roundToInt().coerceAtLeast(1),
                                rect.height.roundToInt().coerceAtLeast(1),
                            )
                        drawImage(
                            image = bmp,
                            dstOffset =
                                IntOffset(
                                    rect.left.roundToInt(),
                                    rect.top.roundToInt(),
                                ),
                            dstSize = dstSize,
                        )
                    }
                    // Night cockpit scrim so route stays readable
                    drawRect(Color(0x66081210))
                } else if (vp == null || !tilesOn) {
                    val grid = Color(0xFF1A2A24)
                    for (i in 1 until 8) {
                        val x = w * i / 8f
                        val y = h * i / 8f
                        drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                        drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                    }
                }

                fun xy(p: LatLng): Offset {
                    return if (vp != null) {
                        val (x, y) = WebMercator.project(p.lat, p.lng, vp)
                        Offset(x, y)
                    } else {
                        val (x, y) = GeoProjection.project(p.lat, p.lng, bounds, w, h)
                        Offset(x, y)
                    }
                }

                if (pathPts.size >= 2) {
                    val path = Path()
                    val first = xy(pathPts.first())
                    path.moveTo(first.x, first.y)
                    for (i in 1 until pathPts.size) {
                        val o = xy(pathPts[i])
                        path.lineTo(o.x, o.y)
                    }
                    drawPath(path, Color(0x553E9EFD), style = Stroke(width = 14f, cap = StrokeCap.Round))
                    drawPath(path, Color(0xFF3E9EFD), style = Stroke(width = 5f, cap = StrokeCap.Round))

                    val tipIdx =
                        ((pathPts.size - 1) * progress).toInt().coerceIn(0, pathPts.lastIndex)
                    val tip = xy(pathPts[tipIdx])
                    drawCircle(Teal.copy(alpha = 0.35f), radius = 18f, center = tip)
                }

                val dxy = xy(dest)
                drawCircle(Color(0xFFE11D48), radius = 10f, center = dxy)
                drawCircle(Color(0xFFFFF1F2), radius = 4f, center = dxy)

                val exy = xy(ego)
                val heading = vehicle.headingDeg ?: 0f

                if (crowdOn) {
                    for (actor in surround.actors) {
                        val ll =
                            GeoProjection.offsetToLatLng(
                                ego,
                                heading,
                                actor.xM,
                                actor.yM,
                            )
                        val p = xy(ll)
                        if (p.x < -20f || p.y < -20f || p.x > w + 20f || p.y > h + 20f) continue
                        val color =
                            when (actor.kind) {
                                ActorKind.PERSON -> Color(0xFFFFB74D)
                                ActorKind.MOTORCYCLE, ActorKind.BICYCLE -> Color(0xFF80CBC4)
                                ActorKind.TRUCK, ActorKind.BUS -> Color(0xFF90A4AE)
                                ActorKind.CAR -> Color(0xFFB0BEC5)
                                ActorKind.UNKNOWN -> Color(0xFF78909C)
                            }
                        val r =
                            when (actor.kind) {
                                ActorKind.PERSON -> 5f
                                ActorKind.TRUCK, ActorKind.BUS -> 8f
                                else -> 6f
                            }
                        drawCircle(color.copy(alpha = 0.35f), radius = r + 6f, center = p)
                        drawCircle(color, radius = r, center = p)
                    }
                }

                rotate(degrees = heading, pivot = exy) {
                    val chevron =
                        Path().apply {
                            moveTo(exy.x, exy.y - 16f)
                            lineTo(exy.x - 11f, exy.y + 12f)
                            lineTo(exy.x, exy.y + 4f)
                            lineTo(exy.x + 11f, exy.y + 12f)
                            close()
                        }
                    drawPath(chevron, Color(0xFF18C964))
                    drawPath(chevron, Color(0xFF042F2E), style = Stroke(width = 2f))
                }
            }

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC0B1411))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                val ready = bitmaps.size
                val tileLabel =
                    when {
                        !tilesOn -> "sin tiles"
                        ready > 0 -> "OSM z$tileZoom · $ready/${neededTiles.size}"
                        neededTiles.isNotEmpty() -> "OSM z${neededTiles.firstOrNull()?.z ?: "—"}…"
                        else -> "OSM…"
                    }
                Text(
                    "Mapa nativo · $tileLabel · ${route.source} · ${(progress * 100).toInt()}%",
                    color = Mist,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${pathPts.size} pts · ${route.destinationName.ifBlank { "—" }}" +
                        if (crowdOn) " · crowd ${surround.actors.size}" else "",
                    color = Mute,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
