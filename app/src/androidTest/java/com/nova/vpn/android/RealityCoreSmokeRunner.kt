package com.nova.vpn.android

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.nova.vpn.android.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** No user keys, external endpoints, VPN consent or stable app data required. */
class RealityCoreSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(targetContext.packageName.endsWith(".realitypreview"))
            RealityCoreBridge.requireAvailable()
            val protected = AtomicInteger()
            val callbacks = object : RealityCoreBridge.Callbacks {
                override fun protectSocket(fd: Int): Boolean { protected.incrementAndGet(); return true }
                override fun coreWarning(message: String) { VpnDiagnostics.coreMessage(message) }
            }
            val profile = ImportedProfile(name = "Synthetic", protocol = "vless", server = "127.0.0.1", port = 443,
                outboundJson = """{"type":"vless","server":"127.0.0.1","server_port":443,"uuid":"11111111-1111-4111-8111-111111111111","tls":{"enabled":true,"server_name":"localhost"}}""")
            val generated = RealityConfigFactory.prepare(SingBoxConfigFactory.build(profile,
                ownPackage = targetContext.packageName), targetContext.packageName)
            check(RealityCoreBridge.validate(generated.config, targetContext.filesDir.path)) { "Generated config rejected" }
            check(!RealityCoreBridge.validate("{}", targetContext.filesDir.path)) { "Invalid config accepted" }
            val badTls = JSONObject(generated.config).apply {
                getJSONArray("outbounds").getJSONObject(0).put("tls", JSONObject().put("enabled", true).put("insecure", true))
            }.toString()
            check(!RealityCoreBridge.validate(badTls, targetContext.filesDir.path)) { "Insecure TLS accepted" }
            val pipe = ParcelFileDescriptor.createPipe()
            val transferred = pipe[0].detachFd()
            check(RealityCoreBridge.start("{}", targetContext.filesDir.path, transferred, callbacks) == 0L)
            check(!java.io.File("/proc/self/fd/$transferred").exists()) {
                "Native failed start did not close owned FD"
            }
            pipe[1].close()
            // Repeated real SOCKS traffic, not just library-load checks.
            repeat(8) {
                ServerSocket(0).use { echo ->
                    val worker = thread(isDaemon = true) {
                        echo.accept().use { client -> client.getOutputStream().write(client.getInputStream().read()) }
                    }
                    val port = ServerSocket(0).use { it.localPort }
                    val config = JSONObject().put("inbounds", JSONArray().put(JSONObject()
                        .put("type", "socks").put("tag", "smoke").put("listen", "127.0.0.1").put("listen_port", port)))
                        .put("outbounds", JSONArray().put(JSONObject().put("type", "direct").put("tag", "direct")))
                        .put("route", JSONObject().put("final", "direct")).toString()
                    val handle = RealityCoreBridge.start(config, targetContext.filesDir.path, -1, callbacks)
                    check(handle != 0L) { "Native SOCKS start failed" }
                    try {
                        check(RealityCoreBridge.isAlive(handle))
                        Socket("127.0.0.1", port).use { socket ->
                            socket.soTimeout = 5000
                            val input = socket.getInputStream(); val output = socket.getOutputStream()
                            output.write(byteArrayOf(5, 1, 0)); output.flush()
                            check(input.read() == 5 && input.read() == 0)
                            output.write(byteArrayOf(5, 1, 0, 1, 127, 0, 0, 1,
                                (echo.localPort shr 8).toByte(), echo.localPort.toByte())); output.flush()
                            check(input.read() == 5 && input.read() == 0)
                            check(input.read() == 0)
                            val size = when (input.read()) { 1 -> 4; 4 -> 16; else -> error("SOCKS address") }
                            repeat(size + 2) { check(input.read() >= 0) }
                            output.write(42); output.flush()
                            check(input.read() == 42) { "Native proxy dropped echo payload" }
                        }
                        check(RealityCoreBridge.isAlive(handle))
                    } finally { RealityCoreBridge.stop(handle) }
                    worker.join(5000)
                    check(!worker.isAlive)
                }
            }
            check(protected.get() >= 8) { "Socket callback not invoked" }
            runVpnSmoke()
            result.putString("stream", "Reality JNI/TUN smoke passed: validation, TLS refusal, FD ownership, 8 SOCKS/stop cycles, callbacks, real VpnService, IPv4/IPv6 app TCP echo, include/exclude policies, refused live reload, quiet single notification and sing-box regression. Core ${RealityCoreBridge.version()}\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "Reality smoke failed: ${failure.javaClass.simpleName}: ${failure.message}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun runVpnSmoke() {
        val context = targetContext
        check(android.net.VpnService.prepare(context) == null) { "Grant ACTIVATE_VPN to the preview package before TUN smoke" }
        AppPreferencesStore(context).apply {
            dismissTutorial(); setAutoConnectEnabled(false); setUpdateChecksEnabled(false)
            setVpnEngine(VpnEngine.REALITY_CORE)
        }
        context.getSharedPreferences("app_preferences", 0).edit().putBoolean("notification_permission_requested", true).commit()
        val activity = startActivitySync(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        val tlsFixture = TestTlsFixture(context)
        tlsFixture.serverSocket().use { listener ->
            val packets = AtomicInteger()
            val sitePackets = AtomicInteger()
            val fixtureFailure = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val acceptor = thread(isDaemon = true) {
                while (!listener.isClosed) runCatching {
                    val peer = listener.accept()
                    thread(isDaemon = true) {
                        runCatching { peer.use { socket ->
                            socket.soTimeout = 7000
                            val input = java.io.DataInputStream(socket.getInputStream())
                            check(input.readUnsignedByte() == 0)
                            input.readFully(ByteArray(16))
                            input.readFully(ByteArray(input.readUnsignedByte()))
                            check(input.readUnsignedByte() == 1)
                            input.readUnsignedShort()
                            val type = input.readUnsignedByte()
                            val destination = ByteArray(when (type) { 1 -> 4; 2 -> input.readUnsignedByte(); 3 -> 16; else -> error("VLESS address") })
                            input.readFully(destination)
                            socket.getOutputStream().write(byteArrayOf(0, 0))
                            val payload = ByteArray(14)
                            input.readFully(payload)
                            val count = payload.size
                            if (count > 0) {
                                val text = String(payload, 0, count, Charsets.US_ASCII)
                                if (text == "nova-tun-smoke") {
                                    packets.incrementAndGet()
                                    if (type == 2 && String(destination, Charsets.US_ASCII) == "native.test") sitePackets.incrementAndGet()
                                }
                                socket.getOutputStream().write(payload, 0, count)
                                socket.getOutputStream().flush()
                            }
                        } }.onFailure { fixtureFailure.set(it.javaClass.simpleName + ": " + it.message) }
                    }
                }
            }
            try {
                val profile = ImportedProfile(name = "Synthetic TUN", protocol = "vless", server = "127.0.0.1", port = listener.localPort,
                    outboundJson = JSONObject().put("type", "vless").put("server", "127.0.0.1")
                        .put("server_port", listener.localPort).put("uuid", "11111111-1111-4111-8111-111111111111")
                        .put("tls", JSONObject().put("enabled", true).put("server_name", "localhost")
                            .put("certificate_path", tlsFixture.certificatePath)).toString())
                val config = SingBoxConfigFactory.build(profile, ownPackage = context.packageName)
                fun start(config: String, burst: Int = 1) {
                    repeat(burst) {
                        context.startForegroundService(android.content.Intent(context, NovaVpnService::class.java)
                            .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                            .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "Synthetic TUN"))
                    }
                    waitUntil("startup") { VpnRuntime.state.value.connected || VpnRuntime.state.value.error != null }
                    check(VpnRuntime.state.value.connected) { "VpnService failed: ${VpnRuntime.state.value.error}" }
                }
                fun stop() {
                    context.startService(android.content.Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP))
                    waitUntil("stop") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
                }
                start(config, burst = 20)
                val notifications = context.getSystemService(android.app.NotificationManager::class.java).activeNotifications
                check(notifications.size == 1) { "Expected one VPN notification, got ${notifications.size}" }
                val channel = context.getSystemService(android.app.NotificationManager::class.java)
                    .getNotificationChannel(notifications.single().notification.channelId)
                check(channel.importance <= android.app.NotificationManager.IMPORTANCE_LOW &&
                    channel.sound == null && !channel.shouldVibrate()) { "VPN notification is not silent/low priority" }
                val answer = routedClientResult()
                check(answer == "ok" && packets.get() == 1) {
                    "Traffic did not traverse native TUN/VLESS echo: response=${answer.take(100)}, packets=${packets.get()}, fixture=${fixtureFailure.get()}, " +
                        VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value)
                }
                check(routedClientResult(ipv6 = true) == "ok" && packets.get() == 2) { "IPv6 bypassed or failed TUN" }
                // The selected active TUN must survive a live config request.
                start(config)
                waitUntil("live reload refusal") { VpnRuntime.state.value.transportNotice != null }
                check(routedClientResult() == "ok" && packets.get() == 3) { "Refused live reload damaged tunnel" }
                stop()
                val testPackage = "${context.packageName}.test"
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED, setOf(testPackage)), context.packageName))
                check(routedClientResult() == "ok" && packets.get() == 4) { "Included application did not traverse TUN" }
                stop()
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.EXCLUDE_SELECTED, setOf(testPackage)), context.packageName))
                check(routedClientResult() != "ok" && packets.get() == 4) { "Excluded application unexpectedly traversed TUN" }
                stop()
                start(SingBoxConfigFactory.build(profile, AppRoutingSettings(AppRoutingMode.INCLUDE_SELECTED,
                    sites = listOf(SiteRoutingRule("native.test", SiteRoutingTarget.VPN))), context.packageName))
                check(routedClientResult(site = true) == "ok" && packets.get() == 5 && sitePackets.get() == 1) {
                    "Site VPN exception did not preserve fake-IP domain routing"
                }
                stop()
                AppPreferencesStore(context).setVpnEngine(VpnEngine.SING_BOX)
                start(config)
                check(routedClientResult() == "ok" && packets.get() == 6) { "Default sing-box regression: TLS/TUN echo failed" }
                stop()
            } finally {
                context.startService(android.content.Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP))
                waitUntil("final cleanup") { !VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting }
                AppPreferencesStore(context).setVpnEngine(VpnEngine.SING_BOX)
                runOnMainSync { activity.finish() }
            }
            listener.close()
            acceptor.join(1000)
        }
    }

    private fun waitUntil(label: String = "condition", condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 15000
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() < deadline) {
                "Timed out: $label. " + VpnDiagnostics.report(null, AppRoutingSettings(), android.os.Build.VERSION.SDK_INT, VpnRuntime.state.value)
            }
            android.os.SystemClock.sleep(100)
        }
    }

    private fun routedClientResult(ipv6: Boolean = false, site: Boolean = false): String {
        val context = targetContext
        val nonce = java.util.UUID.randomUUID().toString()
        val answer = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.getStringExtra("nonce") == nonce) answer.set(intent.getStringExtra("result"))
            }
        }
        val filter = android.content.IntentFilter("${context.packageName}.REALITY_SMOKE_RESULT")
        if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        else { @Suppress("DEPRECATION") context.registerReceiver(receiver, filter) }
        try {
            context.startActivity(android.content.Intent().setComponent(android.content.ComponentName(
                "${context.packageName}.test", RealityProbeClientActivity::class.java.name))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("nonce", nonce).putExtra("ipv6", ipv6).putExtra("site", site))
            waitUntil("client result ipv6=$ipv6 site=$site") { answer.get() != null }
            return answer.get()!!
        } finally { context.unregisterReceiver(receiver) }
    }
}
