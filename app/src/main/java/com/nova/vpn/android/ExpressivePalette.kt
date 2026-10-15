package com.nova.vpn.android

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.pow

/** Hue changes independently of light/dark mode. Tone targets keep text contrast stable. */
internal fun expressiveColorScheme(hue: Float, dark: Boolean): ColorScheme {
    fun tone(h: Float, saturation: Float, t: Float): Color {
        val y = if (t > 8f) ((t + 16f) / 116f).pow(3) else t / 903.3f
        var lo = 0f; var hi = 1f
        repeat(14) {
            val mid = (lo + hi) / 2
            val value = ColorUtils.HSLToColor(floatArrayOf((h % 360f + 360f) % 360f, saturation, mid))
            if (ColorUtils.calculateLuminance(value) < y) lo = mid else hi = mid
        }
        return Color(ColorUtils.HSLToColor(floatArrayOf((h % 360f + 360f) % 360f, saturation, (lo + hi) / 2)))
    }
    fun a(t: Float) = tone(hue, .72f, t)
    fun b(t: Float) = tone(hue, .32f, t)
    fun c(t: Float) = tone(hue, .55f, t)
    fun n(t: Float) = tone(hue, .12f, t)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = a(if (dark) 80f else 40f), onPrimary = a(if (dark) 20f else 100f),
        primaryContainer = a(if (dark) 30f else 90f), onPrimaryContainer = a(if (dark) 90f else 10f),
        secondary = b(if (dark) 80f else 40f), onSecondary = b(if (dark) 20f else 100f),
        secondaryContainer = b(if (dark) 30f else 90f), onSecondaryContainer = b(if (dark) 90f else 10f),
        tertiary = c(if (dark) 80f else 40f), onTertiary = c(if (dark) 20f else 100f),
        tertiaryContainer = c(if (dark) 30f else 90f), onTertiaryContainer = c(if (dark) 90f else 10f),
        background = n(if (dark) 6f else 98f), onBackground = n(if (dark) 90f else 10f),
        surface = n(if (dark) 6f else 98f), onSurface = n(if (dark) 90f else 10f),
        surfaceVariant = n(if (dark) 30f else 90f), onSurfaceVariant = n(if (dark) 80f else 30f),
        surfaceContainerLowest = n(if (dark) 4f else 100f), surfaceContainerLow = n(if (dark) 10f else 96f),
        surfaceContainer = n(if (dark) 12f else 94f), surfaceContainerHigh = n(if (dark) 17f else 92f),
        surfaceContainerHighest = n(if (dark) 22f else 90f),
        surfaceBright = n(if (dark) 24f else 98f), surfaceDim = n(if (dark) 6f else 87f),
        outline = n(if (dark) 60f else 50f), outlineVariant = n(if (dark) 30f else 80f), surfaceTint = a(if (dark) 80f else 40f))
}
