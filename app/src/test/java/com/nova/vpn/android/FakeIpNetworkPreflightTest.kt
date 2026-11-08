package com.nova.vpn.android

import com.nova.vpn.android.data.*
import java.net.InetAddress
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class FakeIpNetworkPreflightTest {
    private fun config(v4:String="198.19.0.0/16",v6:String="fc00::/18")=JSONObject().put("dns",JSONObject().put("servers",JSONArray().put(JSONObject().put("type","fakeip").put("inet4_range",v4).put("inet6_range",v6)))).toString()
    private fun check(json:String,vararg networks:String)=FakeIpNetworkPreflight.validate(json,networks.map {FakeIpNetworkPreflight.Prefix(it)})
    private fun refuses(action:()->Unit){try{action();fail("accepted invalid/conflicting input")}catch(_:IllegalArgumentException){}catch(_:java.net.UnknownHostException){}}
    @Test fun actualGeneratedPoolAvoidsLabAndKeepsTunAndCacheExplicit() {
        val p=ImportedProfile(id="fr",name="FR",protocol="socks",server="127.0.0.1",port=18001,outboundJson="""{"type":"socks","server":"127.0.0.1","server_port":18001}""")
        val route=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",emptyList()))
        val json=SingBoxConfigFactory.build(p,route,"com.nova.vpn.android",listOf(p),"/private/network-preflight.db")
        val root=JSONObject(json);val servers=root.getJSONObject("dns").getJSONArray("servers")
        val pool=(0 until servers.length()).map(servers::getJSONObject).single {it.getString("type")=="fakeip"}
        assertEquals("198.19.0.0/16",pool.getString("inet4_range"));check(json,"198.18.255.254/32")
        refuses {check(json,"198.19.255.254/32")};check(json,"fdfe:dcba:9876::1/126")
        assertEquals(json,root.toString())
    }
    @Test fun overlapHandlesBothFamiliesAndAdjacentNetworks() {
        fun overlaps(a:String,b:String)=FakeIpNetworkPreflight.Prefix(a).overlaps(FakeIpNetworkPreflight.Prefix(b))
        assertTrue(overlaps("198.19.0.0/16","198.19.4.5/32"));assertTrue(overlaps("198.19.0.0/16","198.18.0.0/15"))
        assertFalse(overlaps("198.19.0.0/16","198.18.255.254/32"));assertFalse(overlaps("fc00::/18","fd00::/8"))
        assertTrue(overlaps("fc00::/18","fc00:1234::/32"));assertFalse(overlaps("fc00::/18","198.19.0.0/16"))
    }
    @Test fun ipv4MatchesIndependentIntervalOracle() {
        val random=java.util.Random(71903)
        fun address(value:Long)=(3 downTo 0).joinToString("."){((value ushr (it*8)) and 255).toString()}
        repeat(2000) {
            val a=random.nextInt().toLong() and 0xffffffffL;val b=random.nextInt().toLong() and 0xffffffffL
            val ap=random.nextInt(33);val bp=random.nextInt(33);val sizeA=1L shl (32-ap);val sizeB=1L shl (32-bp)
            val startA=a/sizeA*sizeA;val startB=b/sizeB*sizeB
            assertEquals(startA<=startB+sizeB-1 && startB<=startA+sizeA-1,FakeIpNetworkPreflight.Prefix(address(a)+"/"+ap).overlaps(FakeIpNetworkPreflight.Prefix(address(b)+"/"+bp)))
        }
    }
    @Test fun ipv6MatchesIndependentBitStringOracle() {
        val random=java.util.Random(71904)
        fun bits(bytes:ByteArray)=bytes.joinToString(""){(it.toInt() and 255).toString(2).padStart(8,'0')}
        repeat(500) {
            val a=ByteArray(16).also(random::nextBytes);val b=ByteArray(16).also(random::nextBytes)
            if(it%2==0)System.arraycopy(a,0,b,0,random.nextInt(17))
            val ap=random.nextInt(129);val bp=random.nextInt(129);val min=minOf(ap,bp)
            assertEquals(bits(a).take(min)==bits(b).take(min),FakeIpNetworkPreflight.Prefix(InetAddress.getByAddress(a).hostAddress+"/"+ap).overlaps(FakeIpNetworkPreflight.Prefix(InetAddress.getByAddress(b).hostAddress+"/"+bp)))
        }
    }
    @Test fun invalidNumericPrefixesNeverResolveHostnames() {
        listOf("example.org/24","1.2.3/24","1.2.3.999/24","::/129","::ffff:198.19.0.1/128","1.2.3.4/-1","1.2.3.4/33","::%wifi/64","/0","1.2.3.4/24/0").forEach {value->refuses {FakeIpNetworkPreflight.Prefix(value)}}
    }
    @Test fun fakeIpFamiliesAndBoundedSnapshotsAreValidatedWithoutInbounds() {
        refuses {check(config(),"198.19.1.1/32")};refuses {check(config(),"fc00::/7")};check(config(),"192.168.1.1/24","fd00::/8")
        refuses {check(config("fc00::/18"))};refuses {check(config(v6="198.19.0.0/16"))}
        refuses {FakeIpNetworkPreflight.validate(config(),List(8193){FakeIpNetworkPreflight.Prefix("192.168.0.1/24")})}
        check(config("198.18.0.0/15"),"192.168.0.1/24");refuses {check(config("198.18.0.0/15"),"198.18.255.254/32")}
    }
    @Test fun ordinaryDnsDoesNotAcquireSyntheticPoolRestrictions() {
        check("{}","0.0.0.0/0");check("""{"dns":{"servers":[{"type":"local","tag":"physical"}]}}""","fc00::/7")
    }
}
