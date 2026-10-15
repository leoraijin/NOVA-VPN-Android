@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nova.vpn.android

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.vpn.android.data.DesignStyle

@Composable
internal fun NovaNavigationBar(content: @Composable RowScope.() -> Unit) {
    val style = LocalDesignPreset.current
    val colors = MaterialTheme.colorScheme
    val largeFont = LocalConfiguration.current.fontScale > 1.3f
    val navScroll = rememberScrollState()
    val floating = style == DesignStyle.IOS_LIQUID_GLASS
    val radius = when (style) {
        DesignStyle.IOS_LIQUID_GLASS -> 36.dp

        DesignStyle.MATERIAL_YOU -> 28.dp
        DesignStyle.ONE_UI -> 24.dp
        DesignStyle.WINDOWS_11 -> 8.dp
    }
    if (style == DesignStyle.WINDOWS_11) {
        Surface(color = colors.background) {
            Column {
                HorizontalDivider(color = colors.outlineVariant)
                Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                    .then(if (largeFont) Modifier.horizontalScroll(navScroll) else Modifier)
                    .selectableGroup().padding(horizontal = 8.dp, vertical = 8.dp), content = content)
            }
        }
        return
    }
    if (style == DesignStyle.ONE_UI) {
        Surface(color = colors.background) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .then(if (largeFont) Modifier.horizontalScroll(navScroll) else Modifier)
                .selectableGroup().padding(horizontal = 8.dp, vertical = 10.dp), content = content)
        }
        return
    }
    if (style == DesignStyle.MATERIAL_YOU) {
        Surface(color = colors.surfaceContainer) {
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                .then(if (largeFont) Modifier.horizontalScroll(navScroll) else Modifier)
                .selectableGroup().padding(horizontal = 4.dp, vertical = 8.dp), content = content)
        }
        return
    }
    val systemNavigationHeight = WindowInsets.navigationBars.getBottom(LocalDensity.current).toFloat()
    Box(Modifier.fillMaxWidth().drawBehind {
        // Keep scrolling content out of the system-button area while preserving floating glass above it.
        if (systemNavigationHeight > 0f) drawRect(colors.background,
            topLeft = Offset(0f, size.height - systemNavigationHeight),
            size = Size(size.width, systemNavigationHeight))
    }.windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = if (floating) 14.dp else 8.dp, vertical = 8.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(radius),
            color = if (style == DesignStyle.IOS_LIQUID_GLASS) Color.Transparent else if (floating) colors.surface.copy(alpha = .94f) else colors.surface,
            shadowElevation = if (style == DesignStyle.IOS_LIQUID_GLASS) 3.dp else if (floating) 8.dp else 0.dp,
            border = BorderStroke(1.dp, colors.outline.copy(alpha = if (floating) .4f else .15f))) {
            Row(Modifier.fillMaxWidth().then(if (largeFont) Modifier.horizontalScroll(navScroll) else Modifier).selectableGroup()
                .then(if (floating) Modifier.novaGlass(useContent = true) else Modifier)
                .padding(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
internal fun RowScope.NovaNavigationItem(selected: Boolean, enabled: Boolean, onClick: () -> Unit, icon: ImageVector, label: String) {
    val style = LocalDesignPreset.current
    val colors = MaterialTheme.colorScheme
    val windows = style == DesignStyle.WINDOWS_11
    val shape = RoundedCornerShape(if (windows) 4.dp else 28.dp)
    val tint = if (selected) colors.primary else colors.onSurfaceVariant
    val visibleLabel = if (label == "Маршрутизация") "Маршруты" else label
    val fontScale = LocalConfiguration.current.fontScale
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected, fontScale) { if (selected && fontScale > 1.3f) bringIntoView.bringIntoView() }
    val itemModifier = if (fontScale > 1.3f) Modifier.width((104 * fontScale).dp) else Modifier.weight(1f)
    if (windows) {
        Box(itemModifier.bringIntoViewRequester(bringIntoView).padding(horizontal = 2.dp).clip(RoundedCornerShape(4.dp))
            .background(if (selected) colors.surface else Color.Transparent)
            .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick)) {
            Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(20.dp)
                .background(if (selected) colors.primary else Color.Transparent, RoundedCornerShape(2.dp)))
            Column(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val glyph = when (label) {
                    "Главная" -> Icons.Outlined.Home
                    "Серверы" -> Icons.Outlined.Dns
                    "Маршрутизация" -> Icons.Outlined.Route
                    "Настройки" -> Icons.Outlined.Settings
                    else -> icon
                }
                Icon(glyph, null, Modifier.size(20.dp), tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .38f))
                Text(visibleLabel, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = .38f))
            }
        }
        return
    }
    if (style == DesignStyle.ONE_UI) {
        Column(itemModifier.bringIntoViewRequester(bringIntoView).clip(RoundedCornerShape(12.dp))
            .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick).heightIn(min = 56.dp).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (!enabled) colors.onSurface.copy(alpha = .38f) else if (selected) colors.onSurface else colors.onSurfaceVariant)
            Text(visibleLabel, fontSize = 12.sp, maxLines = 1,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant)
            Box(Modifier.width(20.dp).height(2.dp).background(if (selected) colors.onSurface else Color.Transparent, RoundedCornerShape(1.dp)))
        }
        return
    }
    if (style == DesignStyle.MATERIAL_YOU) {
        Column(itemModifier.bringIntoViewRequester(bringIntoView).heightIn(min = 64.dp)
            .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = if (selected) colors.secondaryContainer else Color.Transparent) {
                Icon(icon, null, Modifier.padding(horizontal = 20.dp, vertical = 4.dp).size(24.dp),
                    tint = if (!enabled) colors.onSurface.copy(alpha = .38f) else if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
            }
            Text(visibleLabel, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1,
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                color = colors.onSurface)
        }
        return
    }
    Column(itemModifier.bringIntoViewRequester(bringIntoView).heightIn(min = 56.dp).clip(shape)
        .background(if (selected && !windows) { if (style == DesignStyle.IOS_LIQUID_GLASS) colors.surfaceVariant.copy(alpha = .75f) else colors.primaryContainer } else Color.Transparent)
        .semantics { contentDescription = label }
        .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val displayIcon = if (style == DesignStyle.IOS_LIQUID_GLASS && !selected) when (label) {
            "Главная" -> Icons.Outlined.Home
            "Серверы" -> Icons.Outlined.Public
            "Маршрутизация" -> Icons.Outlined.Tune
            "Настройки" -> Icons.Outlined.Settings
            else -> icon
        } else icon
        Icon(displayIcon, contentDescription = null, tint = if (enabled) tint else tint.copy(alpha = .38f), modifier = Modifier.size(24.dp))
        Text(visibleLabel, maxLines = 1, fontSize = 11.sp, lineHeight = 14.sp, color = if (enabled) tint else tint.copy(alpha = .38f))
        if (windows) Box(Modifier.width(18.dp).height(3.dp).background(if (selected) colors.primary else Color.Transparent, RoundedCornerShape(2.dp)))
    }
}
