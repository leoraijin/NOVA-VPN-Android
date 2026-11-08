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
    private class SyntheticDeleteProcessDeath : Error("synthetic process death")
    fun runStore(instrumentation: Instrumentation) = runBlocking {
        val context = instrumentation.targetContext
        check(isIsolatedUsbTestPackage(context.packageName))
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        withStoreDiagnostics(instrumentation) { stage ->
        stage("export-backup")
        val backup = FullTransfer.export(context, "Synthetic-delete-store-backup-2026")
        val vault = ProfileVault(context)
        val routes = AppRoutingStore(context).loadPresets()
        val identities = IdentityVault(context).load()
        val preferences = context.getSharedPreferences("app_preferences", 0).all.toMap()
        try {
            val node = ProfileLinkParser.parseText("vless://11111111-1111-4111-8111-111111111111@fixture.example:443#Fixture").single()
            val source = Subscription(id = "delete-source", name = "Fixture", url = "https://fixture.example/sub", intervalHours = 6, lastSuccess = 123)
            for (response in listOf("200", "304", "401")) {
                stage("initialize-$response")
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
                    stage("waiting-provider-$response")
                    check(entered.await(10, TimeUnit.SECONDS))
                    stage("delete-$response")
                    val generation = ProfileVault.replacementGeneration
                    vault.deleteAllConfigurations()
                    check(ProfileVault.replacementGeneration > generation)
                    val file = File(context.filesDir, "profiles.vault")
                    val emptyBytes = file.readBytes()
                    check(ProfileVault(context).loadSnapshot() == ProfileSnapshot())
                    check(!String(emptyBytes, Charsets.UTF_8).contains("profiles"))
                    release.countDown()
                    stage("waiting-late-$response")
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
                    stage("corruption-guard-$response")
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
            stage("checking-preserved-settings")
            check(AppRoutingStore(context).loadPresets() == routes)
            check(IdentityVault(context).load() == identities)
            check(context.getSharedPreferences("app_preferences", 0).all == preferences)
            // Multi-server deletion/rollback uses real encrypted files in this isolated package.
            val assigned=AppRoutingSettings(multiServer=MultiServerSettings(1,"subscribed",listOf(
                MultiServerRule(packageName="org.example.fixture",target=ServerRouteTarget(ServerRouteKind.PROFILE,node.id)))))
            for(checkpointStage in listOf("profiles-cleared","assignments-cleared","selection-cleared")) {
                stage("checkpoint-$checkpointStage")
                vault.restoreSnapshot(ProfileSnapshot.migrate(listOf(node.copy(id="subscribed"),node)))
                AppRoutingStore(context).updatePresets { RoutingPresets.migrate(assigned) }
                SelectedProfileStore(context).save("subscribed")
                val old=vault.loadSnapshot()
                val routingBefore=AppRoutingStore(context).loadPresets()
                check(runCatching { FullTransfer.deleteAllConfigurations(context) { if(it==checkpointStage)throw java.io.IOException("synthetic checkpoint") } }.isFailure)
                check(vault.loadSnapshot()==old)
                check(AppRoutingStore(context).loadPresets()==routingBefore)
                check(SelectedProfileStore(context).load()=="subscribed")
                check(runCatching { FullTransfer.deleteAllConfigurations(context) { if(it==checkpointStage)throw SyntheticDeleteProcessDeath() } }.isFailure)
                // The journal survives Error, and the ordinary next store access restores all files.
                check(vault.loadSnapshot()==old)
                check(AppRoutingStore(context).loadPresets()==routingBefore)
                check(SelectedProfileStore(context).load()=="subscribed")
            }
            vault.deleteAllConfigurations()
            check(vault.loadSnapshot()==ProfileSnapshot())
            check(AppRoutingStore(context).loadPresets().referencedProfileIds.isEmpty())
            check(SelectedProfileStore(context).load()==null)
        } finally {
            stage("restore-backup")
            FullTransfer.restore(context, FullTransfer.read(backup, "Synthetic-delete-store-backup-2026"))
        }
        }
    }

    fun runProcessCut(instrumentation: Instrumentation, phase: String) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.nova.vpn.android.usbtest.completionpreview")
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val password = "Synthetic-delete-process-cut-2026"
        val backup = File(context.filesDir, "delete-process-cut-original.nova")
        val expected = File(context.filesDir, "delete-process-cut-expected.json")
        val journal = File(context.filesDir, "restore-pending.vault")
        val vault = ProfileVault(context)
        val routing = AppRoutingStore(context)
        val selection = SelectedProfileStore(context)
        val cuts = setOf("profiles-cleared", "assignments-cleared", "selection-cleared")
        fun receipt(cut: String) = File(context.filesDir, "delete-process-cut-$cut.json")
        fun durable(file: File, value: String) {
            java.io.FileOutputStream(file).use { output ->
                output.write(value.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
        }
        fun snapshot() = org.json.JSONObject()
            .put("profiles", ProfileDocument.encodeSnapshot(vault.loadSnapshot()))
            .put("routing", routing.loadPresets().toJson())
            .put("selected", selection.load() ?: org.json.JSONObject.NULL)
            .toString()
        fun verify() {
            check(snapshot() == expected.readText()) { "Catalog, routing and selection did not recover together" }
            check(!journal.exists() && !File(journal.path + ".bak").exists() && !FullTransfer.recoverPending(context))
        }

        withStoreDiagnostics(instrumentation) { stage ->
            stage(phase)
            when (phase) {
                "delete-process-prepare" -> {
                    check(!backup.exists() && !expected.exists() && !journal.exists() && cuts.none { receipt(it).exists() })
                    durable(backup, FullTransfer.export(context, password))
                    val first = ProfileLinkParser.parseText(
                        "vless://11111111-1111-4111-8111-111111111111@delete-process.invalid:443#DeleteFixture"
                    ).single().copy(id = "delete-process-first")
                    val second = first.copy(id = "delete-process-second", name = "DeleteFixtureSecond")
                    vault.restoreSnapshot(ProfileSnapshot.migrate(listOf(first, second)))
                    val assigned = AppRoutingSettings(multiServer = MultiServerSettings(1, first.id, listOf(
                        MultiServerRule(packageName = "org.example.fixture", target = ServerRouteTarget(ServerRouteKind.PROFILE, second.id))
                    )))
                    routing.updatePresets { RoutingPresets.migrate(assigned) }
                    selection.save(first.id)
                    durable(expected, snapshot())
                    verify()
                }
                "delete-process-verify" -> verify()
                "delete-process-cleanup" -> {
                    FullTransfer.restore(context, FullTransfer.read(backup.readText(), password))
                    check(backup.delete())
                    if (expected.exists()) check(expected.delete())
                    cuts.forEach { cut -> if (receipt(cut).exists()) check(receipt(cut).delete()) }
                }
                else -> {
                    val cut = phase.removePrefix("delete-process-cut-")
                    check(cut in cuts && !receipt(cut).exists())
                    verify()
                    FullTransfer.deleteAllConfigurations(context) { checkpoint ->
                        if (checkpoint == cut) {
                            val bytes = journal.readBytes()
                            check(bytes.isNotEmpty() && !bytes.toString(Charsets.ISO_8859_1).contains("DeleteFixture"))
                            val stat = File("/proc/self/stat").readText()
                            val birth = stat.substringAfterLast(") ").split(" ")[19]
                            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                                .joinToString("") { "%02x".format(it) }
                            durable(receipt(cut), org.json.JSONObject().put("cut", cut)
                                .put("pid", android.os.Process.myPid()).put("birthTicks", birth)
                                .put("journalSha256", sha).toString())
                            android.os.Process.killProcess(android.os.Process.myPid())
                            error("Physical process cut failed")
                        }
                    }
                    error("Delete did not reach the requested checkpoint")
                }
            }
        }
    }

    private fun <T> withStoreDiagnostics(instrumentation:Instrumentation,action:((String)->Unit)->T):T {
        val context=instrumentation.targetContext
        val activity=instrumentation.startActivitySync(Intent(context,StorageTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val finished=CountDownLatch(1)
        val stage=java.util.concurrent.atomic.AtomicReference("starting")
        kotlin.concurrent.thread(name="nova-delete-store-watchdog",isDaemon=true) {
            if(!finished.await(30,TimeUnit.SECONDS))runCatching {
                val threads=org.json.JSONArray()
                Thread.getAllStackTraces().entries.sortedBy {it.key.id}.forEach {entry->
                    threads.put(org.json.JSONObject().put("name",entry.key.name).put("id",entry.key.id)
                        .put("state",entry.key.state.name).put("frames",org.json.JSONArray(entry.value.map{it.toString()})))
                }
                val receipt=org.json.JSONObject().put("pid",android.os.Process.myPid()).put("stage",stage.get())
                    .put("elapsedRealtime",SystemClock.elapsedRealtime()).put("threads",threads)
                java.io.FileOutputStream(File(context.getExternalFilesDir(null),"delete-all-threads.json")).use {out->
                    out.write(receipt.toString().toByteArray(Charsets.UTF_8));out.fd.sync()
                }
            }
        }
        return try{action(stage::set)}finally{
            finished.countDown();instrumentation.runOnMainSync {activity.finish()}
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
            val root=ui.rootInActiveWindow
            if(root?.packageName?.toString()!=context.packageName)return null
            return visit(root)
        }
        fun scrollOwnedForward():Boolean {
            val root=ui.rootInActiveWindow?:return false
            if(root.packageName?.toString()!=context.packageName)return false
            fun scroll(node:AccessibilityNodeInfo):Boolean {
                val down=AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
                if(node.isScrollable&&node.actionList.any{it.id==down}&&node.performAction(down))return true
                if(node.isScrollable&&node.className?.toString()=="android.widget.ScrollView"&&node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true
                repeat(node.childCount){node.getChild(it)?.let{child->if(scroll(child))return true}}
                return false
            }
            return scroll(root)
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
            await(text) { val visible=find(text)!=null; if(!visible)scrollOwnedForward(); visible }
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
            instrumentation.runOnMainSync { activity!!.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            instrumentation.waitForIdleSync()
            click("Серверы")
            click("Удалить все конфиги VPN")
            await("confirmation") { find("Удалить все конфиги VPN?") != null }
            click("Отмена")
            val afterCancel=vault.loadSnapshot()
            if(afterCancel!=beforeCancel) {
                val diagnostic=org.json.JSONObject().put("scope","Synthetic isolated test snapshots only")
                    .put("pid",android.os.Process.myPid())
                    .put("before",org.json.JSONObject(ProfileDocument.encodeSnapshot(beforeCancel)))
                    .put("after",org.json.JSONObject(ProfileDocument.encodeSnapshot(afterCancel)))
                    .put("beforeAvailability",org.json.JSONArray(beforeCancel.profiles.map{it.id+":"+it.retiredFromSubscription}))
                    .put("afterAvailability",org.json.JSONArray(afterCancel.profiles.map{it.id+":"+it.retiredFromSubscription}))
                java.io.FileOutputStream(File(context.getExternalFilesDir(null),"delete-all-cancel-diff.json")).use {out->out.write(diagnostic.toString().toByteArray(Charsets.UTF_8));out.fd.sync()}
            }
            check(afterCancel == beforeCancel) { "Cancel changed the vault" }
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
            instrumentation.runOnMainSync { activity!!.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            click("Серверы")
            await("empty list after activity recreation") {
                val persistedEmpty=vault.loadSnapshot()==ProfileSnapshot() && SelectedProfileStore(context).load()==null
                val visible=listOf("Найдено: 0 из 0","Пока нет серверов","Пока нет профилей","Нет сохранённых серверов","Пока нет подключений").any {find(it)!=null}
                if(persistedEmpty&&!visible)scrollOwnedForward()
                persistedEmpty&&visible
            }
            repeat(8) { if(scrollOwnedForward()) SystemClock.sleep(150) }
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




