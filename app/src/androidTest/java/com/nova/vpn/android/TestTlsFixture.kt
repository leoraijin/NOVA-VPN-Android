package com.nova.vpn.android

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.cert.CertificateFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.security.auth.x500.X500Principal

/** Ephemeral loopback TLS identity. No pre-existing private key is bundled or exported. */
internal class TestTlsFixture(context: Context) {
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val algorithm = "300d06092a864886f70d01010b0500".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val subject = X500Principal("CN=localhost").encoded
    private val clock = SimpleDateFormat("yyyyMMddHHmmss'Z'", java.util.Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val validity = der(0x30, der(0x18, clock.format(Date(System.currentTimeMillis() - 86400000)).toByteArray()),
        der(0x18, clock.format(Date(System.currentTimeMillis() + 86400000)).toByteArray()))
    private val san = der(0xa3, der(0x30, der(0x30,
        byteArrayOf(6, 3, 0x55, 0x1d, 0x11), der(4, der(0x30, der(0x82, "localhost".toByteArray()))))))
    private val tbs = der(0x30, der(0xa0, der(2, byteArrayOf(2))), der(2, BigInteger.ONE.toByteArray()),
        algorithm, subject, validity, subject, key.public.encoded, san)
    private val signed = Signature.getInstance("SHA256withRSA").run { initSign(key.private); update(tbs); sign() }
    private val encoded = der(0x30, tbs, algorithm, der(3, byteArrayOf(0), signed))
    private val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(encoded))
    val certificatePath = File(context.filesDir, "synthetic-loopback-ca.pem").apply {
        writeText("-----BEGIN CERTIFICATE-----\n" + android.util.Base64.encodeToString(encoded, android.util.Base64.NO_WRAP)
            .chunked(64).joinToString("\n") + "\n-----END CERTIFICATE-----\n")
    }.path

    fun serverSocket(): SSLServerSocket {
        val password = "ephemeral-only".toCharArray()
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("loopback", key.private, password, arrayOf(certificate))
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password) }
        val tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
        return tls.serverSocketFactory.createServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")) as SSLServerSocket
    }

    private fun der(tag: Int, vararg parts: ByteArray): ByteArray {
        val content = parts.fold(byteArrayOf()) { acc, part -> acc + part }
        val length = if (content.size < 128) byteArrayOf(content.size.toByte()) else {
            val bytes = BigInteger.valueOf(content.size.toLong()).toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            byteArrayOf((0x80 or bytes.size).toByte()) + bytes
        }
        return byteArrayOf(tag.toByte()) + length + content
    }
}
