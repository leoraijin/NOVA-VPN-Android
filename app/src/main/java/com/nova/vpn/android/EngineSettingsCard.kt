package com.nova.vpn.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.vpn.android.data.AppPreferencesStore
import com.nova.vpn.android.data.VpnEngine

@Composable
internal fun EngineSettingsCard() {
    val context = LocalContext.current
    val preferences = remember(context) { AppPreferencesStore(context.applicationContext) }
    var selected by remember(preferences) { mutableStateOf(preferences.vpnEngine()) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val state by VpnRuntime.state.collectAsStateWithLifecycle()
    val locked = state.connected || state.connecting

    SoftCard {
        Text("VPN-движок", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            VpnEngine.entries.forEach { engine ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = selected == engine,
                        enabled = !locked,
                        role = Role.RadioButton,
                        onClick = {
                            val current = VpnRuntime.state.value
                            if (!current.connected && !current.connecting) {
                                runCatching { preferences.setVpnEngine(engine) }
                                    .onSuccess { selected = engine; saveError = null }
                                    .onFailure { saveError = "Не удалось сохранить VPN-движок" }
                            }
                        }
                    ).heightIn(min = 48.dp).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(selected = selected == engine, onClick = null, enabled = !locked)
                    Text(
                        if (engine == VpnEngine.SING_BOX) "sing-box · основной" else "Reality Core · экспериментальный",
                        color = if (locked) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                            else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Text(
            "Reality Core: только VLESS/Trojan. Смешанные правила приложений и сайтов имеют ограничения; " +
                "полные конфигурации могут не поддерживаться. Сервер и правила меняются после отключения VPN.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (locked) Text(
            "Чтобы сменить движок, отключите VPN и дождитесь завершения подключения.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
