package com.nova.vpn.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.nova.vpn.android.data.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (max(x, y) + .05f) / (min(x, y) + .05f)
    }
    @Test fun normalTextMeetsContrastAcrossAllTenSchemes() {
        for (style in DesignStyle.entries) for (dark in listOf(false, true)) {
            val c = designColorScheme(style, RuntimeEnvironment.getApplication(), dark)
            val pairs = mapOf(
                "surface" to (c.onSurface to c.surface),
                "background" to (c.onBackground to c.background),
                "secondary text" to (c.onSurfaceVariant to c.surface),
                "primary button" to (c.onPrimary to c.primary),
                "selection" to (c.onPrimaryContainer to c.primaryContainer),
                "status" to (c.onSecondaryContainer to c.secondaryContainer),
                "error" to (c.error to c.surface),
                "error container" to (c.onErrorContainer to c.errorContainer)
            )
            pairs.forEach { (role, pair) ->
                val ratio = contrast(pair.first, pair.second)
                assertTrue("$style dark=$dark $role contrast=$ratio", ratio >= 4.5f)
            }
        }
    }
}
