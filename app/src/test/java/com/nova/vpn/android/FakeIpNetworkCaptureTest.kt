package com.nova.vpn.android

import android.content.Context
import android.net.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class FakeIpNetworkCaptureTest {
    @Suppress("DEPRECATION")
    @Test fun capturesPhysicalRoutesButExcludesVpnAndDefaultGateways() {
        val context=RuntimeEnvironment.getApplication();val cm=context.getSystemService(ConnectivityManager::class.java);val shadow=shadowOf(cm);shadow.clearAllNetworks()
        fun add(id:Int,vpn:Boolean,address:String,extra:String):Network {
            val network=ShadowNetwork.newInstance(id);val info=org.robolectric.shadows.ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_WIFI,0,true,true)
            shadow.addNetwork(network,info)
            val caps=ShadowNetworkCapabilities.newInstance();shadowOf(caps).addTransportType(if(vpn)NetworkCapabilities.TRANSPORT_VPN else NetworkCapabilities.TRANSPORT_WIFI)
            if(!vpn)shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            shadow.setNetworkCapabilities(network,caps)
            val links=LinkProperties();links.interfaceName=if(vpn)"tun-fixture" else "wifi-fixture"
            fun prefix(value:String)=IpPrefix(java.net.InetAddress.getByName(value.substringBefore('/')),value.substringAfter('/').toInt())
            val link=LinkAddress::class.java.getDeclaredConstructor(java.net.InetAddress::class.java,Int::class.javaPrimitiveType).newInstance(java.net.InetAddress.getByName(address.substringBefore('/')),address.substringAfter('/').toInt())
            LinkProperties::class.java.getDeclaredMethod("addLinkAddress",LinkAddress::class.java).invoke(links,link)
            val constructor=RouteInfo::class.java.getDeclaredConstructor(IpPrefix::class.java,java.net.InetAddress::class.java,String::class.java)
            links.addRoute(constructor.newInstance(prefix("0.0.0.0/0"),null,null));links.addRoute(constructor.newInstance(prefix(extra),null,null));shadow.setLinkProperties(network,links)
            return network
        }
        add(501,false,"192.168.4.2/24","198.19.7.8/32");add(502,true,"198.19.0.1/16","fc00::/18")
        try {
            val result=FakeIpNetworkPreflight.capture(context);assertEquals(2,result.size)
            assertTrue(result.any {it.overlaps(FakeIpNetworkPreflight.Prefix("198.19.7.8/32"))})
            assertFalse(result.any {it.overlaps(FakeIpNetworkPreflight.Prefix("203.0.113.1/32"))})
            assertFalse(result.any {it.overlaps(FakeIpNetworkPreflight.Prefix("fc00::/18"))})
        } finally {shadow.clearAllNetworks()}
    }
    @Test fun emptyInventoryRemainsEditableOffline() {
        val context=RuntimeEnvironment.getApplication();val shadow=shadowOf(context.getSystemService(ConnectivityManager::class.java));shadow.clearAllNetworks()
        assertTrue(FakeIpNetworkPreflight.capture(context).isEmpty())
    }
}

