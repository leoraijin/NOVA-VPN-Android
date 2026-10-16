package com.nova.vpn.android

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Test-only loopback DNS. No forwarding, secrets or real provider hosts. */
internal class TestDnsFixture : AutoCloseable {
    private val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort
    val transportQueries = AtomicInteger()
    private val worker = thread(isDaemon = true, name = "nova-smoke-dns") {
        val buffer = ByteArray(4096)
        while (!socket.isClosed) runCatching {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val query = packet.data.copyOfRange(packet.offset, packet.offset + packet.length)
            if (query.size < 17 || query[4].toInt() != 0 || query[5].toInt() != 1) return@runCatching
            var end = 12
            val labels = mutableListOf<String>()
            while (end < query.size && query[end].toInt() != 0) {
                val size = query[end].toInt() and 255
                require(size in 1..63 && end + 1 + size < query.size)
                labels += String(query, end + 1, size, Charsets.US_ASCII)
                end += 1 + size
            }
            end++
            require(end + 4 <= query.size)
            val type = (query[end].toInt() and 255) * 256 + (query[end + 1].toInt() and 255)
            val hostMatches = labels.joinToString(".").equals("transport.test", ignoreCase = true)
            if (hostMatches) transportQueries.incrementAndGet()
            end += 4
            val answer = hostMatches && type == 1
            val response = query.copyOfRange(0, end).apply {
                this[2] = 0x81.toByte(); this[3] = 0x80.toByte()
                this[6] = 0; this[7] = if (answer) 1 else 0
                this[8] = 0; this[9] = 0; this[10] = 0; this[11] = 0
            }
            val bytes = if (answer) response + byteArrayOf(
                0xc0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 30, 0, 4, 127, 0, 0, 1
            ) else response
            socket.send(DatagramPacket(bytes, bytes.size, packet.socketAddress))
        }
    }

    override fun close() { socket.close(); worker.join(1000) }
}
