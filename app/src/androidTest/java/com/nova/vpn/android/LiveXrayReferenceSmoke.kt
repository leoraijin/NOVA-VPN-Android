package com.nova.vpn.android

import android.app.Instrumentation
import android.os.Bundle
import com.nova.vpn.android.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Explicit disposable-device reference; private one-use configs, no catalog changes. */
internal object LiveXrayReferenceSmoke {
    fun run(instrumentation: Instrumentation, phase: String, port: Int) {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        if (phase != "cleanup") check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        check(port in 1024..65535)
        fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$value\n") })
        fun output(kind: String) = File(context.filesDir, "xray-reference-$kind.json")
        when (phase) {
            "cleanup" -> {
                for (kind in listOf("tcp", "grpc")) output(kind).let { check(!it.exists() || it.delete()) }
                report("REFERENCE private configurations removed")
            }
            "prepare" -> {
                check(listOf("tcp", "grpc").none { output(it).exists() })
                val input = File(context.getExternalFilesDir(null), "live-subscription-input.txt")
                check(input.isFile && input.length() in 1..4096)
                val url = try { input.readText().trim() } finally { check(input.delete()) }
                val response = SubscriptionFetcher.fetchSubscription(url, SubscriptionDeviceIdentity(context).forUrl(url), null, null)
                val profiles = ProfileLinkParser.parseReport(response.body).profiles
                val roots = JSONArray(response.body.trim())
                try {
                    for (kind in listOf("tcp", "grpc")) {
                        val wanted = profiles.first { profileTransport(it) == kind }.configurationKey()
                        var reference: JSONObject? = null
                        rootsLoop@ for (i in 0 until roots.length()) {
                            val root = roots.optJSONObject(i) ?: continue
                            val outbounds = root.optJSONArray("outbounds") ?: continue
                            for (j in 0 until outbounds.length()) {
                                val out = outbounds.optJSONObject(j) ?: continue
                                val single = JSONObject(root.toString()).put("outbounds", JSONArray().put(out))
                                val candidates = runCatching { ProfileLinkParser.parseText(single.toString()) }.getOrNull() ?: continue
                                if (candidates.size != 1 || candidates.single().configurationKey() != wanted) continue
                                val originals = JSONArray(outbounds.toString())
                                val selected = originals.getJSONObject(j)
                                val tags = (0 until originals.length()).mapNotNull { originals.optJSONObject(it)?.optString("tag")?.takeIf(String::isNotEmpty) }
                                check(tags.distinct().size == tags.size)
                                var tag = selected.optString("tag")
                                if (tag.isBlank()) {
                                    tag = "nova-reference-selected"
                                    while (tag in tags) tag += "-x"
                                    selected.put("tag", tag)
                                }
                                val inbound = "nova-reference-loopback"
                                val rules = JSONArray().put(JSONObject().put("type", "field").put("inboundTag", JSONArray().put(inbound))
                                    .put("network", "tcp,udp").put("outboundTag", tag))
                                val routing = root.optJSONObject("routing")?.let { JSONObject(it.toString()) } ?: JSONObject()
                                routing.optJSONArray("rules")?.let { old -> for (k in 0 until old.length()) rules.put(old.get(k)) }
                                routing.put("rules", rules)
                                reference = JSONObject().put("log", JSONObject().put("loglevel", "none"))
                                    .put("inbounds", JSONArray().put(JSONObject().put("tag", inbound).put("listen", "127.0.0.1").put("port", port)
                                        .put("protocol", "socks").put("settings", JSONObject().put("auth", "noauth").put("udp", false))))
                                    .put("outbounds", originals).put("routing", routing)
                                for (key in listOf("dns", "policy", "transport")) if (root.has(key)) reference.put(key, root.get(key))
                                break@rootsLoop
                            }
                        }
                        check(reference != null)
                        output(kind).writeText(reference.toString())
                        report("REFERENCE prepared transport=$kind exactImportedEndpoint=true")
                    }
                } catch (error: Throwable) {
                    for (kind in listOf("tcp", "grpc")) output(kind).delete()
                    throw error
                }
            }
            "probe" -> {
                // Fixed HTTPS targets; default certificate and hostname verification remain enabled.
                for (target in listOf("https://1.1.1.1/cdn-cgi/trace", "https://www.google.com/generate_204")) {
                    var connection: HttpsURLConnection? = null
                    var outcome = "request_failed"
                    try {
                        connection = URL(target).openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))) as HttpsURLConnection
                        connection.connectTimeout = 12_000; connection.readTimeout = 12_000
                        connection.instanceFollowRedirects = false; connection.useCaches = false
                        val code = connection.responseCode
                        outcome = if (code == if (target.contains("generate_204")) 204 else 200) "ok" else "http_$code"
                    } catch (error: Exception) { outcome = error.javaClass.simpleName }
                    finally { connection?.disconnect() }
                    report("REFERENCE probe target=${if (target.contains("generate_204")) "dns_https" else "literal_ip_https"} outcome=$outcome")
                }
            }
            else -> error("Unsupported reference phase")
        }
    }
}