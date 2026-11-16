package com.nova.vpn.android

internal data class VpnExitLine(val title: String, val status: String, val failed: Boolean = false)
private val exitCheckClock = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
internal fun VpnUiState.exitLines(primaryName: String): List<VpnExitLine> {
    val primary = appliedConfiguration?.profileId
    val ids = (listOfNotNull(primary) + exitChecks.keys + exitNames.keys).distinct()
    fun status(check: ExitCheckState?): String = when {
        recoveryStatus != null -> recoveryText().orEmpty()
        connecting -> "Подключается"
        !connected -> "Отключён"
        check == null -> when {
            trafficChecking -> "Проверяется"
            trafficVerified -> "Работает · HTTPS подтверждён"
            trafficCheckFailed -> "Нет ответа на HTTPS-проверку"
            else -> "Трафик ещё не проверен"
        }
        else -> when(check.status) {
            ExitCheckStatus.QUEUED -> "Ожидает проверки"
            ExitCheckStatus.CHECKING -> "Проверяется"
            ExitCheckStatus.VERIFIED -> "Работает · HTTPS подтверждён" + (check.responseMillis?.let { " · $it мс" } ?: "")
            ExitCheckStatus.FAILED -> "Нет ответа на HTTPS-проверку"
            ExitCheckStatus.UNAVAILABLE -> "Конфиг недоступен"
            ExitCheckStatus.INCOMPATIBLE -> "Конфиг несовместим"
            ExitCheckStatus.UNSUPPORTED -> "Проверка не поддерживается"
        }
    }
    if (ids.isEmpty()) return listOf(VpnExitLine("Основной VPN · $primaryName", status(null)))
    return ids.map { id ->
        val slot = appliedConfiguration?.policy?.vpnSlots?.entries?.firstOrNull { it.value == id }?.key
        val label = if(id == primary) "Основной VPN" else if(slot != null) "VPN-$slot" else if(ids.size == 2) "VPN-2" else "Дополнительный VPN"
        val name = exitNames[id] ?: if(id == primary) primaryName else "Сервер ${id.takeLast(6)}"
        val check = exitChecks[id]
        val checked = check?.checkedAtMillis?.takeIf { connected && recoveryStatus == null }?.let {
            val local = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
            " · проверено в ${exitCheckClock.format(local)}"
        }.orEmpty()
        VpnExitLine("$label · $name", status(check) + checked, check?.status in setOf(ExitCheckStatus.FAILED, ExitCheckStatus.UNAVAILABLE, ExitCheckStatus.INCOMPATIBLE))
    }
}
