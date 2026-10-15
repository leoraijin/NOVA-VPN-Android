package com.nova.vpn.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.vpn.android.data.*

@Composable
internal fun AppearancePicker(appearance: Appearance, onChange: (Appearance) -> Unit) {
    Text("Оформление", style = MaterialTheme.typography.titleMedium)
    Text("Выберите режим и стиль интерфейса.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().selectableGroup()
        .clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ThemeMode.entries.forEach { mode ->
            val selected = appearance.mode == mode
            Surface(Modifier.weight(1f).heightIn(min = 48.dp)
                .selectable(selected = selected, role = Role.RadioButton,
                    onClick = { onChange(appearance.copy(mode = mode)) }),
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (selected && LocalConfiguration.current.fontScale < 1.3f) { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(if (mode == ThemeMode.LIGHT) "Светлая" else "Тёмная", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    if (appearance.style == DesignStyle.MATERIAL_YOU) {
        Text("Цвет интерфейса", style = MaterialTheme.typography.titleMedium)
        Text("Оттенок · ${appearance.materialHue.toInt()}°", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Brush.horizontalGradient(
                (0..12).map { Color.hsv(it * 30f, .7f, .85f) })))
            Slider(appearance.materialHue, { onChange(appearance.copy(materialHue = it)) }, valueRange = 0f..360f,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Оттенок интерфейса" },
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
        }
    }
    if (appearance.style == DesignStyle.IOS_LIQUID_GLASS) {
        Text("Прозрачность стекла", style = MaterialTheme.typography.titleMedium)
        Slider(appearance.glassFrost, { onChange(appearance.copy(glassFrost = it)) }, valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Матирование стекла" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Прозрачное", style = MaterialTheme.typography.bodySmall)
            Text("Матовое", style = MaterialTheme.typography.bodySmall)
        }
        Text("Меняется стекло, текст остаётся чётким.", style = MaterialTheme.typography.bodySmall)
    }
    val columns = if (LocalConfiguration.current.fontScale >= 1.5f || LocalConfiguration.current.screenWidthDp < 340) 1 else 2
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DesignStyle.entries.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { style ->
                    StylePreview(style, appearance.mode, appearance.style == style,
                        Modifier.weight(1f), onClick = { onChange(appearance.copy(style = style)) })
                }
                if (row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
    Text("Цвет Material и прозрачность Liquid Glass сохраняются отдельно. Светлый и тёмный режим доступны для любого стиля.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StylePreview(style: DesignStyle, mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = designColorScheme(style, LocalContext.current, mode == ThemeMode.DARK, LocalMaterialHue.current)
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(modifier.selectable(selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(if (selected) 2.dp else 1.dp, outline),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().height(112.dp).clearAndSetSemantics { }
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(scheme.background, scheme.surfaceVariant)))
                .padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(40.dp).height(5.dp).background(scheme.onBackground, CircleShape))
                if (style == DesignStyle.IOS_LIQUID_GLASS) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(30.dp).background(scheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(12.dp).background(scheme.primary.copy(alpha = .6f), CircleShape))
                        }
                    }
                } else if (style == DesignStyle.ONE_UI) {
                    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(30.dp).height(6.dp).background(scheme.onSurface, CircleShape))
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.fillMaxWidth().height(12.dp).background(scheme.surface, RoundedCornerShape(4.dp)))
                        Box(Modifier.fillMaxWidth().height(12.dp).background(scheme.surface, RoundedCornerShape(4.dp)))
                    }
                } else if (style == DesignStyle.WINDOWS_11) {
                    Column(Modifier.fillMaxWidth().weight(1f).background(scheme.surface, RoundedCornerShape(3.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).background(scheme.primaryContainer, RoundedCornerShape(2.dp)))
                            Box(Modifier.padding(start = 5.dp).width(22.dp).height(4.dp).background(scheme.onSurface))
                        }
                        Box(Modifier.fillMaxWidth().height(3.dp).background(scheme.outlineVariant))
                        Box(Modifier.width(30.dp).height(3.dp).background(scheme.onSurfaceVariant))
                    }
                } else if (style == DesignStyle.MATERIAL_YOU) {
                    Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(scheme.primaryContainer).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.width(34.dp).height(5.dp).background(scheme.onPrimaryContainer, CircleShape))
                        Box(Modifier.width(26.dp).height(5.dp).background(scheme.onPrimaryContainer, CircleShape))
                        Box(Modifier.fillMaxWidth().height(10.dp).background(scheme.primary, CircleShape))
                    }
                } else Surface(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape((style.cardRadius / 2).dp), color = scheme.surface) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(20.dp).background(scheme.primaryContainer, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.weight(1f).height(4.dp).background(scheme.onSurfaceVariant, CircleShape))
                    }
                }
                Box(Modifier.align(Alignment.CenterHorizontally).width(48.dp).height(14.dp)
                    .background(scheme.primary, RoundedCornerShape(if (style == DesignStyle.WINDOWS_11) 2.dp else 8.dp)))
                Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(if (style == DesignStyle.WINDOWS_11) 2.dp else 12.dp))
                    .background(scheme.surface), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    repeat(4) { i -> Box(Modifier.size(5.dp).background(if (i == 0) scheme.primary else scheme.onSurfaceVariant, CircleShape)) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(style.label, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
