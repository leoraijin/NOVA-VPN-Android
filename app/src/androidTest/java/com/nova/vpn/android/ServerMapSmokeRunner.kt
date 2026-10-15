package com.nova.vpn.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.vpn.android.data.*
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.concurrent.thread

/** Runs only against the separate .maptest app with synthetic, disposable profiles. */
class ServerMapSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        var exitCode = Activity.RESULT_CANCELED
        var server: ServerSocket? = null
        var liveProbe: AndroidTrafficProbe? = null
        try {
            check(targetContext.packageName.endsWith(".maptest"))
            val count = AtomicInteger()
            server = ServerSocket(0)
            val listener = server
            thread(isDaemon = true, name = "synthetic-vless") {
                while (!listener.isClosed) runCatching {
                    val incoming = listener.accept()
                    count.incrementAndGet()
                    thread(isDaemon = true) { relay(incoming) }
                }
            }
            val closedPort = ServerSocket(0).use { it.localPort }
            fun profile(name: String, port: Int) = ImportedProfile(name = name, protocol = "vless", server = "127.0.0.1", port = port,
                outboundJson = JSONObject().put("type", "vless").put("tag", "proxy").put("server", "127.0.0.1").put("server_port", port)
                    .put("uuid", "00000000-0000-4000-8000-000000000001").toString())
            val good = profile("🇳🇱 Netherlands Amsterdam · тест", listener.localPort)
            val second = profile("🇫🇮 Finland Helsinki · тест", listener.localPort)
            val failed = profile("🇵🇱 Poland Warsaw · нет ответа", closedPort)
            val unsupported = profile("🇩🇪 Germany · полный JSON", closedPort).copy(fullConfigJson = """{"outbounds":[{"type":"direct","tag":"direct"}]}""")
            val unknown = unsupported.copy(id = "unknown-location", name = "Без локации")
            ProfileVault(targetContext).save(listOf(good, second, failed, unsupported, unknown))
            SelectedProfileStore(targetContext).save(good.id)
            AppPreferencesStore(targetContext).apply { dismissTutorial(); setAutoConnectEnabled(false); setUpdateChecksEnabled(false); setSelectedDesign("MATERIAL_YOU") }
            targetContext.getSharedPreferences("app_preferences", 0).edit().putBoolean("notification_permission_requested", true).commit()
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            SystemClock.sleep(700)
            val serverNav = bounds(waitFor { findText("Серверы") })
            click(waitFor { findText("Серверы") })
            waitFor { findDescription("Карта серверов.") }
            SystemClock.sleep(1_200)
            check(count.get() == 0) { "A check ran before the button was pressed" }
            screenshot("map-before.png")
            val map = bounds(waitFor { findDescription("Карта серверов.") })
            val beforePan = mapFingerprint(map)
            swipe(map.centerX().toFloat(), map.centerY().toFloat(), map.centerX() + 65f, map.centerY() + 45f)
            SystemClock.sleep(500)
            check(beforePan != mapFingerprint(map)) { "Map did not pan" }
            screenshot("map-panned.png")
            val beforePinch = mapFingerprint(map)
            pinch(map.centerX().toFloat(), map.centerY().toFloat())
            SystemClock.sleep(500)
            check(beforePinch != mapFingerprint(map)) { "Pinch did not zoom map" }
            screenshot("map-pinched.png")
            click(waitFor { findDescription("Приблизить карту") })
            screenshot("map-zoomed.png")
            click(waitFor { findDescription("Показать весь мир") })
            click(waitFor { findDescription("К выбранному серверу") })
            click(waitFor { findText("Проверить") })
            waitFor(45_000) { findText("Проверка завершена") }
            check(count.get() >= 2) { "Proxy engine did not reach synthetic endpoints" }
            check(findAny { it.text?.contains("мс") == true || it.contentDescription?.contains("мс") == true } != null) { "No successful latency result" }
            screenshot("map-results.png")
            click(waitFor { findDescription(second.name) })
            check(SelectedProfileStore(targetContext).load() == second.id) { "Map point did not select its profile" }
            click(waitFor { findDescription(good.name) })
            check(SelectedProfileStore(targetContext).load() == good.id)
            // Reopening the page must not trigger another request.
            val previous = count.get()
            click(waitFor { findText("Главная") })
            tap(serverNav.centerX().toFloat(), serverNav.centerY().toFloat())
            SystemClock.sleep(500)
            SystemClock.sleep(1_200)
            check(previous == count.get()) { "Page entry triggered a new request" }
            // Cancellation must close a real in-flight upstream socket, not just hide progress.
            ServerSocket(0).use { stalled ->
                val accepted = CountDownLatch(1)
                val closed = CountDownLatch(1)
                thread(isDaemon = true) { runCatching {
                    stalled.accept().use { peer ->
                        accepted.countDown()
                        try { val buffer = ByteArray(1024); while(peer.getInputStream().read(buffer) >= 0) { } }
                        finally { closed.countDown() }
                    }
                } }
                val checkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                val cancellationCheck = ServerChecks(targetContext, checkScope)
                try {
                    runOnMainSync { cancellationCheck.start(listOf(profile("Cancellation", stalled.localPort))) }
                    check(accepted.await(5, TimeUnit.SECONDS)) { "Cancellation fixture was not reached" }
                    runOnMainSync { cancellationCheck.cancel() }
                    check(closed.await(3, TimeUnit.SECONDS)) { "Cancelled check kept its upstream socket open" }
                    check(!cancellationCheck.state.value.running)
                } finally { checkScope.cancel() }
            }
            // The separate worker must also preserve an already running tunnel.
            check(android.net.VpnService.prepare(targetContext) == null) { "Grant ACTIVATE_VPN to the disposable maptest app" }
            liveProbe = AndroidTrafficProbe.prepare(SingBoxConfigFactory.build(good, ownPackage = targetContext.packageName))!!
            targetContext.startForegroundService(Intent(targetContext, NovaVpnService::class.java)
                .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG,
                    liveProbe.config)
                .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, good.name))
            val vpnDeadline = SystemClock.uptimeMillis() + 25_000
            while(SystemClock.uptimeMillis() < vpnDeadline && (!VpnRuntime.state.value.connected || VpnRuntime.state.value.trafficChecking)) SystemClock.sleep(150)
            check(VpnRuntime.state.value.connected) { "Synthetic VPN did not start" }
            check(liveProbe.check(7_000)) { "Active VPN proxy did not pass HTTPS before batch" }
            click(waitFor { findText("Проверить") })
            SystemClock.sleep(500)
            waitFor(45_000) { findText("Проверка завершена") }
            check(VpnRuntime.state.value.connected) { "Manual checks interrupted the active tunnel" }
            check(liveProbe.check(7_000)) { "Active VPN proxy did not pass HTTPS after batch" }
            result.putString("stream", "PASS: no automatic probes; pan; pinch; zoom controls; map selection; native proxy checks; cancellation closes sockets; page re-entry stays idle; existing VPN HTTPS before/after preserved. syntheticConnections=${count.get()}")
            exitCode = Activity.RESULT_OK
        } catch (error: Throwable) {
            screenshot("map-failure.png")
            result.putString("stream", "FAIL: ${error.stackTraceToString().lineSequence().take(7).joinToString("\n")}")
        } finally {
            liveProbe?.cancel()
            if (VpnRuntime.state.value.connected || VpnRuntime.state.value.connecting) {
                runCatching { targetContext.startService(Intent(targetContext, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP)) }
                val deadline = SystemClock.uptimeMillis() + 5_000
                while (SystemClock.uptimeMillis() < deadline && (VpnRuntime.state.value.connected || VpnRuntime.state.value.connecting)) SystemClock.sleep(100)
                if (VpnRuntime.state.value.connected) { result.putString("stream", "FAIL: synthetic VPN cleanup failed"); exitCode = Activity.RESULT_CANCELED }
            }
            server?.close()
        }
        finish(exitCode, result)
    }

    private fun relay(socket: Socket) {
        socket.use { client -> runCatching {
            client.soTimeout = 10_000
            val input = DataInputStream(client.getInputStream())
            check(input.readUnsignedByte() == 0)
            input.readFully(ByteArray(16))
            input.skipBytes(input.readUnsignedByte())
            check(input.readUnsignedByte() == 1)
            val port = input.readUnsignedShort()
            val host = when(input.readUnsignedByte()) {
                1 -> ByteArray(4).also(input::readFully).joinToString(".") { (it.toInt() and 255).toString() }
                2 -> ByteArray(input.readUnsignedByte()).also(input::readFully).toString(Charsets.UTF_8)
                else -> error("Unexpected address")
            }
            Socket(host, port).use { remote ->
                client.getOutputStream().apply { write(byteArrayOf(0, 0)); flush() }
                val upload = thread(isDaemon = true) { runCatching { input.copyTo(remote.getOutputStream()) } }
                runCatching { remote.getInputStream().copyTo(client.getOutputStream()) }
                remote.close(); upload.join(200)
            }
        } }
    }

    private fun findAny(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if(node == null) return null
            if(predicate(node)) return node
            for(i in 0 until node.childCount) visit(node.getChild(i))?.let { return it }
            return null
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) uiAutomation.clearCache()
        val root = uiAutomation.rootInActiveWindow
        root?.refresh()
        return visit(root)
    }
    private fun findText(text: String) = findAny { node ->
        node.text?.toString() == text && (text !in setOf("Главная", "Серверы", "Настройки") || clickableParent(node) != null)
    }
    private fun findDescription(text: String) = findAny { it.contentDescription?.toString()?.startsWith(text) == true }
    private fun waitFor(timeout: Long = 10_000, read: () -> AccessibilityNodeInfo?): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeout
        while(SystemClock.uptimeMillis() < deadline) { read()?.let { return it }; SystemClock.sleep(150) }
        error("UI wait timed out")
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)
    private fun click(node: AccessibilityNodeInfo) {
        val target = clickableParent(node) ?: error("No actionable parent for ${node.text}")
        if(!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            val rect = bounds(target)
            check(rect.width() > 0 && rect.height() > 0) { "No visible click target for ${node.text}" }
            val now = SystemClock.uptimeMillis()
            sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,rect.centerX().toFloat(),rect.centerY().toFloat(),0))
            sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,rect.centerX().toFloat(),rect.centerY().toFloat(),0))
        }
        SystemClock.sleep(500)
    }
    private fun clickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while(current != null && !current.isClickable) current = current.parent
        return current
    }
    private fun swipe(x: Float, y: Float, toX: Float, toY: Float) {
        val start = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,x,y,0))
        for(i in 1..12) {
            sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x+(toX-x)*i/12,y+(toY-y)*i/12,0))
            SystemClock.sleep(20)
        }
        sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,toX,toY,0))
    }
    private fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0))
        sendPointerSync(MotionEvent.obtain(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0))
    }
    private fun pinch(x: Float, y: Float) {
        val start = SystemClock.uptimeMillis()
        val props = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun event(action: Int, count: Int, distance: Float) {
            val coords = Array(count) { i -> MotionEvent.PointerCoords().apply {
                this.x = x + if(i == 0) -distance else distance; this.y = y; pressure = 1f; size = 1f
            } }
            sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0))
        }
        event(MotionEvent.ACTION_DOWN,1,45f)
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,45f)
        for(i in 1..10) { event(MotionEvent.ACTION_MOVE,2,45f+i*5); SystemClock.sleep(20) }
        event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,95f)
        event(MotionEvent.ACTION_UP,1,95f)
    }
    private fun screenshot(name: String) {
        runCatching { uiAutomation.takeScreenshot()?.let { bitmap ->
            File(targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        } }
    }
    private fun mapFingerprint(rect: Rect): Long {
        val bitmap = uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        var hash = 1L
        for(y in rect.top.coerceAtLeast(0) until rect.bottom.coerceAtMost(bitmap.height) step 8)
            for(x in rect.left.coerceAtLeast(0) until rect.right.coerceAtMost(bitmap.width) step 8) hash = hash * 31 + bitmap.getPixel(x,y)
        bitmap.recycle()
        return hash
    }
}
