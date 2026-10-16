package com.nova.vpn.android.data

/** Persist explicit identifiers so enum names and ordering can evolve independently. */
enum class VpnEngine(val id: String) {
    SING_BOX("sing_box"),
    REALITY_CORE("reality_core");

    companion object {
        fun fromId(id: String?): VpnEngine = entries.firstOrNull { it.id == id } ?: SING_BOX
    }
}
