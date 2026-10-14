package com.nova.vpn.android

/** SystemUI owns tile colors; NOVA provides its brand, readable status and accessibility text. */
internal data class NovaTilePresentation(val active: Boolean, val subtitle: String, val description: String) {
    companion object {
        fun from(state: VpnUiState, profileName: String?): NovaTilePresentation {
            val active = state.connected || state.connecting
            val status = when {
                state.connecting -> "Подключение…"
                state.connected && state.trafficVerified -> "VPN включён"
                state.connected && state.trafficChecking -> "Проверка трафика…"
                state.connected && state.trafficCheckFailed -> "Туннель активен · проверка не подтверждена"
                state.connected -> "Туннель включён"
                state.error != null -> "Ошибка подключения"
                profileName == null -> "Добавьте конфигурацию"
                else -> "VPN выключен"
            }
            val subtitle = if (profileName.isNullOrBlank()) status else "$status · ${profileName.take(40)}"
            return NovaTilePresentation(active, subtitle, "NOVA VPN. $subtitle")
        }
    }
}
