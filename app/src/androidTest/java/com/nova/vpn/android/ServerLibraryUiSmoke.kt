package com.nova.vpn.android

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.vpn.android.data.*
import java.io.File

/** Large synthetic library, real UI and encrypted persistence; isolated package only. */
internal object ServerLibraryUiSmoke {
    fun run(instrumentation: Instrumentation, style: String) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.nova.vpn.android.usbtest.realitypreview")
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val backup = FullTransfer.export(context, "Synthetic-library-backup-2026")
        val vault = ProfileVault(context)
        val ui = instrumentation.uiAutomation
        var stage = "prepare"
        fun find(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
            fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (predicate(node)) return node
                for (index in 0 until node.childCount) visit(node.getChild(index))?.let { return it }
                return null
            }
            return visit(ui.rootInActiveWindow)
        }
        fun waitFor(text: String, action: Boolean = false): AccessibilityNodeInfo {
            val until = SystemClock.elapsedRealtime() + 15_000
            var attempts = 0
            while (SystemClock.elapsedRealtime() < until) {
                find { node ->
                    val matches = node.text?.toString() == text || node.contentDescription?.toString() == text
                    var ancestor: AccessibilityNodeInfo? = node
                    var editable = false
                    repeat(6) { editable = editable || ancestor?.isEditable == true; ancestor = ancestor?.parent }
                    matches && (!action || !editable)
                }?.let { return it }
                if (++attempts % 25 == 0) {
                    find { it.isScrollable && !it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                }
                SystemClock.sleep(100)
            }
            error("Missing library UI control: $text")
        }
        fun click(text: String) {
            var node: AccessibilityNodeInfo? = waitFor(text, action = true)
            repeat(10) {
                if (node?.isClickable == true) {
                    check(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)); return
                }
                node = node?.parent
            }
            error("Unclickable library UI control: $text")
        }
        fun enter(text: String) {
            val field = requireNotNull(find { it.isEditable })
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }))
        }
        var activity: MainActivity? = null
        try {
            AppPreferencesStore(context).apply {
                setAppearance(Appearance(DesignStyle.valueOf(style), ThemeMode.DARK))
                setServerGrouping(ServerGrouping.NONE.name)
                setServerListOptions(ServerSort.MANUAL.name, false)
            }
            AutoServerSettingsStore(context).use { it.save(false, emptySet()) }
            val fixture = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@fixture.example:443?type=xhttp#Fixture").single()
            val profiles = List(2000) { index -> fixture.copy(id = "library-$index", name = "Узел %04d".format(index),
                userSettings = ProfileUserSettings(favorite = index % 2 == 0, order = index.toLong())) }
            vault.updateSnapshot { ProfileSnapshot.migrate(profiles) }
            SelectedProfileStore(context).save("library-0")
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            instrumentation.waitForIdleSync()
            click("Серверы")
            waitFor("Найдено: 2000 из 2000")
            enter("Узел 0000")
            waitFor("Найдено: 1 из 2000")
            stage = "open-original-actions"
            if (style == DesignStyle.IOS_LIQUID_GLASS.name) click("Узел 0000")
            click("Название и порядок")
            waitFor("Моё название")
            enter("Мой игровой узел")
            click("Сохранить")
            waitFor("Мой игровой узел")
            stage = "search-personal-name"
            click("Сбросить")
            enter("МОЙ ИГРОВОЙ")
            waitFor("Найдено: 1 из 2000")
            stage = "reopen-personal-actions"
            click("Название и порядок")
            click("Ниже")
            stage = "await-persisted-order"
            val untilSaved = SystemClock.elapsedRealtime() + 10_000
            while (vault.load().single { it.id == "library-0" }.userSettings.order != 1L &&
                SystemClock.elapsedRealtime() < untilSaved) SystemClock.sleep(100)
            val edited = vault.load().single { it.id == "library-0" }
            check(edited.userSettings.personalName == "Мой игровой узел" && edited.userSettings.order == 1L) { "Personal name/order not persisted" }
            check(edited.name == profiles.first().name && edited.configurationKey() == profiles.first().configurationKey())
            check(vault.load().single { it.id == "library-1" }.userSettings.order == 0L)
            check(SelectedProfileStore(context).load() == "library-0")
            check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
            val image = requireNotNull(ui.takeScreenshot())
            File(context.getExternalFilesDir(null), "server-library-$style.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        } catch (error: Throwable) {
            ui.takeScreenshot()?.let { image ->
                File(context.getExternalFilesDir(null), "server-library-failed-$style.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
            throw IllegalStateException("$stage: ${error.message}", error)
        } finally {
            activity?.let { target -> instrumentation.runOnMainSync { target.finish() } }
            instrumentation.waitForIdleSync()
            FullTransfer.restore(context, FullTransfer.read(backup, "Synthetic-library-backup-2026"))
        }
    }
}
