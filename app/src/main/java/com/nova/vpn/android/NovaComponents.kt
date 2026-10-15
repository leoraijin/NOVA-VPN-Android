package com.nova.vpn.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.DesignStyle

@Composable
internal fun novaControlShape(): Shape = when (LocalDesignPreset.current) {
    DesignStyle.WINDOWS_11 -> RoundedCornerShape(4.dp)
    DesignStyle.ONE_UI -> RoundedCornerShape(16.dp)

    else -> CircleShape
}

@Composable
internal fun NovaButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val expressive = LocalDesignPreset.current == DesignStyle.MATERIAL_YOU || LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS
    val scale by animateFloatAsState(if (pressed && expressive) .96f else 1f,
        animationSpec = spring(dampingRatio = .7f, stiffness = 500f), label = "button-press")
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).graphicsLayer { scaleX = scale; scaleY = scale }, enabled = enabled,
        interactionSource = interactions, shape = novaControlShape(), colors = colors, content = content)
}

@Composable
internal fun NovaOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = novaControlShape(),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Transparent),
        border = if (LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else .38f)), content = content)
}

@Composable
internal fun NovaTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = novaControlShape(), content = content)
}

@Composable
internal fun NovaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val style = LocalDesignPreset.current
    if (style == DesignStyle.MATERIAL_YOU) {
        Switch(checked, onCheckedChange, modifier, enabled = enabled)
        return
    }
    val colors = MaterialTheme.colorScheme
    val windows = style == DesignStyle.WINDOWS_11
    val height = if (windows) 24.dp else if (style == DesignStyle.ONE_UI) 26.dp else 30.dp
    val width = if (windows || style == DesignStyle.ONE_UI) 44.dp else 50.dp
    val thumb = if (windows) 16.dp else if (style == DesignStyle.ONE_UI) 20.dp else 24.dp
    val thumbOffset by animateDpAsState(if (checked) width - thumb - 6.dp else 0.dp,
        animationSpec = spring(dampingRatio = .9f, stiffness = if (windows) 1400f else 700f), label = "switch")
    val ios = style == DesignStyle.IOS_LIQUID_GLASS
    val track = if (ios) { if (checked) androidx.compose.ui.graphics.Color(0xFF34C759) else colors.surfaceVariant } else if (checked) colors.primary else if (windows) colors.surfaceVariant else colors.outline
    Box(modifier.widthIn(min = 52.dp).heightIn(min = 48.dp)
        .then(if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange) else Modifier),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(width, height).clip(CircleShape).background(track.copy(alpha = if (enabled) 1f else .38f)).then(if (windows && !checked) Modifier.border(1.dp, colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f), CircleShape) else Modifier).padding(3.dp)) {
            Box(Modifier.size(thumb).align(Alignment.CenterStart).offset(x = thumbOffset)
                .background(if (ios || style == DesignStyle.ONE_UI) androidx.compose.ui.graphics.Color.White else if (checked) colors.onPrimary else if (windows) colors.onSurfaceVariant else colors.surface, CircleShape))
        }
    }
}

@Composable
internal fun novaFieldShape(): Shape = RoundedCornerShape(when (LocalDesignPreset.current) {
    DesignStyle.WINDOWS_11 -> 4.dp
    DesignStyle.MATERIAL_YOU -> 20.dp

    else -> 16.dp
})
