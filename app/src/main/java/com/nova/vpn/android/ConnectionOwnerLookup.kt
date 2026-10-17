package com.nova.vpn.android

import java.net.InetAddress
import java.net.InetSocketAddress

/** Numeric original TUN tuples only. Never resolve a hostname or cache a socket UID. */
internal object ConnectionOwnerLookup {
    fun endpoint(value: String): InetSocketAddress {
        val host: String
        val port: String
        if (value.startsWith("[")) {
            val end = value.indexOf("]:")
            require(end > 1)
            host = value.substring(1, end)
            port = value.substring(end + 2)
            require(host.contains(':') && host.all { it in "0123456789abcdefABCDEF:." })
        } else {
            require(value.count { it == ':' } == 1)
            host = value.substringBefore(':')
            port = value.substringAfter(':')
            val octets = host.split('.')
            require(octets.size == 4 && octets.all {
                it.isNotEmpty() && it.length <= 3 && it.all { c -> c in '0'..'9' } &&
                    it.toInt() in 0..255
            })
        }
        require(port.isNotEmpty() && port.all { it in '0'..'9' })
        val number = port.toInt()
        require(number in 1..65535)
        return InetSocketAddress(InetAddress.getByName(host), number)
    }

    fun resolve(protocol: Int, source: String, destination: String,
                query: (Int, InetSocketAddress, InetSocketAddress) -> Int): Long = runCatching {
        require(protocol == 6 || protocol == 17)
        val local = endpoint(source)
        val remote = endpoint(destination)
        require(local.address.address.size == remote.address.address.size)
        query(protocol, local, remote).takeIf { it >= 0 }?.toLong() ?: -1L
    }.getOrDefault(-1L)
}
