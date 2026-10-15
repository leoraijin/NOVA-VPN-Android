package com.nova.vpn.android

import android.app.ActivityManager
import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import com.nova.vpn.android.data.DesignStyle
import com.nova.vpn.android.data.ThemeMode
import kotlin.math.roundToInt

/** Scene excludes controls. Content may sample scene, but never itself; overlays sample content. */
@Stable
internal class NovaBackdrop(val scene: GraphicsLayer, val content: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    var recorded by mutableStateOf(false)
}
internal val LocalGlassTierOverride = compositionLocalOf<Int?> { null }
internal val LocalNovaBackdrop = staticCompositionLocalOf<NovaBackdrop?> { null }

internal fun glassTier(sdk: Int, lowRam: Boolean, effectsEnabled: Boolean): Int = when {
    lowRam || !effectsEnabled || sdk < 31 -> 0
    sdk < 33 -> 1
    else -> 2
}

@Composable
private fun currentGlassTier(): Int {
    val context = LocalContext.current
    val supported = remember(context) {
        glassTier(Build.VERSION.SDK_INT,
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice,
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
    }
    return LocalGlassTierOverride.current?.coerceIn(0, supported) ?: supported
}

/** Records the actual painted scene and screen content once per invalidation, never on a timer. */
@Composable
internal fun Modifier.novaBackdropSource(): Modifier {
    val backdrop = LocalNovaBackdrop.current ?: return this
    val colors = MaterialTheme.colorScheme
    val style = LocalDesignPreset.current
    val active = currentGlassTier() > 0 && style in listOf(DesignStyle.IOS_LIQUID_GLASS, DesignStyle.WINDOWS_11)
    val view = LocalView.current
    val glass = style == DesignStyle.IOS_LIQUID_GLASS
    val background = Brush.verticalGradient(if (glass) listOf(colors.background, lerp(colors.background, colors.primaryContainer, .16f), colors.background) else if (style == DesignStyle.WINDOWS_11) listOf(lerp(colors.background, colors.primaryContainer, .20f), colors.background, colors.background) else listOf(colors.background, colors.background))
    return onGloballyPositioned { coordinates ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        backdrop.origin = coordinates.positionInRoot() + Offset(location[0].toFloat(), location[1].toFloat())
    }.drawWithContent {
        if (active) {
            backdrop.scene.record { drawRect(background) }
            backdrop.content.record {
                drawLayer(backdrop.scene)
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.content)
            if (!backdrop.recorded) backdrop.recorded = true
        } else {
            drawRect(background)
            drawContent()
        }
    }
}

/** Blur/refraction affects only the sampled layer. Foreground labels are drawn afterwards. */
@Composable
internal fun Modifier.novaGlass(useContent: Boolean = false, acrylic: Boolean = false): Modifier {
    val backdrop = LocalNovaBackdrop.current
    val colors = MaterialTheme.colorScheme
    val dark = LocalThemeMode.current == ThemeMode.DARK
    val tier = currentGlassTier()
    val frost = LocalGlassFrost.current.coerceIn(0f, 1f)
    val liquid = LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS
    val view = LocalView.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val crop = rememberGraphicsLayer()
    val surface = colors.surface
    return onGloballyPositioned { coordinates ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        origin = coordinates.positionInRoot() + Offset(location[0].toFloat(), location[1].toFloat())
    }.drawWithCache {
        val fallback = Brush.verticalGradient(listOf(surface, colors.surfaceVariant, surface))
        val dimensions = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
        val effect = if (tier > 0 && Build.VERSION.SDK_INT >= 31) backdropEffect(tier, size.width, size.height, (if (liquid) 1f + frost * 31f else 14f) * density, acrylic) else null
        crop.renderEffect = effect
        val tintAlpha = if (acrylic) .78f else if (liquid) .06f + frost * .86f else if (dark) .58f else .64f
        val sheen = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) .12f else .28f), Color.Transparent, Color.Black.copy(alpha = .06f)))
        onDrawBehind {
            if (tier > 0 && backdrop != null && backdrop.recorded) {
                val delta = backdrop.origin - origin
                crop.record(size = dimensions) {
                    translate(delta.x, delta.y) { drawLayer(if (useContent) backdrop.content else backdrop.scene) }
                }
                drawLayer(crop)
                drawRect(surface.copy(alpha = tintAlpha))
                drawRect(sheen)
                if (acrylic) {
                    // Stationary microtexture: deterministic and bounded to this panel.
                    val step = 5f * density
                    var y = 0f
                    while (y < size.height) {
                        var x = 0f
                        while (x < size.width) {
                            drawCircle(Color.White.copy(alpha = .035f), .4f * density, Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
            } else if (liquid) drawRect(surface.copy(alpha = .12f + frost * .8f)) else drawRect(fallback)
        }
    }
}

@RequiresApi(31)
private fun backdropEffect(tier: Int, width: Float, height: Float, blur: Float, acrylic: Boolean): RenderEffect {
    val softened = android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP)
    if (tier < 2 || acrylic) return softened.asComposeRenderEffect()
    return refractedEffect(width, height, softened).asComposeRenderEffect()
}

@RequiresApi(33)
private fun refractedEffect(width: Float, height: Float, softened: android.graphics.RenderEffect): android.graphics.RenderEffect {
    val shader = RuntimeShader("""
        uniform shader backdrop;
        uniform float2 bounds;
        half4 main(float2 p) {
            float2 centre = bounds * 0.5;
            float radius = min(centre.x, centre.y);
            float2 q = abs(p - centre) - (centre - radius);
            float distance = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
            float lens = exp(-abs(distance + 2.0) * 0.16);
            float2 direction = (p - centre) / max(length(p - centre), 1.0);
            float2 sampleAt = clamp(p - direction * lens * 7.0, float2(0.0), bounds);
            half4 sampled = backdrop.eval(sampleAt);
            float rim = exp(-abs(distance + 1.0) * 0.75);
            return half4(clamp(sampled.rgb + half3(half(rim * .10)), 0.0, 1.0), sampled.a);
        }
    """).apply { setFloatUniform("bounds", width, height) }
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "backdrop"), softened)
}
