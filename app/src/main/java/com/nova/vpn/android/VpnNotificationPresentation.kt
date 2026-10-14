package com.nova.vpn.android

internal data class VpnNotificationContent(val title: String, val text: String)

internal object VpnNotificationPresentation {
    fun create(profileName: String, status: String): VpnNotificationContent {
        val name = profileName.trim().replace(Regex("\\s+"), " ")
            .take(48).ifBlank { "Выбранная конфигурация" }
        return VpnNotificationContent(
            title = "NOVA VPN · $name",
            text = status.trim().ifBlank { "VPN работает" }
        )
    }
}

