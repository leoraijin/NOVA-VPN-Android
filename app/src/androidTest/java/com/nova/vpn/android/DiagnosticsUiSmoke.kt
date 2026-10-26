package com.nova.vpn.android

import android.accessibilityservice.AccessibilityService
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.vpn.android.data.*
import org.json.JSONObject
import java.io.File

internal object DiagnosticsUiSmoke {
    fun run(instrumentation: Instrumentation, theme: ThemeMode = ThemeMode.LIGHT) {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val preferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val original = preferences.all.toMap()
        val selected = SelectedProfileStore(context).load()
        val routing = AppRoutingStore(context).loadPresets()
        val ui = instrumentation.uiAutomation
        val exportStem = "nova-diagnostic-test-${java.util.UUID.randomUUID()}"
        // MuMu's document provider can default to its shared folder instead of Downloads.
        // Read only the fresh unique test filename, then remove it from either location.
        val exportRoots = listOf("/sdcard/Download", "/mnt/shared/MuMuShared")
        val exportPaths = listOf("json", "txt").flatMap { extension -> exportRoots.map { "$it/$exportStem.$extension" } }
        var activity: android.app.Activity? = null
        var pendingAutomaticFixture: String? = null
        fun visit(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
            for (index in 0 until node.childCount) visit(node.getChild(index), text)?.let { return it }
            return null
        }
        fun scroll(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (node.isScrollable && !node.isEditable) node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            for (index in 0 until node.childCount) scroll(node.getChild(index))
        }
        fun waitFor(text: String): AccessibilityNodeInfo {
            repeat(80) { attempt ->
                visit(ui.rootInActiveWindow, text)?.let { return it }
                if (attempt in setOf(15, 30, 45, 60)) scroll(ui.rootInActiveWindow)
                SystemClock.sleep(100)
            }
            error("Missing diagnostic UI control: $text")
        }
        fun click(text: String) {
            var node: AccessibilityNodeInfo? = waitFor(text)
            repeat(8) {
                if (node?.isClickable == true) { check(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)); return }
                node = node?.parent
            }
            error("Unclickable diagnostic UI control: $text")
        }
        fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            if (node == null) return null
            if (predicate(node)) return node
            for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
            return null
        }
        fun saveReport(expected: String, json: Boolean) {
            val extension = if (json) "json" else "txt"
            val exportName = "$exportStem.$extension"
            val paths = exportRoots.map { "$it/$exportName" }
            var filename: AccessibilityNodeInfo? = null
            repeat(80) {
                val root = ui.rootInActiveWindow
                if (root?.packageName?.toString()?.endsWith(".documentsui") == true) {
                    filename = find(root) { it.isEditable && it.text?.toString() == "nova-diagnostics.$extension" }
                }
                if (filename == null) SystemClock.sleep(100)
            }
            val field = requireNotNull(filename) { "System document picker filename field unavailable" }
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, exportName)
            }))
            val root = ui.rootInActiveWindow
            check(root?.packageName?.toString()?.endsWith(".documentsui") == true)
            var save = find(root) { it.text?.toString()?.lowercase() in setOf("сохранить", "save") }
            while (save != null && !save!!.isClickable) save = save!!.parent
            check(save?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "System document save action unavailable" }
            waitFor("Закрыть")
            val expectedBytes = expected.toByteArray(Charsets.UTF_8)
            var actual = byteArrayOf()
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (!actual.contentEquals(expectedBytes) && SystemClock.elapsedRealtime() < deadline) {
                for (path in paths) {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("cat $path")).use { actual = it.readBytes() }
                    if (actual.contentEquals(expectedBytes)) break
                }
                if (!actual.contentEquals(expectedBytes)) SystemClock.sleep(100)
            }
            check(actual.contentEquals(expectedBytes)) { "Saved diagnostic $extension differs from its preview or is outside the known local test folders" }
            if (json) check(JSONObject(actual.toString(Charsets.UTF_8)).getInt("schema") == 1)
            check(!actual.toString(Charsets.UTF_8).contains("PRIVATE_TOKEN") && !actual.toString(Charsets.UTF_8).contains("secret.example"))
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS SAF $extension export: actual UTF-8 bytes equal preview, no secrets\n") })
        }
        try {
            AppPreferencesStore(context).apply { dismissTutorial(); setAutoConnectEnabled(false); setUpdateChecksEnabled(false) }
            AutoServerSettingsStore.pin(context)
            for (style in DesignStyle.entries) {
                AppPreferencesStore(context).setAppearance(Appearance(style, theme))
                if (style == DesignStyle.entries.first()) {
                    pendingAutomaticFixture = "diagnostic-ui-${java.util.UUID.randomUUID()}"
                    check(AutomaticProbePause.dispatch(pendingAutomaticFixture!!, Long.MIN_VALUE) {})
                }
                VpnDiagnostics.reset()
                VpnDiagnostics.coreMessage("xhttp HTTP 404 PRIVATE_TOKEN secret.example")
                activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                instrumentation.waitForIdleSync()
                val detail = when (style) {
                    DesignStyle.IOS_LIQUID_GLASS -> "О соединении"
                    DesignStyle.WINDOWS_11 -> "Диагностика"
                    else -> "Состояние и диагностика"
                }
                click(detail)
                click("Открыть диагностику")
                pendingAutomaticFixture?.let { request ->
                    val waiting = "Завершается автоматическое переключение. Дождитесь результата или отключите VPN."
                    waitFor(waiting)
                    var probeButton: AccessibilityNodeInfo? = waitFor("Проверить профиль")
                    val probeAncestors = mutableListOf<String>()
                    repeat(8) {
                        probeAncestors += "${probeButton?.className}:enabled=${probeButton?.isEnabled}:clickable=${probeButton?.isClickable}"
                        if (probeButton?.isClickable != true) probeButton = probeButton?.parent
                    }
                    check(probeButton?.isClickable == true && probeButton?.isEnabled == false) {
                        "Diagnostic button not confirmed disabled: ${probeAncestors.joinToString(" / ")}"
                    }
                    check(AutomaticProbePause.paused)
                    check(!AutomaticProbePause.dispatch("forbidden-$request", Long.MIN_VALUE) { error("Automatic dispatch during diagnostic modal") })
                    AutomaticProbePause.settled(request)
                    pendingAutomaticFixture = null
                    repeat(80) {
                        if (visit(ui.rootInActiveWindow, waiting) != null) SystemClock.sleep(50)
                    }
                    check(visit(ui.rootInActiveWindow, waiting) == null) { "Diagnostic pending message did not clear after native acknowledgement" }
                    instrumentation.sendStatus(0, android.os.Bundle().apply {
                        putString("stream", "PASS diagnostic modal: pending apply disables probe, blocks new automation, acknowledgement clears wait\n")
                    })
                }
                waitFor("JSON")
                waitFor("Ошибка транспорта")
                click("JSON")
                instrumentation.waitForIdleSync()
                val formatDeadline = SystemClock.elapsedRealtime() + 8_000
                while (find(ui.rootInActiveWindow) { it.text?.toString()?.trimStart()?.startsWith("{") == true } == null &&
                    SystemClock.elapsedRealtime() < formatDeadline) SystemClock.sleep(50)
                check(find(ui.rootInActiveWindow) { it.text?.toString()?.trimStart()?.startsWith("{") == true } != null) {
                    "JSON preview was not rendered before copying"
                }
                click("Копировать")
                instrumentation.waitForIdleSync()
                var copied: String? = null
                instrumentation.runOnMainSync {
                    copied = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .primaryClip?.getItemAt(0)?.text?.toString()
                }
                check(JSONObject(requireNotNull(copied)).getInt("schema") == 1)
                check(!copied!!.contains("PRIVATE_TOKEN") && !copied!!.contains("secret.example"))
                val screenshot = requireNotNull(ui.takeScreenshot())
                File(context.getExternalFilesDir(null), "diagnostics-${theme.name.lowercase()}-${style.name.lowercase()}.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
                click("Сохранить")
                if (style == DesignStyle.entries.first()) {
                    saveReport(requireNotNull(copied), json = true)
                    click("Текст")
                    instrumentation.waitForIdleSync()
                    val textDeadline = SystemClock.elapsedRealtime() + 8_000
                    while (find(ui.rootInActiveWindow) { it.text?.toString()?.startsWith("NOVA VPN diagnostic ") == true } == null &&
                        SystemClock.elapsedRealtime() < textDeadline) SystemClock.sleep(50)
                    check(find(ui.rootInActiveWindow) { it.text?.toString()?.startsWith("NOVA VPN diagnostic ") == true } != null)
                    click("Копировать")
                    instrumentation.waitForIdleSync()
                    var text: String? = null
                    instrumentation.runOnMainSync {
                        text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()
                    }
                    check(text?.startsWith("NOVA VPN diagnostic ") == true)
                    click("Сохранить")
                    saveReport(requireNotNull(text), json = false)
                }
                else {
                    SystemClock.sleep(500)
                    check(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                }
                waitFor("Закрыть")
                click("Закрыть")
                check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
                check(SelectedProfileStore(context).load() == selected)
                check(AppRoutingStore(context).loadPresets() == routing)
                instrumentation.runOnMainSync { activity?.finish() }
                activity = null
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "PASS diagnostic preview/JSON/copy/${if (style == DesignStyle.entries.first()) "save" else "save-cancel"}: ${style.name}/${theme.name}\n") })
            }
        } finally {
            pendingAutomaticFixture?.let(AutomaticProbePause::settled)
            exportPaths.forEach { path -> android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("rm -f $path")).use { it.readBytes() } }
            instrumentation.runOnMainSync { activity?.finish() }
            val editor = preferences.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            check(editor.commit())
        }
    }
}
