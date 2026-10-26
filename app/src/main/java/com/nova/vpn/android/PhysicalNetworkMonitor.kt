package com.nova.vpn.android

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.security.MessageDigest

internal data class PhysicalNetworkSnapshot(val network: Network?, val signature: String?)

/** One source of physical-network readiness. onAvailable alone never means ready. */
internal class PhysicalNetworkMonitor(private val connectivity: ConnectivityManager,
    private val publish: (PhysicalNetworkSnapshot) -> Unit) : AutoCloseable {
    private data class Links(val name: String?, val addresses: List<String>, val dns: List<String>, val routes: List<String>, val mtu: Int) {
        companion object { fun from(value: LinkProperties) = Links(value.interfaceName, value.linkAddresses.map { it.toString() },
            value.dnsServers.map { it.hostAddress.orEmpty() }, value.routes.map { it.toString() }, value.mtu) }
    }
    private data class Entry(val caps: NetworkCapabilities? = null, val links: Links? = null)
    private val entries = linkedMapOf<Network, Entry>()
    private val lock = Any()
    private var registered = false
    private var closed = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = synchronized(lock) {
            if (!closed) { entries.putIfAbsent(network, Entry()); emit() }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = synchronized(lock) {
            if (!closed) { entries[network] = (entries[network] ?: Entry()).copy(caps = NetworkCapabilities(capabilities)); emit() }
        }
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = synchronized(lock) {
            if (!closed) { entries[network] = (entries[network] ?: Entry()).copy(links = Links.from(properties)); emit() }
        }
        override fun onLost(network: Network) = synchronized(lock) { if (!closed) { entries.remove(network); emit() } }
    }

    fun start() {
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), callback)
        synchronized(lock) {
            registered = true
            // Synchronous snapshot is only for initial seeding, outside any NetworkCallback.
            connectivity.allNetworks.forEach { network ->
                val caps = connectivity.getNetworkCapabilities(network)
                val links = connectivity.getLinkProperties(network)
                if (caps != null && links != null) {
                    val existing = entries[network]
                    // onAvailable may already have inserted a partial entry.
                    // Seed missing fields without overwriting newer callbacks.
                    entries[network] = Entry(existing?.caps ?: caps, existing?.links ?: Links.from(links))
                }
            }
            emit()
        }
    }

    private fun emit() {
        val chosen = entries.entries.filter { (_, entry) ->
            val caps = entry.caps; val links = entry.links
            caps != null && links != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) &&
                !links.name.isNullOrBlank() && links.addresses.isNotEmpty()
        }.maxWithOrNull(compareBy<Map.Entry<Network, Entry>> {
            if (it.value.caps!!.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 100 else 0
        }.thenBy { when {
            it.value.caps!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 3
            it.value.caps!!.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 2
            it.value.caps!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
            else -> 0
        } }.thenBy { it.key.networkHandle })
        if (chosen == null) { publish(PhysicalNetworkSnapshot(null, null)); return }
        val links = chosen.value.links!!
        val material = listOf(chosen.key.networkHandle.toString(), links.name.orEmpty(),
            links.addresses.sorted().joinToString(), links.dns.sorted().joinToString(),
            links.routes.sorted().joinToString(), links.mtu.toString()).joinToString("\n")
        // The signature stays in memory; interface names, addresses and DNS never enter diagnostics.
        val signature = MessageDigest.getInstance("SHA-256").digest(material.toByteArray()).joinToString("") { "%02x".format(it) }
        publish(PhysicalNetworkSnapshot(chosen.key, signature))
    }

    override fun close() = synchronized(lock) {
        closed = true
        if (registered) runCatching { connectivity.unregisterNetworkCallback(callback) }
        registered = false; entries.clear()
    }
}
