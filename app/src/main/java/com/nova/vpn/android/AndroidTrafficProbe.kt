package com.nova.vpn.android
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
/** A bounded HTTPS check explicitly through the core, not the excluded app's direct route. */
internal class AndroidTrafficProbe private constructor(
    val config: String, private val port: Int, private val username: String, private val password: String
) {
    private val cancelled = AtomicBoolean()
    var onProgress: (() -> Unit)? = null
    private fun record(code: String, metrics: Map<DiagnosticMetric, Long> = emptyMap()) {
        VpnDiagnostics.record(code, metrics)
        runCatching { onProgress?.invoke() }
    }
    // Response headers over an established TLS channel; never includes SOCKS/TLS setup.
    @Volatile var responseMillis: Int? = null
        private set
    @Volatile private var socket: Socket? = null
    fun cancel() { cancelled.set(true); runCatching { socket?.close() } }
    fun check(timeoutMillis: Int = 18_000, target: ProbeTarget = ProbeTarget.CLOUDFLARE): Boolean {
        require(timeoutMillis > 0)
        responseMillis = null
        val expired = AtomicBoolean()
        val attemptSocket = java.util.concurrent.atomic.AtomicReference<Socket?>()
        var stage = "local_socket"
        record("probe_start")
        val deadline = java.util.Timer("nova-probe-deadline", true)
        deadline.schedule(object : java.util.TimerTask() { override fun run() {
            expired.set(true); runCatching { attemptSocket.get()?.close() }
        } }, timeoutMillis.toLong())
        return try {
        check(!cancelled.get() && !expired.get())
        Socket().use { raw ->
            socket = raw
            attemptSocket.set(raw)
            check(!cancelled.get() && !expired.get())
            raw.connect(InetSocketAddress("127.0.0.1", port), 3000)
            raw.soTimeout = 7000
            stage = "socks_auth"
            val input = raw.getInputStream()
            val output = raw.getOutputStream()
            // SOCKS5 username/password: another local app cannot use this test listener.
            output.write(byteArrayOf(5, 1, 2)); output.flush()
            check(input.read() == 5 && input.read() == 2)
            val user = username.toByteArray(StandardCharsets.UTF_8)
            val secret = password.toByteArray(StandardCharsets.UTF_8)
            output.write(byteArrayOf(1, user.size.toByte()) + user + byteArrayOf(secret.size.toByte()) + secret)
            output.flush(); check(input.read() == 1 && input.read() == 0)
            stage = "proxy_connect"
            record("probe_proxy_connect_started")
            // Domains are resolved by the proxy, never by the excluded app's DNS.
            output.write(target.socksDestination()); output.flush()
            check(input.read() == 5)
            val reply = input.read()
            if (reply in 1..8) record("probe_socks_reply_$reply")
            check(reply == 0 && input.read() == 0)
            val addressBytes = when (input.read()) { 1 -> 4; 4 -> 16; 3 -> input.read().also { check(it >= 0) }; else -> error("SOCKS address") }
            repeat(addressBytes + 2) { check(input.read() >= 0) }
            record("probe_proxy_connected")
            check(!cancelled.get() && !expired.get())
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, target.host, 443, false) as SSLSocket).use { tls ->
                socket = tls
                attemptSocket.set(tls)
                check(!cancelled.get() && !expired.get())
                tls.soTimeout = 7000
                tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                stage = "target_tls"
                record("probe_target_tls_started")
                tls.startHandshake()
                record("probe_target_tls_ready")
                stage = "https_response"
                record("probe_https_response_started")
                val responseTimer = ResponseLatency()
                var headersMillis: Int? = null
                tls.getOutputStream().apply {
                    write("GET ${target.path} HTTP/1.1\r\nHost: ${target.host}\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                    flush()
                }
                (target.readResponse(tls.getInputStream()) { headersMillis = responseTimer.millis() }
                    && !cancelled.get() && !expired.get()).also {
                    responseMillis = if (it) headersMillis else null
                    record(if (it) "probe_success" else "probe_invalid_response",
                        responseMillis?.let { value -> mapOf(DiagnosticMetric.RESPONSE_MS to value.toLong()) } ?: emptyMap())
                }
            }
        }
        } catch (error: Exception) {
            val category = if (expired.get()) "timeout" else when (error) {
                is java.net.SocketTimeoutException -> "timeout"
                is javax.net.ssl.SSLException -> "tls_error"
                is java.net.ConnectException -> "connect_error"
                is java.io.IOException -> "io_error"
                else -> "protocol_error"
            }
            record(if (cancelled.get()) "core_cancelled" else "probe_failed_${stage}_$category")
            false
        } finally { deadline.cancel(); socket = null }
    }
    companion object {
        internal const val TAG = "nova-local-health"
        internal fun validTrace(response: String): Boolean =
            response.lineSequence().firstOrNull()?.matches(Regex("HTTP/1\\.[01] 200(?: .*)?")) == true &&
                response.contains("\n\n") && response.substringAfter("\n\n").lineSequence().any { it.startsWith("ip=") && it.length > 3 }
        fun prepare(config: String): AndroidTrafficProbe? {
            val json = JSONObject(config)
            val route = json.optJSONObject("route") ?: return null
            val finalTag = route.optString("final")
            val outbounds = json.optJSONArray("outbounds") ?: return null
            val final = (0 until outbounds.length()).mapNotNull(outbounds::optJSONObject)
                .firstOrNull { it.optString("tag") == finalTag } ?: return null
            // Arbitrary selectors/full configs may contain a direct fallback. Do not certify them.
            if (final.optString("type") !in setOf("vless", "trojan", "shadowsocks", "vmess", "hysteria2", "tuic")) return null
            val inbounds = json.optJSONArray("inbounds") ?: JSONArray()
            if ((0 until inbounds.length()).any { inbounds.optJSONObject(it)?.optString("tag") == TAG }) return null
            val port = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
            val user = UUID.randomUUID().toString(); val password = UUID.randomUUID().toString()
            inbounds.put(JSONObject().put("type", "socks").put("tag", TAG).put("listen", "127.0.0.1")
                .put("listen_port", port).put("users", JSONArray().put(JSONObject().put("username", user).put("password", password))))
            json.put("inbounds", inbounds)
            val original = route.optJSONArray("rules") ?: JSONArray()
            val rules = JSONArray().put(JSONObject().put("inbound", JSONArray().put(TAG)).put("action", "route").put("outbound", finalTag))
            for (index in 0 until original.length()) rules.put(original.get(index))
            route.put("rules", rules)
            return AndroidTrafficProbe(json.toString(), port, user, password)
        }
    }
}
