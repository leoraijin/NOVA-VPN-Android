package com.nova.vpn.android

/** Suppress redundant platform events without hiding a new network or a newly ready interface. */
internal class InterfacePublicationGate {
    private data class Snapshot(val network: Long?, val name: String, val index: Int)
    private var published: Snapshot? = null

    @Synchronized
    fun publish(network: Long?, name: String, index: Int, update: () -> Unit) {
        val next = Snapshot(network, name, index)
        if (published == next) return
        update()
        published = next
    }
}
