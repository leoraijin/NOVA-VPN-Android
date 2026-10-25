package com.nova.vpn.android

import java.io.InputStream
import java.nio.charset.StandardCharsets

internal enum class ProbeTarget(val host: String, val path: String) {
    CLOUDFLARE("1.1.1.1", "/cdn-cgi/trace"),
    GOOGLE("www.gstatic.com", "/generate_204");

    fun socksDestination(): ByteArray {
        val name = host.toByteArray(StandardCharsets.US_ASCII)
        return if (this == CLOUDFLARE) byteArrayOf(5, 1, 0, 1, 1, 1, 1, 1, 1, -69)
        else byteArrayOf(5, 1, 0, 3, name.size.toByte()) + name + byteArrayOf(1, -69)
    }

    /** Reads bounded lines without BufferedReader.readLine's unbounded allocation. */
    fun readResponse(input: InputStream, onHeaders: () -> Unit): Boolean {
        var consumed = 0
        fun line(): String? {
            val bytes = java.io.ByteArrayOutputStream()
            while (true) {
                check(consumed < 8192) { "Probe response exceeds limit" }
                val value = input.read()
                if (value < 0) return if (bytes.size() == 0) null else bytes.toString("US-ASCII").removeSuffix("\r")
                consumed++
                if (value == 10) return bytes.toString("US-ASCII").removeSuffix("\r")
                check(bytes.size() < 2048) { "Probe response line exceeds limit" }
                bytes.write(value)
            }
        }
        val status = line() ?: return false
        val expected = if (this == GOOGLE) 204 else 200
        if (!status.matches(Regex("HTTP/1\\.[01] $expected(?: .*)?"))) return false
        while (true) {
            val header = line() ?: return false
            if (header.isEmpty()) break
            if (!header.contains(':')) return false
        }
        onHeaders()
        if (this == GOOGLE) return true
        while (true) {
            val body = line() ?: return false
            if (body.startsWith("ip=") && body.length > 3) return true
        }
    }
}
