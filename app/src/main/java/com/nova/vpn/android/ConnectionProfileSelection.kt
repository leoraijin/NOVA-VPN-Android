package com.nova.vpn.android

/** Presentation identity only; never selects or changes an exit in the native policy. */
internal object ConnectionProfileSelection {
    fun name(state: VpnUiState, profile: com.nova.vpn.android.data.ImportedProfile?, idle: String): String =
        profile?.displayName ?: if (state.connected || state.recoveryStatus != null)
            "Активный сервер отсутствует в списке" else idle

    fun id(state: VpnUiState, selectedId: String?, defaultId: String?): String? {
        // Never label a running/recovering tunnel with an unrelated draft or fallback profile.
        if (state.connected || state.recoveryStatus != null || state.appliedConfiguration != null) {
            return state.appliedConfiguration?.profileId
        }
        return defaultId ?: selectedId?.takeIf(String::isNotBlank)
    }
}
