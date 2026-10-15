package com.nova.vpn.android

import android.os.Build
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.vpn.android.data.*

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

internal val LocalDesignPreset = compositionLocalOf { DesignStyle.IOS_LIQUID_GLASS }
internal val LocalGlassFrost = compositionLocalOf { .65f }
internal val LocalMaterialHue = compositionLocalOf { 275f }
internal val LocalThemeMode = compositionLocalOf { ThemeMode.LIGHT }

@Composable
internal fun NovaTheme(appearance: Appearance, colorScheme: ColorScheme, content: @Composable () -> Unit) {
    val sceneLayer = rememberGraphicsLayer()
    val contentLayer = rememberGraphicsLayer()
    val backdrop = remember(sceneLayer, contentLayer) { NovaBackdrop(sceneLayer, contentLayer) }
    val view = LocalView.current
    SideEffect {
        val activity = view.context.hostActivity()
        (activity as? ComponentActivity)?.let {
            val barStyle = if (appearance.isDark) SystemBarStyle.dark(colorScheme.background.toArgb())
                else SystemBarStyle.light(colorScheme.background.toArgb(), colorScheme.background.toArgb())
            it.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        }
        activity?.window?.let { window ->
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !appearance.isDark
                isAppearanceLightNavigationBars = !appearance.isDark
            }
        }
    }
    DisposableEffect(view, appearance.isDark) {
        fun syncIcons() {
            view.context.hostActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !appearance.isDark
                    isAppearanceLightNavigationBars = !appearance.isDark
                }
            }
        }
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) syncIcons() }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        view.post { syncIcons() }
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    val radius = appearance.style.cardRadius.dp
    val windows = appearance.style == DesignStyle.WINDOWS_11
    val oneUi = appearance.style == DesignStyle.ONE_UI
    val ios = appearance.style == DesignStyle.IOS_LIQUID_GLASS
    val material = appearance.style == DesignStyle.MATERIAL_YOU
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = if (oneUi || ios) 34.sp else 30.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = if (windows) 24.sp else if (material) 32.sp else 28.sp, lineHeight = if (material) 40.sp else androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight = if (material) FontWeight.Normal else FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    )
    CompositionLocalProvider(LocalDesignPreset provides appearance.style, LocalThemeMode provides appearance.mode, LocalGlassFrost provides appearance.glassFrost, LocalMaterialHue provides appearance.materialHue, LocalNovaBackdrop provides backdrop) {
        MaterialTheme(colorScheme = colorScheme, typography = typography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(if (windows) 4.dp else 8.dp),
                small = RoundedCornerShape(if (windows) 4.dp else 12.dp),
                medium = RoundedCornerShape(if (windows) 8.dp else 20.dp),
                large = RoundedCornerShape(radius), extraLarge = RoundedCornerShape(radius)
            ), content = content)
    }
}

internal fun designColorScheme(preset: DesignStyle, context: android.content.Context, systemDark: Boolean, materialHue: Float = 275f): androidx.compose.material3.ColorScheme = when (preset) {
    DesignStyle.MATERIAL_YOU -> expressiveColorScheme(materialHue, systemDark)
    DesignStyle.IOS_LIQUID_GLASS -> if (systemDark) darkColorScheme(
        primary = Color(0xFF8DBBFF), onPrimary = Color(0xFF002C59),
        primaryContainer = Color(0xFF233E60), onPrimaryContainer = Color(0xFFD9E9FF),
        secondary = Color(0xFF86D3C9), onSecondary = Color(0xFF003B36),
        secondaryContainer = Color(0xFF254640), onSecondaryContainer = Color(0xFFCCF5EC),
        background = Color(0xFF090A0D), onBackground = Color(0xFFE9EDF7),
        surface = Color(0xFF1C1D21), onSurface = Color(0xFFE9EDF7),
        surfaceVariant = Color(0xFF292B30), onSurfaceVariant = Color(0xFFBFC2CC),
        error = Color(0xFFFFB4AB), outline = Color(0xFF76859D)
    ) else lightColorScheme(
        primary = Color(0xFF0065D5), onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E9FD), onPrimaryContainer = Color(0xFF123B61),
        secondary = Color(0xFF208783), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDEEEB),
        background = Color(0xFFF2F2F7), onBackground = Color(0xFF15161A),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF15161A),
        surfaceVariant = Color(0xFFF4F5FB), onSurfaceVariant = Color(0xFF405369),
        error = Color(0xFFC63C49), outline = Color(0xFFB3C2D3)
    )
    DesignStyle.ONE_UI -> if (systemDark) darkColorScheme(
        primary = Color(0xFF9ABEFF), onPrimary = Color(0xFF002F67),
        primaryContainer = Color(0xFF183E77), onPrimaryContainer = Color(0xFFD7E3FF),
        secondary = Color(0xFFA8C8E8), onSecondary = Color(0xFF12324B),
        secondaryContainer = Color(0xFF284861), onSecondaryContainer = Color(0xFFD0E5FA),
        background = Color(0xFF08090B), onBackground = Color(0xFFF3F4F7),
        surface = Color(0xFF17191D), onSurface = Color(0xFFF3F4F7),
        surfaceVariant = Color(0xFF24272E), onSurfaceVariant = Color(0xFFC2C6CF),
        error = Color(0xFFFFB4AB), outline = Color(0xFF8B909B)
    ) else lightColorScheme(
        primary = Color(0xFF225DB8), onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E5FF), onPrimaryContainer = Color(0xFF12346C),
        secondary = Color(0xFF437F68), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2EEE8),
        background = Color(0xFFF3F4F6), onBackground = Color(0xFF191C22),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF191C22),
        surfaceVariant = Color(0xFFECEEF2), onSurfaceVariant = Color(0xFF505660),
        error = Color(0xFFA9344B), outline = Color(0xFF9299A5)
    )
    DesignStyle.WINDOWS_11 -> if (systemDark) darkColorScheme(
        primary = Color(0xFF60CDFF), onPrimary = Color(0xFF00344D),
        primaryContainer = Color(0xFF33465C), onPrimaryContainer = Color(0xFFE5F5FF),
        secondary = Color(0xFF52C9C0), onSecondary = Color(0xFF003B38),
        secondaryContainer = Color(0xFF263E3D),
        background = Color(0xFF202020), onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF2C2C2C), onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF333333), onSurfaceVariant = Color(0xFFBFBFBF),
        error = Color(0xFFF0808C), outline = Color(0xFF4C4C4C)
    ) else lightColorScheme(
        primary = Color(0xFF0067C0), onPrimary = Color.White,
        primaryContainer = Color(0xFFD5E8F7), onPrimaryContainer = Color(0xFF153C5B),
        secondary = Color(0xFF16827D), onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDEDEA),
        background = Color(0xFFF0F0F0), onBackground = Color(0xFF202020),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF202020),
        surfaceVariant = Color(0xFFF5F5F5), onSurfaceVariant = Color(0xFF5C5C5C),
        error = Color(0xFFB23B52), outline = Color(0xFFCCCCCC)
    )
}.let { scheme ->
    if (preset == DesignStyle.MATERIAL_YOU) scheme else scheme.copy(
        surfaceTint = scheme.primary,
        surfaceDim = lerp(scheme.background, scheme.onSurface, .04f),
        surfaceBright = scheme.surface,
        surfaceContainerLowest = scheme.background,
        surfaceContainerLow = scheme.surface,
        surfaceContainer = scheme.surface,
        surfaceContainerHigh = scheme.surfaceVariant,
        surfaceContainerHighest = lerp(scheme.surfaceVariant, scheme.onSurface, .04f),
        outlineVariant = lerp(scheme.surface, scheme.outline, .45f),
        onSecondaryContainer = if (systemDark) Color(0xFFE8F3F5) else Color(0xFF17363E)
    )
}
