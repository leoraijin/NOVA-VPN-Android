package com.nova.vpn.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.vpn.android.data.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Actual encrypted vault and UI; synthetic data in an isolated package only. */
internal object DeleteAllProfilesSmoke {
    fun runStore(instrumentation: Instrumentation) = runBlocking {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val backup = FullTransfer.export(context, "Synthetic-delete-store-backup-2026")
        val vault = ProfileVault(context)
        val routes = AppRoutingStore(context).loadPresets()
        val identities = IdentityVault(context).load()
        val preferences = context.getSharedPreferences("app_preferences", 0).all.toMap()
        try {
            val node = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@fixture.example:443#Fixture").single()
            val source = Subscription(id = "delete-source", name = "Fixture", url = "https://fixture.example/sub", intervalHours = 6, lastSuccess = 123)
            for (response in listOf("200", "304", "401")) {
                val subscribed = node.copy(id = "subscribed", subscriptionUrl = source.url)
                val initial = ProfileSnapshot(listOf(node, subscribed), listOf(source),
                    setOf(SubscriptionMembership(subscribed.id, source.id)), setOf(node.id))
                vault.updateSnapshot(ProfileVault.replacementGeneration) { initial }
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val late = executor.submit {
                        runBlocking {
                            SubscriptionRepository(context) { _, _, _ ->
                                entered.countDown()
                                check(release.await(20, TimeUnit.SECONDS))
                                when (response) {
                                    "200" -> SubscriptionFetcher.Response("vless://11111111-1111-4111-8111-111111111111@late.example:8443#Late")
                                    "304" -> SubscriptionFetcher.Response("", status = 304)
                                    else -> throw SubscriptionFetcher.FetchFailure(401, 0)
                                }
                            }.refresh(source.id)
                        }
                    }
                    check(entered.await(10, TimeUnit.SECONDS))
                    val generation = ProfileVault.replacementGeneration
                    vault.deleteAllConfigurations()
                    check(ProfileVault.replacementGeneration > generation)
                    val file = File(context.filesDir, "profiles.vault")
                    val emptyBytes = file.readBytes()
                    check(ProfileVault(context).loadSnapshot() == ProfileSnapshot())
                    check(!String(emptyBytes, Charsets.UTF_8).contains("profiles"))
                    release.countDown()
                    late.get(15, TimeUnit.SECONDS)
                    check(vault.loadSnapshot() == ProfileSnapshot()) { "Late HTTP $response restored profiles" }
                    check(file.readBytes().contentEquals(emptyBytes)) { "Late HTTP $response rewrote vault" }
                    val lateReport = ProfileLinkParser.ImportResult(listOf(node), emptyList())
                    check(runCatching { vault.updateSnapshot(generation) { it.importManual(listOf(node)) } }
                        .exceptionOrNull() is ProfileVault.ReplacementSupersededException)
                    check(runCatching { SubscriptionRepository(context).importReport(source.url, false, lateReport, generation) }
                        .exceptionOrNull() is ProfileVault.ReplacementSupersededException)
                    check(vault.loadSnapshot() == ProfileSnapshot()) { "Pre-delete import recreated the catalog" }
                    check(file.readBytes().contentEquals(emptyBytes)) { "Rejected import rewrote the encrypted vault" }
                    synchronized(ProfileVault.lock) {
                        val boundary = ProfileVault.replacementGeneration
                        file.writeText("corrupt-synthetic-vault")
                        check(runCatching { vault.deleteAllConfigurations() }.isFailure)
                        check(file.readText() == "corrupt-synthetic-vault")
                        check(ProfileVault.replacementGeneration == boundary)
                        file.writeBytes(emptyBytes)
                    }
                    vault.deleteAllConfigurations()
                    check(vault.loadSnapshot() == ProfileSnapshot())
                } finally { release.countDown(); executor.shutdownNow() }
            }
            check(AppRoutingStore(context).loadPresets() == routes)
            check(IdentityVault(context).load() == identities)
            check(context.getSharedPreferences("app_preferences", 0).all == preferences)
        } finally {
            FullTransfer.restore(context, FullTransfer.read(backup, "Synthetic-delete-store-backup-2026"))
        }
    }

    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val backup = FullTransfer.export(context, "Synthetic-delete-backup-2026")
        val vault = ProfileVault(context)
        val ui = instrumentation.uiAutomation
        var activity: Activity? = null
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        fun find(text: String): AccessibilityNodeInfo? {
            fun visit(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.text?.toString() == text && node.isVisibleToUser) return node
                for (i in 0 until node.childCount) visit(node.getChild(i))?.let { return it }
                return null
            }
            return visit(ui.rootInActiveWindow)
        }
        fun await(description: String, condition: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < until) {
                if (condition()) return
                SystemClock.sleep(100)
            }
            error("Timeout: $description")
        }
        fun click(text: String) {
            await(text) { find(text) != null }
            var node = requireNotNull(find(text))
            while (!node.isClickable) node = requireNotNull(node.parent)
            check(node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Cannot click $text" }
            instrumentation.waitForIdleSync()
        }
        try {
            AppPreferencesStore(context).apply {
                dismissTutorial()
                markNotificationPermissionRequested()
                setUpdateChecksEnabled(false)
                setAutoConnectEnabled(false)
            }
            val fixture = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@fixture.example:443?type=xhttp#Fixture").single()
            val source = Subscription(id = "delete-source", name = "Fixture source", url = "https://fixture.example/sub", intervalHours = 6)
            val manual = fixture.copy(id = "delete-manual", name = "Manual")
            val subscribed = fixture.copy(id = "delete-subscribed", name = "Subscribed", subscriptionUrl = source.url)
            vault.updateSnapshot { ProfileSnapshot(listOf(manual, subscribed), listOf(source),
                setOf(SubscriptionMembership(subscribed.id, source.id)), setOf(manual.id)) }
            SelectedProfileStore(context).save(subscribed.id)
            AutoServerSettingsStore(context).use { it.save(true, setOf(manual.id, subscribed.id)) }
            val routesBefore = AppRoutingStore(context).loadPresets()
            val identitiesBefore = IdentityVault(context).load()
            val late = executor.submit {
                runBlocking {
                    SubscriptionRepository(context) { _, _, _ ->
                        entered.countDown()
                        check(release.await(60, TimeUnit.SECONDS))
                        SubscriptionFetcher.Response("vless://11111111-1111-4111-8111-111111111111@late.example:8443#Late")
                    }.refresh(source.id)
                }
            }
            check(entered.await(10, TimeUnit.SECONDS))
            val beforeCancel = vault.loadSnapshot()
            val tokenBefore = VpnUserRequests.gate.snapshot()
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            click("Серверы")
            click("Удалить все конфиги VPN")
            await("confirmation") { find("Удалить все конфиги VPN?") != null }
            click("Отмена")
            check(vault.loadSnapshot() == beforeCancel) { "Cancel changed the vault" }
            check(SelectedProfileStore(context).load() == subscribed.id)
            check(VpnUserRequests.gate.isCurrent(tokenBefore)) { "Cancel invalidated a connection request" }
            AutoServerSettingsStore(context).use { check(it.snapshot().enabled) }
            click("Удалить все конфиги VPN")
            click("Удалить всё")
            await("empty persisted catalog") { vault.loadSnapshot() == ProfileSnapshot() && SelectedProfileStore(context).load() == null }
            await("dismissed confirmation") { find("Удалить все конфиги VPN?") == null }
            check(!VpnUserRequests.gate.isCurrent(tokenBefore)) { "Old start request survived deletion" }
            AutoServerSettingsStore(context).use { check(!it.snapshot().enabled && it.snapshot().candidateIds.isEmpty()) }
            check(AppRoutingStore(context).loadPresets() == routesBefore)
            check(IdentityVault(context).load() == identitiesBefore)
            release.countDown()
            late.get(15, TimeUnit.SECONDS)
            check(vault.loadSnapshot() == ProfileSnapshot()) { "Late subscription resurrected deleted servers" }
            SubscriptionScheduling.reconcile(context)
            val file = File(context.filesDir, "profiles.vault")
            val emptyBytes = file.readBytes()
            synchronized(ProfileVault.lock) {
                val generation = ProfileVault.replacementGeneration
                file.writeText("corrupt-synthetic-vault")
                check(runCatching { vault.deleteAllConfigurations() }.isFailure)
                check(file.readText() == "corrupt-synthetic-vault")
                check(ProfileVault.replacementGeneration == generation)
                file.writeBytes(emptyBytes)
            }
            vault.deleteAllConfigurations() // Empty catalog is safe to clear again.
            instrumentation.runOnMainSync { activity?.finish() }
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            click("Серверы")
            await("empty list after activity recreation") { find("Найдено: 0 из 0") != null }
            val image = requireNotNull(ui.takeScreenshot())
            File(context.getExternalFilesDir(null), "delete-all-empty.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        } finally {
            release.countDown()
            executor.shutdownNow()
            instrumentation.runOnMainSync { activity?.finish() }
            // The STOP service must finish before the existing restore transaction can run.
            await("test service released") {
                runCatching { FullTransfer.restore(context, FullTransfer.read(backup, "Synthetic-delete-backup-2026")) }.isSuccess
            }
        }
    }
}