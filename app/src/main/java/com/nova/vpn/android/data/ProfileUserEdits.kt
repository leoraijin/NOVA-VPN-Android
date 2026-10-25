package com.nova.vpn.android.data

/** Edits are applied to the latest vault state inside its writer lock. */
internal object ProfileUserEdits {
    fun rename(profiles: List<ImportedProfile>, id: String, input: String): List<ImportedProfile> {
        val name = input.trim().takeIf { it.isNotEmpty() }
        require(name == null || (name.length <= 96 && name.none { it.isISOControl() })) { "Название: до 96 символов без переводов строк" }
        return profiles.map { if (it.id == id) it.copy(userSettings = it.userSettings.copy(personalName = name)) else it }
    }

    fun move(profiles: List<ImportedProfile>, id: String, direction: Int): List<ImportedProfile> {
        require(direction == -1 || direction == 1)
        val ordered = profiles.sortedBy { it.userSettings.order }.toMutableList()
        val index = ordered.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target !in ordered.indices) return profiles
        val profile = ordered.removeAt(index)
        ordered.add(target, profile)
        val positions = ordered.mapIndexed { position, item -> item.id to position.toLong() }.toMap()
        return profiles.map { it.copy(userSettings = it.userSettings.copy(order = positions.getValue(it.id))) }
    }
}
