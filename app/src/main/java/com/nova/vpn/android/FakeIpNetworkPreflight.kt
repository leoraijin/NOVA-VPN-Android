package com.nova.vpn.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress
import org.json.JSONObject

/** Read-only preflight; addresses stay in memory and never enter diagnostics. */
internal object FakeIpNetworkPreflight {
    class Conflict : IllegalArgumentException("Диапазон FakeIP пересекается с подключённой сетью. Измените конфликтующую сеть и повторите подключение.")
    class Prefix(text: String) {
        private val bytes: ByteArray
        private val bits: Int
        val ipv6: Boolean
        init {
            val parts=text.split('/')
            require(parts.size==2 && parts[1].matches(Regex("[0-9]{1,3}"))) { "Некорректная подсеть" }
            val host=parts[0];ipv6=host.contains(':')
            require(if(ipv6) host.matches(Regex("[0-9a-fA-F:.]+")) else
                host.split('.').let { it.size==4 && it.all { octet -> octet.matches(Regex("[0-9]{1,3}")) && octet.toInt() in 0..255 } }) { "Ожидается числовая подсеть" }
            bytes=InetAddress.getByName(host).address
            require(bytes.size==(if(ipv6)16 else 4)) { "Некорректное семейство подсети" }
            bits=parts[1].toInt();require(bits in 0..bytes.size*8) { "Некорректный префикс подсети" }
        }
        fun overlaps(other: Prefix): Boolean {
            if(bytes.size!=other.bytes.size)return false
            var remaining=minOf(bits,other.bits);var index=0
            while(remaining>0) {
                val mask=(255 shl (8-minOf(8,remaining))) and 255
                if((bytes[index].toInt() and mask)!=(other.bytes[index].toInt() and mask))return false
                remaining-=8;index++
            }
            return true
        }
    }
    fun capture(context: Context): List<Prefix> {
        val connectivity=requireNotNull(context.getSystemService(ConnectivityManager::class.java)) { "Недоступно состояние сети" }
        val result=ArrayList<Prefix>()
        fun append(text: String) { require(result.size<8192) { "Слишком много сетевых маршрутов" };result.add(Prefix(text)) }
        connectivity.allNetworks.forEach { network ->
            val caps=connectivity.getNetworkCapabilities(network) ?: return@forEach
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN))return@forEach
            val links=requireNotNull(connectivity.getLinkProperties(network)) { "Сеть изменилась во время проверки. Повторите подключение." }
            links.linkAddresses.forEach { address -> append(address.address.hostAddress.orEmpty().substringBefore('%')+"/"+address.prefixLength) }
            links.routes.forEach { route ->
                val destination=route.destination
                // Default gateways do not reserve the whole Internet.
                if(destination.prefixLength!=0)append(destination.address.hostAddress.orEmpty().substringBefore('%')+"/"+destination.prefixLength)
            }
        }
        return result.toList()
    }
    fun validate(config: String, connected: List<Prefix>) {
        require(connected.size<=8192) { "Слишком много сетевых маршрутов" }
        val servers=JSONObject(config).optJSONObject("dns")?.optJSONArray("servers") ?: return
        repeat(servers.length()) { index ->
            val server=servers.optJSONObject(index) ?: return@repeat
            if(server.optString("type")!="fakeip")return@repeat
            listOf("inet4_range","inet6_range").forEach { key ->
                if(server.has(key)) {
                    val proposed=Prefix(server.getString(key))
                    require(proposed.ipv6==(key=="inet6_range")) { "Некорректное семейство FakeIP" }
                    if(connected.any(proposed::overlaps))throw Conflict()
                }
            }
        }
    }
}
