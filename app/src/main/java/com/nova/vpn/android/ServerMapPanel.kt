package com.nova.vpn.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.nova.vpn.android.NovaSwitch as Switch
import com.nova.vpn.android.NovaTextButton as TextButton
import com.nova.vpn.android.NovaOutlinedButton as OutlinedButton
import com.nova.vpn.android.NovaButton as Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.nova.vpn.android.data.ImportedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

internal fun serverCheckLabel(result: ServerCheckResult?, checking: Boolean = false): String = when {
    checking -> "Проверяется…"
    result?.outcome == CheckOutcome.SUCCESS -> "${result.millis} мс"
    result?.outcome == CheckOutcome.FAILED -> "Нет ответа"
    result?.outcome == CheckOutcome.UNSUPPORTED -> "Проверка не поддерживается"
    else -> "Не проверен"
}

@Composable internal fun ServerCheckBadge(result: ServerCheckResult?, checking: Boolean = false) {
    val color = checkColor(result, MaterialTheme.colorScheme.surface.luminance() > .5f)
    Surface(color = color.copy(alpha = .12f), shape = MaterialTheme.shapes.small) {
        Text(serverCheckLabel(result, checking), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if(checking) MaterialTheme.colorScheme.primary else color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun checkColor(result: ServerCheckResult?, light: Boolean = false) = when(result?.outcome) {
    CheckOutcome.SUCCESS -> if(light) Color(0xFF137D5A) else Color(0xFF28BF88)
    CheckOutcome.FAILED -> if(light) Color(0xFFB83B4B) else Color(0xFFEC6976)
    else -> if(light) Color(0xFF596574) else Color(0xFF8793A4)
}

private data class MapShape(val country: MapCountry, val path: Path)
private data class LocatedProfile(val profile: ImportedProfile, val location: ServerLocation)
private data class MapMarker(val members: List<LocatedProfile>, val selected: LocatedProfile, val screen: Offset)

@Composable internal fun ServerMapPanel(
    profiles: List<ImportedProfile>, selectedId: String, checks: ServerCheckState,
    vpnState: VpnUiState, onCheck: () -> Unit, onCancel: () -> Unit,
    onSelect: (String) -> Unit, onConnect: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    var mapFailed by remember { mutableStateOf(false) }
    val data by produceState<ServerMapData?>(null) {
        val loaded = withContext(Dispatchers.IO) { runCatching { ServerMapData.load(context) } }
        value = loaded.getOrNull(); mapFailed = loaded.isFailure
    }
    val locations by produceState(emptyList<LocatedProfile>(), data, profiles) {
        value = withContext(Dispatchers.Default) { profiles.mapNotNull { p -> data?.resolve(p.name)?.let { LocatedProfile(p, it) } } }
    }
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val selectedLocation = locations.firstOrNull { it.profile.id == selected?.id }?.location
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(colors.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Карта серверов", fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                Text("${locations.size} из ${profiles.size} на карте", fontSize = 12.sp, color = colors.onSurfaceVariant)
            }
            if (checks.running) OutlinedButton(onClick = onCancel) {
                Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Стоп")
            } else OutlinedButton(onClick = onCheck, enabled = profiles.isNotEmpty() && !vpnState.connecting) {
                Icon(Icons.Rounded.Radar, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Проверить")
            }
        }
        if (data != null) InteractiveServerMap(data!!, locations, selectedId, checks, onSelect)
        else Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            Text(if(mapFailed) "Не удалось открыть карту. Профили доступны в списке ниже." else "Подготовка карты…", color = colors.onSurfaceVariant)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Ответил" to Color(0xFF28BF88), "Нет ответа" to Color(0xFFEC6976), "Не проверен" to Color(0xFF8793A4)).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(color, CircleShape)); Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 10.sp, color = colors.onSurfaceVariant)
                }
            }
        }
        if (checks.running) {
            LinearProgressIndicator(progress = { if(checks.total == 0) 0f else checks.completed.toFloat() / checks.total }, modifier = Modifier.fillMaxWidth())
            Text("Проверка ${checks.completed} из ${checks.total}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp)
        } else checks.message?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontSize = 12.sp, color = colors.onSurfaceVariant) }
        selected?.let { profile ->
            HorizontalDivider(color = colors.outlineVariant)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(selectedLocation?.label ?: "Локация не указана в названии профиля", fontSize = 12.sp, color = colors.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.server.ifBlank { profile.protocol.uppercase() }, Modifier.weight(1f), maxLines = 1, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    ServerCheckBadge(checks.results[profile.id], checks.currentId == profile.id)
                }
                checks.results[profile.id]?.let {
                    Text("Проверено в ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.time))}", fontSize = 11.sp, color = colors.onSurfaceVariant)
                }
                Button(onClick = onConnect, enabled = !vpnState.connecting && !vpnState.connected, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(when { vpnState.connecting -> "Подключение…"; vpnState.connected -> "VPN подключён"; else -> "Подключить" })
                }
            }
        }
        Text("Место определяется по названию профиля. Проверка — только по кнопке.",
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), fontSize = 10.sp, color = colors.onSurfaceVariant)
    }
}

@Composable private fun InteractiveServerMap(data: ServerMapData, locations: List<LocatedProfile>, selectedId: String,
    checks: ServerCheckState, onSelect: (String) -> Unit) {
    var camera by remember { mutableStateOf(ServerMapCamera()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var touched by remember { mutableStateOf(false) }
    val latestCamera by rememberUpdatedState(camera)
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val shapes = remember(data) { data.countries.map { country ->
        MapShape(country, Path().apply {
            country.rings.forEach { ring ->
                var previous = ring.firstOrNull()?.x ?: return@forEach
                ring.forEachIndexed { index, point ->
                    var x = point.x
                    while (x - previous > .5) x -= 1
                    while (x - previous < -.5) x += 1
                    if(index == 0) moveTo((x * 1000).toFloat(), (point.y * 1000).toFloat())
                    else lineTo((x * 1000).toFloat(), (point.y * 1000).toFloat())
                    previous = x
                }
                close()
            }
        })
    } }
    LaunchedEffect(locations) {
        if (!touched && locations.isNotEmpty()) {
            val xs = locations.map { it.location.point.x }; val ys = locations.map { it.location.point.y }
            val span = max(xs.max() - xs.min(), (ys.max() - ys.min()) * 1.2).coerceAtLeast(.12)
            camera = ServerMapCamera((xs.max() + xs.min()) / 2, (ys.max() + ys.min()) / 2, (.7 / span).toFloat().coerceIn(1f, 5f))
        }
    }
    fun project(point: MapPoint): Offset = Offset((viewport.width / 2 + (point.x - camera.x) * viewport.width * camera.zoom).toFloat(),
        (viewport.height / 2 + (point.y - camera.y) * viewport.width * camera.zoom).toFloat())
    val cell = with(density) { 38.dp.toPx() }
    val markers = remember(locations, camera, viewport, selectedId) {
        locations.map { it to project(it.location.point) }.filter { (_, p) -> p.x in -24f..(viewport.width + 24f) && p.y in -24f..(viewport.height + 24f) }
            .groupBy { (_, p) -> floor(p.x / cell).toInt() to floor(p.y / cell).toInt() }
            .values.map { group ->
                val selected = group.firstOrNull { it.first.profile.id == selectedId } ?: group.first()
                MapMarker(group.map { it.first }, selected.first, selected.second)
            }
    }
    val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val labelCountries = remember(data, locations) {
        val represented = locations.map { it.location.country }.toSet()
        data.countries.sortedByDescending { it.name in represented }
    }
    val textSize = with(density) { 11.sp.toPx() }
    val dotRadius = with(density) { 6.dp.toPx() }
    Box(Modifier.fillMaxWidth().height(410.dp).clip(RoundedCornerShape(18.dp)).onSizeChanged { viewport = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    touched = true
                    camera = latestCamera.transform(pan.x, pan.y, zoom, centroid.x, centroid.y, size.width.toFloat(), size.height.toFloat())
                }
            }) {
        Canvas(Modifier.fillMaxSize()
            .semantics { contentDescription = "Карта серверов. Двигайте одним пальцем, масштабируйте двумя. Выбор также доступен в списке ниже." }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { focus ->
                    touched = true
                    camera = latestCamera.transform(0f, 0f, 2f, focus.x, focus.y, size.width.toFloat(), size.height.toFloat())
                })
            }) {
            drawRect(colors.background)
            val world = size.width * camera.zoom
            val scale = world / 1000
            withTransform({ translate(size.width / 2 - (camera.x * world).toFloat(), size.height / 2 - (camera.y * world).toFloat()); scale(scale, scale, Offset.Zero) }) {
                shapes.forEach { shape ->
                    drawPath(shape.path, colors.surfaceVariant)
                    drawPath(shape.path, colors.onSurfaceVariant.copy(alpha = .32f), style = Stroke(.8f / scale))
                }
            }
            labelPaint.textSize = textSize
            labelPaint.color = colors.onSurfaceVariant.copy(alpha = .55f).toArgb()
            val labelBounds = ArrayList<android.graphics.RectF>()
            if (camera.zoom >= 2) labelCountries.forEach { country ->
                val p = project(country.center)
                val halfWidth = labelPaint.measureText(country.name) / 2
                val bounds = android.graphics.RectF(p.x - halfWidth - 5, p.y - textSize - 5, p.x + halfWidth + 5, p.y + 5)
                if(bounds.left >= 8 && bounds.right <= size.width - 8 && bounds.top >= 8 && bounds.bottom <= size.height - 12 &&
                    labelBounds.none { android.graphics.RectF.intersects(it, bounds) }) {
                    drawContext.canvas.nativeCanvas.drawText(country.name, p.x - halfWidth, p.y, labelPaint)
                    labelBounds.add(bounds)
                }
            }
            markers.forEach { marker ->
                val result = checks.results[marker.selected.profile.id]
                val color = checkColor(result, colors.surface.luminance() > .5f)
                val selected = marker.members.any { it.profile.id == selectedId }
                if (selected) drawCircle(colors.primary.copy(alpha = .17f), dotRadius * 3.8f, marker.screen)
                drawCircle(colors.background, dotRadius + 3, marker.screen)
                drawCircle(color, dotRadius, marker.screen)
                if (selected) drawCircle(colors.primary, dotRadius + 5, marker.screen, style = Stroke(2f))
                val label = if(marker.members.size > 1) "${marker.members.size} · ${serverCheckLabel(result)}"
                    else if(selected) "${marker.selected.location.city ?: marker.selected.location.country} · ${serverCheckLabel(result)}"
                    else result?.millis?.let { "$it мс" } ?: ""
                if(label.isNotBlank()) {
                    labelPaint.color = colors.onSurface.toArgb()
                    val width = labelPaint.measureText(label)
                    val x = marker.screen.x.coerceIn(width / 2 + 8, max(width / 2 + 8, size.width - width / 2 - 8))
                    val y = (marker.screen.y - dotRadius - 14).coerceAtLeast(textSize + 10)
                    drawRoundRect(colors.surface.copy(alpha = .96f), Offset(x - width / 2 - 7, y - textSize - 5),
                        androidx.compose.ui.geometry.Size(width + 14, textSize + 12), androidx.compose.ui.geometry.CornerRadius(8f))
                    drawContext.canvas.nativeCanvas.drawText(label, x - width / 2, y, labelPaint)
                }
            }
        }
        markers.forEach { marker ->
            Box(Modifier.offset { IntOffset(marker.screen.x.roundToInt() - with(density) { 22.dp.roundToPx() }, marker.screen.y.roundToInt() - with(density) { 22.dp.roundToPx() }) }
                .size(44.dp).clip(CircleShape)
                .semantics { contentDescription = "${marker.selected.profile.name}, ${serverCheckLabel(checks.results[marker.selected.profile.id])}. Профилей в точке: ${marker.members.size}" }
                .clickable {
                    val current = marker.members.indexOfFirst { it.profile.id == selectedId }
                    val next = if(current < 0) marker.members.first() else marker.members[(current + 1) % marker.members.size]
                    if (next.profile.id != selectedId) onSelect(next.profile.id)
                })
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(10.dp).background(colors.surface.copy(alpha = .94f), RoundedCornerShape(18.dp))) {
            IconButton(onClick = { touched = true; camera = camera.transform(0f,0f,1.5f,viewport.width/2f,viewport.height/2f,viewport.width.toFloat(),viewport.height.toFloat()) }) { Icon(Icons.Rounded.Add, "Приблизить карту") }
            IconButton(onClick = { touched = true; camera = camera.transform(0f,0f,1/1.5f,viewport.width/2f,viewport.height/2f,viewport.width.toFloat(),viewport.height.toFloat()) }) { Icon(Icons.Rounded.Remove, "Отдалить карту") }
            IconButton(onClick = { touched = true; camera = ServerMapCamera(.5,.5,1f) }) { Icon(Icons.Rounded.Public, "Показать весь мир") }
            IconButton(onClick = {
                locations.firstOrNull { it.profile.id == selectedId }?.location?.point?.let {
                    touched = true; camera = ServerMapCamera(it.x, it.y, 5f)
                }
            }) { Icon(Icons.Rounded.CenterFocusStrong, "К выбранному серверу") }
        }
        Text("Natural Earth", Modifier.align(Alignment.BottomStart).padding(10.dp), color = colors.onSurfaceVariant, fontSize = 10.sp)
    }
}
