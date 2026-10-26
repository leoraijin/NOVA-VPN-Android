package com.nova.vpn.android

import android.accessibilityservice.AccessibilityService
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

internal object SubscriptionUiSmoke {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        val ui = instrumentation.uiAutomation
        fun find(text: String): AccessibilityNodeInfo? {
            fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
                for (index in 0 until node.childCount) visit(node.getChild(index))?.let { return it }
                return null
            }
            return visit(ui.rootInActiveWindow)
        }
        fun waitFor(text: String): AccessibilityNodeInfo {
            repeat(60) { attempt ->
                find(text)?.let { return it }
                if (attempt == 20 || attempt == 40) {
                    fun scroll(node: AccessibilityNodeInfo?) {
                        if (node == null) return
                        if (node.isScrollable && !node.isEditable) node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                        for (index in 0 until node.childCount) scroll(node.getChild(index))
                    }
                    scroll(ui.rootInActiveWindow)
                }
                SystemClock.sleep(100)
            }
            error("Missing UI control: $text")
        }
        fun click(text: String) {
            var node: AccessibilityNodeInfo? = waitFor(text)
            repeat(8) {
                if (node?.isClickable == true) { check(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)); return }
                node = node?.parent
            }
            error("Unclickable UI control: $text")
        }
        fun screenshot(name: String) {
            val image = requireNotNull(ui.takeScreenshot())
            File(context.getExternalFilesDir(null), name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        click("Серверы")
        waitFor("Поиск серверов")
        screenshot("servers-new-ui.png")
        click("Подписки")
        waitFor("Second")
        waitFor("Серверов: 1 · +0 / ~0 / −0")
        screenshot("subscriptions-new-ui.png")
        click("Добавить")
        waitFor("HTTPS-ссылка")
        waitFor("Новая подписка")
        screenshot("subscription-editor-new-ui.png")
        check(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        click("Закрыть")
        val label = waitFor("Поиск серверов")
        var editable: AccessibilityNodeInfo? = label
        if (editable?.isEditable != true) {
            val roots = ui.rootInActiveWindow
            fun firstEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null || node.isEditable) return node
                for (index in 0 until node.childCount) firstEditable(node.getChild(index))?.let { return it }
                return null
            }
            editable = firstEditable(roots)
        }
        check(requireNotNull(editable).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "no_such_server_fixture") }))
        waitFor("Ничего не найдено. Измените поиск или сбросьте фильтры.")
        click("Сбросить")
        waitFor("Fixture")
        click("Fixture")
        waitFor("★ Убрать из избранного")
        click("★ Убрать из избранного")
        waitFor("☆ В избранное")
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        check(com.nova.vpn.android.data.SelectedProfileStore(context).load() == "fixture-node")
    }
}
