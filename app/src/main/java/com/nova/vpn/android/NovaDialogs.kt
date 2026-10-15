@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.nova.vpn.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import com.nova.vpn.android.data.ThemeMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nova.vpn.android.data.DesignStyle

/** Style-specific dialogs with platform measurement for keyboard-safe action areas. */
@Composable
internal fun NovaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    if (LocalDesignPreset.current == DesignStyle.IOS_LIQUID_GLASS) {
        ModalBottomSheet(onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)) {
            val sheetView = LocalView.current
            val sheetDark = LocalThemeMode.current == ThemeMode.DARK
            val sheetSurface = MaterialTheme.colorScheme.surface
            SideEffect {
                (sheetView.parent as? DialogWindowProvider)?.window?.let { window ->
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = sheetSurface.toArgb()
                    WindowCompat.getInsetsController(window, sheetView).apply {
                        isAppearanceLightStatusBars = !sheetDark
                        isAppearanceLightNavigationBars = !sheetDark
                    }
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .85f).dp).padding(horizontal = 20.dp).imePadding()) {
                title?.let { ProvideTextStyle(MaterialTheme.typography.titleLarge) { it() }; Spacer(Modifier.height(20.dp)) }
                text?.let { Box(Modifier.weight(1f, fill = false)) { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() } }; Spacer(Modifier.height(16.dp)) }
                FlowRow(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) { dismissButton?.invoke(); confirmButton() }
            }
        }
        return
    }
    if (LocalDesignPreset.current == DesignStyle.ONE_UI) {
        AlertDialog(onDismissRequest = onDismissRequest,
            modifier = Modifier.padding(bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            confirmButton = confirmButton, dismissButton = dismissButton,
            title = {
                val view = LocalView.current
                SideEffect {
                    (view.parent as? DialogWindowProvider)?.window?.let { window ->
                        window.setGravity(android.view.Gravity.BOTTOM)
                        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    }
                }
                title?.invoke()
            }, text = text)
        return
    }
    if (LocalDesignPreset.current != DesignStyle.WINDOWS_11) {
        AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
            dismissButton = dismissButton, title = title, text = text)
        return
    }
    AlertDialog(onDismissRequest = onDismissRequest,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = {
            val view = LocalView.current
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            ProvideTextStyle(MaterialTheme.typography.titleLarge) { title?.invoke() }
        },
        text = text,
        confirmButton = {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                FlowRow(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        })
}
