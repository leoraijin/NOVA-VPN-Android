package com.nova.vpn.android

import android.app.Instrumentation
import android.os.Bundle
import com.nova.vpn.android.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/** Explicit audit: one-use input, no persisted real profiles, aggregate reports only. */
internal object LiveSubscriptionAuditSmoke {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        val input = File(context.getExternalFilesDir(null), "live-subscription-input.txt")
        check(input.isFile && input.length() in 1..4096)
        val url = try { input.readText().trim() } finally { check(input.delete()) }
        val uri = URI(url)
        check(uri.scheme == "https" && uri.host != null)
        fun report(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", message + "\n") })
        val device = SubscriptionDeviceIdentity(context).forUrl(url)
        var usableResponses = 0
        val vpnProtocols = setOf("vless", "vmess", "trojan", "shadowsocks", "hysteria")
        for ((ordinal, agent) in listOf("NOVA-VPN-Android", "v2raytun/android").withIndex()) {
            val response = try { SubscriptionFetcher.download(uri, agent, device, null, null) }
                catch (error: Exception) { report("AUDIT response=${ordinal + 1} fetchFailure=${error.javaClass.simpleName}"); continue }
            report("AUDIT response=${ordinal + 1} status=${response.status} hwidMissing=${response.hwidMissing} deviceLimit=${response.deviceLimit} bytes=${response.body.toByteArray(Charsets.UTF_8).size}")
            if (response.hwidMissing || response.deviceLimit) continue
            val body = response.body.trim()
            // Do not add a second desugared java.util.Base64 to the instrumentation
            // APK: its shrunk factories can shadow the target application's copy.
            val decoded = if (body.startsWith("[") || body.startsWith("{") || body.contains("://")) body else runCatching {
                String(android.util.Base64.decode(body, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
            }.getOrDefault(body)
            val configs = if (decoded.startsWith("[")) JSONArray(decoded) else null
            val roots = configs ?: if (decoded.startsWith("{")) JSONArray().put(JSONObject(decoded)) else JSONArray()
            val links = if (roots.length() == 0) decoded.lineSequence().count { it.trim().contains("://") } else 0
            report("AUDIT response=${ordinal + 1} format=${if (roots.length() > 0) "json" else "links"} entries=${configs?.length() ?: roots.length().takeIf { it > 0 } ?: links}")
            var rawOutbounds = 0; var rawXhttp = 0; var rawDownload = 0; var unimportedEntries = 0
            var grpcCount = 0; var grpcMulti = 0; var authorityDiffers = 0; var chained = 0; var grpcTcpMask = 0; var grpcBootstrapOverrides = 0
            val networks = mutableMapOf<String, Int>()
            for (i in 0 until roots.length()) {
                val root = roots.optJSONObject(i) ?: continue
                if (runCatching { ProfileLinkParser.parseReport(root.toString()) }.isFailure) unimportedEntries++
                val outbounds = root.optJSONArray("outbounds") ?: continue
                for (j in 0 until outbounds.length()) {
                    val out = outbounds.optJSONObject(j) ?: continue
                    if (out.optString("protocol").lowercase() !in vpnProtocols) continue
                    rawOutbounds++
                    val stream = out.optJSONObject("streamSettings") ?: JSONObject()
                    val network = stream.optString("method").ifBlank { stream.optString("network", "tcp") }.lowercase()
                        .takeIf { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,32}")) } ?: "other"
                    networks[network] = (networks[network] ?: 0) + 1
                    if (network in setOf("xhttp", "splithttp")) {
                        rawXhttp++
                        val settings = stream.optJSONObject("xhttpSettings") ?: stream.optJSONObject("splithttpSettings") ?: JSONObject()
                        if (settings.has("downloadSettings") || settings.optJSONObject("extra")?.has("downloadSettings") == true) rawDownload++
                    }
                    if (network == "grpc") {
                        grpcCount++
                        // A provider can define endpoint aliases in its root DNS hosts.
                        // Count matches without disclosing either hostname or mapped IP.
                        val hosts = root.optJSONObject("dns")?.optJSONObject("hosts")
                        val servers = out.optJSONObject("settings")?.optJSONArray("vnext")
                        if (hosts != null && servers != null && (0 until servers.length()).any { index ->
                            val address = servers.optJSONObject(index)?.optString("address").orEmpty()
                            address.isNotEmpty() && (hosts.has(address) || hosts.has("full:$address") || hosts.has("domain:$address"))
                        }) grpcBootstrapOverrides++
                        val grpc = stream.optJSONObject("grpcSettings") ?: JSONObject()
                        if (grpc.optBoolean("multiMode")) grpcMulti++
                        val tls = stream.optJSONObject("tlsSettings") ?: stream.optJSONObject("realitySettings") ?: JSONObject()
                        if (grpc.optString("authority").isNotEmpty() && !grpc.optString("authority").equals(tls.optString("serverName"), true)) authorityDiffers++
                        if (out.optJSONObject("proxySettings")?.optString("tag").orEmpty().isNotEmpty() ||
                            stream.optJSONObject("sockopt")?.optString("dialerProxy").orEmpty().isNotEmpty()) chained++
                        if ((stream.optJSONObject("finalmask")?.optJSONArray("tcp")?.length() ?: 0) > 0) grpcTcpMask++
                    }
                }
            }
            report("AUDIT response=${ordinal + 1} rawVpnOutbounds=$rawOutbounds rawXhttp=$rawXhttp rawDownloadSettings=$rawDownload unimportedEntries=$unimportedEntries networks=${networks.toSortedMap()}")
            report("AUDIT response=${ordinal + 1} grpcVpnOccurrences=$grpcCount multiMode=$grpcMulti authorityDiffersFromSni=$authorityDiffers chained=$chained tcpMask=$grpcTcpMask bootstrapHostOverrides=$grpcBootstrapOverrides")
            val parsed = try { ProfileLinkParser.parseReport(response.body) }
                catch (error: Exception) { report("AUDIT response=${ordinal + 1} parseFailure=${error.javaClass.simpleName}"); continue }
            usableResponses++
            report("AUDIT response=${ordinal + 1} imported=${parsed.profiles.size} warnings=${parsed.warnings.size}")
            parsed.profiles.groupBy { profileTransport(it) }.forEach { (transport, profiles) -> report("AUDIT response=${ordinal + 1} transport=$transport count=${profiles.size}") }
            var generated = 0; var generationRefused = 0; var realityAdapted = 0; var realityRefused = 0
            for (profile in parsed.profiles) {
                val config = try { SingBoxConfigFactory.build(profile, AppRoutingSettings(), context.packageName).also { generated++ } }
                    catch (_: Exception) { generationRefused++; continue }
                try { RealityConfigFactory.prepare(config, context.packageName); realityAdapted++ } catch (_: Exception) { realityRefused++ }
            }
            report("AUDIT response=${ordinal + 1} generated=$generated generationRefused=$generationRefused realityAdapted=$realityAdapted realityAdaptationRefused=$realityRefused")
        }
        check(usableResponses > 0)
        report("AUDIT COMPLETE: no VPN start; no real profiles persisted; per-origin test identity retained")
    }
}