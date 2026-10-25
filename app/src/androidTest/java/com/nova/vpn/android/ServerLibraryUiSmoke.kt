package com.nova.vpn.android

import android.app.Instrumentation
import android.app.Activity
import android.app.Application
import android.app.UiAutomation
import android.content.res.Configuration
import java.util.concurrent.atomic.AtomicReference
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.vpn.android.data.*
import java.io.File

/** Large synthetic library, real UI and encrypted persistence; isolated package only. */
internal object ServerLibraryUiSmoke {
    fun run(instrumentation: Instrumentation, style: String, theme: ThemeMode = ThemeMode.DARK) {
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
            val until = SystemClock.elapsedRealtime() + 45_000
            var attempts = 0
            while (SystemClock.elapsedRealtime() < until) {
                val candidate = find { node ->
                    val matches = node.text?.toString() == text || node.contentDescription?.toString() == text
                    var ancestor: AccessibilityNodeInfo? = node
                    var editable = false
                    var clickable = false
                    repeat(10) {
                        editable = editable || ancestor?.isEditable == true
                        clickable = clickable || ancestor?.isClickable == true
                        ancestor = ancestor?.parent
                    }
                    // A heading and the navigation item can share the same text.
                    // Choose a node with an actionable ancestor before returning it.
                    matches && (!action || !editable && clickable)
                }
                if (candidate?.isVisibleToUser == true) return candidate
                if (++attempts % 10 == 0) {
                    var parent = candidate?.parent
                    var scrollParent: AccessibilityNodeInfo? = null
                    while (parent != null) {
                        if (parent.isScrollable && parent.actionList.any {
                            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id ||
                                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
                        }) { scrollParent = parent; break }
                        parent = parent.parent
                    }
                    if (scrollParent != null) {
                        val targetBounds = android.graphics.Rect()
                        val scrollBounds = android.graphics.Rect()
                        candidate!!.getBoundsInScreen(targetBounds)
                        scrollParent.getBoundsInScreen(scrollBounds)
                        scrollParent.performAction(if (targetBounds.top < scrollBounds.top)
                            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    } else {
                        // Search both vertical viewports: controls and lazy server list.
                        // Offscreen Compose nodes may not exist in the accessibility tree.
                        val lists = mutableListOf<AccessibilityNodeInfo>()
                        fun visit(node: AccessibilityNodeInfo?) {
                            if (node == null) return
                            if (node.isScrollable && node.actionList.any {
                                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id ||
                                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
                            }) lists += node
                            for (i in 0 until node.childCount) visit(node.getChild(i))
                        }
                        visit(ui.rootInActiveWindow)
                        lists.getOrNull((attempts / 10 - 1) % lists.size.coerceAtLeast(1))?.let { scroll ->
                            // Compose can advertise both actions even at a boundary.
                            // Labels normally need the top; actions normally need the bottom.
                            // Reverse periodically so an offscreen lazy item is still discoverable.
                            val reverse = ((attempts / 10 - 1) / (lists.size.coerceAtLeast(1) * 4)) % 2 != 0
                            val forward = action != reverse
                            scroll.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        }
                    }
                }
                SystemClock.sleep(100)
            }
            val tree = buildString {
                fun visit(node: AccessibilityNodeInfo?, depth: Int) {
                    if (node == null) return
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    append(" ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                        .append(" desc=").append(node.contentDescription).append(" visible=").append(node.isVisibleToUser)
                        .append(" scrollable=").append(node.isScrollable).append(" bounds=").append(bounds)
                        .append(" actions=").append(node.actionList.map { it.id }).append('\n')
                    for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
                }
                visit(ui.rootInActiveWindow, 0)
            }
            File(context.getExternalFilesDir(null), "server-library-failed-$style.txt").writeText(tree)
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
        fun awaitEditorClosed() {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (find { it.text?.toString() == "Настройки сервера" } == null) {
                    instrumentation.waitForIdleSync()
                    return
                }
                SystemClock.sleep(100)
            }
            error("Server editor did not close after saving")
        }
        fun enter(text: String) {
            val field = requireNotNull(find { it.isEditable })
            check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }))
        }
        val latest = AtomicReference<MainActivity?>()
        val application = context.applicationContext as Application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { if (activity is MainActivity) latest.set(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        fun capture(label: String) {
            val image = requireNotNull(ui.takeScreenshot())
            File(context.getExternalFilesDir(null), "server-library-$style-${theme.name}-$label.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
        }
        fun awaitLifecycle(previous: MainActivity?, orientation: Int) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < deadline) {
                val current = latest.get()
                if (current != null && current !== previous &&
                    context.resources.configuration.orientation == orientation) {
                    instrumentation.waitForIdleSync()
                    waitFor("Найдено: 1 из 2000")
                    waitFor("Мой игровой узел")
                    return
                }
                SystemClock.sleep(100)
            }
            error("Activity did not restore search after lifecycle change")
        }
        instrumentation.runOnMainSync { application.registerActivityLifecycleCallbacks(callbacks) }
        try {
            check(ui.setRotation(UiAutomation.ROTATION_FREEZE_0))
            AppPreferencesStore(context).apply {
                // Library lifecycle test starts after onboarding; onboarding has its own flow.
                dismissTutorial()
                setAutoConnectEnabled(false)
                setUpdateChecksEnabled(false)
                setAppearance(Appearance(DesignStyle.valueOf(style), theme))
                setServerGrouping(ServerGrouping.NONE.name)
                setServerListOptions(ServerSort.MANUAL.name, false)
            }
            AutoServerSettingsStore(context).use { it.save(false, emptySet()) }
            val fixture = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@fixture.example:443?type=xhttp#Fixture").single()
            val profiles = List(2000) { index -> fixture.copy(id = "library-$index", name = "Узел %04d".format(index),
                userSettings = ProfileUserSettings(favorite = index % 2 == 0, order = index.toLong())) }
            vault.updateSnapshot { ProfileSnapshot.migrate(profiles) }
            SelectedProfileStore(context).save("library-0")
            instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
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
            awaitEditorClosed()
            waitFor("Мой игровой узел")
            stage = "search-personal-name"
            // Replace the query directly; reset is in the horizontally scrollable filters.
            enter("МОЙ ИГРОВОЙ")
            waitFor("Найдено: 1 из 2000")
            stage = "reopen-personal-actions"
            click("Название и порядок")
            click("Ниже")
            awaitEditorClosed()
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
            capture("portrait")
            stage = "rotate-landscape"
            var previous = latest.get()
            check(ui.setRotation(UiAutomation.ROTATION_FREEZE_90))
            awaitLifecycle(previous, Configuration.ORIENTATION_LANDSCAPE)
            capture("landscape")
            stage = "rotate-portrait"
            previous = latest.get()
            check(ui.setRotation(UiAutomation.ROTATION_FREEZE_0))
            awaitLifecycle(previous, Configuration.ORIENTATION_PORTRAIT)
            stage = "recreate-activity"
            previous = latest.get()
            instrumentation.runOnMainSync { requireNotNull(previous).recreate() }
            awaitLifecycle(previous, Configuration.ORIENTATION_PORTRAIT)
            capture("recreated")
            check(vault.load().single { it.id == "library-0" }.userSettings == edited.userSettings)
            check(SelectedProfileStore(context).load() == "library-0")
        } catch (error: Throwable) {
            ui.takeScreenshot()?.let { image ->
                File(context.getExternalFilesDir(null), "server-library-failed-$style.png").outputStream().use {
                    image.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                image.recycle()
            }
            throw IllegalStateException("$stage: ${error.message}", error)
        } finally {
            instrumentation.runOnMainSync {
                latest.get()?.finish()
                application.unregisterActivityLifecycleCallbacks(callbacks)
            }
            ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
            instrumentation.waitForIdleSync()
            FullTransfer.restore(context, FullTransfer.read(backup, "Synthetic-library-backup-2026"))
        }
    }
}
