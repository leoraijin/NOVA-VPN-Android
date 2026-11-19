package com.nova.vpn.android

import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhysicalNetworkMonitorTest {
    @Suppress("DEPRECATION")
    @Test fun dnsAndMtuUpdatesPreserveTheActiveConnection() {
        val connectivity = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(connectivity)
        shadow.clearAllNetworks()
        val network = ShadowNetwork.newInstance(701)
        val wifi = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true)
        shadow.addNetwork(network, wifi)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        shadow.setNetworkCapabilities(network, caps)

        fun links(name: String) = LinkProperties().apply {
            interfaceName = name
            val constructor = LinkAddress::class.java.getDeclaredConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
            val address = constructor.newInstance(InetAddress.getByName("192.0.2.4"), 24)
            LinkProperties::class.java.getDeclaredMethod("addLinkAddress", LinkAddress::class.java).invoke(this, address)
        }
        shadow.setLinkProperties(network, links("wlan-fixture"))
        val coordinator = ConnectionCoordinator()
        val monitor = PhysicalNetworkMonitor(connectivity) {
            coordinator.networkChanged(it.signature, it.network != null)
        }
        try {
            monitor.start()
            val active = coordinator.connect()
            assertTrue(coordinator.succeeded(active))
            val callback = shadow.networkCallbacks.single()
            val updated = links("wlan-fixture")
            LinkProperties::class.java.getMethod("setDnsServers", Collection::class.java)
                .invoke(updated, listOf(InetAddress.getByName("192.0.2.53")))
            LinkProperties::class.java.getMethod("setMtu", Int::class.javaPrimitiveType).invoke(updated, 1420)
            callback.onLinkPropertiesChanged(network, updated)

            assertEquals(active, coordinator.view().ticket)
            assertEquals(ConnectionCoordinator.Phase.ACTIVE, coordinator.view().phase)

            val replacement = links("different-interface")
            callback.onLinkPropertiesChanged(network, replacement)
            assertNotEquals(active, coordinator.view().ticket)
            assertEquals(ConnectionCoordinator.Phase.RECOVERING, coordinator.view().phase)
        } finally {
            monitor.close()
            shadow.clearAllNetworks()
        }
    }
}
