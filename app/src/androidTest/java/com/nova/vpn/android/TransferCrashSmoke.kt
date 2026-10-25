package com.nova.vpn.android

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.nova.vpn.android.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Host restarts the isolated package after each deliberate process death. */
internal object TransferCrashSmoke {
    private const val PASSWORD = "Synthetic-restore-crash-2026"
    private const val ORIGINAL = "restore-crash-original.nova"
    private const val INCOMING = "restore-crash-incoming.nova"

    fun run(instrumentation: Instrumentation, phase: String) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.nova.vpn.android.usbtest.realitypreview")
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val journal = File(context.filesDir, "restore-pending.vault")
        val original = File(context.filesDir, ORIGINAL)
        val incoming = File(context.filesDir, INCOMING)
        val validJournal = File(context.filesDir, "restore-crash-valid-journal.bin")
        when {
            phase == "repair-test-baseline" -> {
                if (journal.exists() && validJournal.exists()) java.io.FileOutputStream(journal).use {
                    it.write(validJournal.readBytes()); it.fd.sync()
                }
                FullTransfer.recoverPending(context)
                if (original.exists()) check(comparable(FullTransfer.read(FullTransfer.export(context, PASSWORD), PASSWORD)) ==
                    comparable(FullTransfer.read(original.readText(), PASSWORD)))
                validJournal.delete()
            }
            phase == "prepare" -> {
                check(ProfileVault(context).load().isNotEmpty()) { "Run synthetic subscription setup first" }
                val exported = FullTransfer.export(context, PASSWORD)
                original.writeText(exported)
                repeat(8) { File(context.getExternalFilesDir(null), "restore-crash-$it.json").delete() }
                val changed = FullTransfer.read(exported, PASSWORD)
                changed.put("profiles", JSONArray()).put("selectedId", "")
                    .put("dark", !changed.getBoolean("dark")).put("ipv6", !changed.getBoolean("ipv6"))
                    .put("directDomains", JSONArray().put("crash-incoming.invalid")).put("proxyDomains", JSONArray())
                val platforms = changed.getJSONObject("platforms")
                val native = JSONObject(platforms.getString("android"))
                native.put("profileDocument", ProfileDocument.encodeSnapshot(ProfileSnapshot()))
                native.getJSONObject("app_preferences").put("server_grouping",
                    JSONObject().put("type", "string").put("value", if (
                        native.getJSONObject("app_preferences").optJSONObject("server_grouping")?.optString("value") == "TRANSPORT"
                    ) "COUNTRY" else "TRANSPORT"))
                platforms.put("android", native.toString())
                val encoded = IdentityTransfer.seal(changed.toString(), PASSWORD)
                FullTransfer.read(encoded, PASSWORD)
                incoming.writeText(encoded)
            }
            phase == "corrupt-startup-prepare" -> {
                val document = FullTransfer.read(incoming.readText(), PASSWORD)
                check(runCatching { FullTransfer.restore(intercept(context, before = {
                    if (it == 2) throw SyntheticInterruption()
                }), document) }.exceptionOrNull() is SyntheticInterruption)
                val bytes = journal.readBytes()
                java.io.FileOutputStream(validJournal).use { it.write(bytes); it.fd.sync() }
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
                java.io.FileOutputStream(journal).use { it.write(bytes); it.fd.sync() }
            }
            phase == "corrupt-startup-ui" -> {
                check(RestoreRecoveryState.problem.value == RestoreProblem.AUTHENTICATION) { "Provider failed to expose a safe recovery error" }
                var activity: android.app.Activity? = null
                var recoveryWork: java.util.UUID? = null
                val ui = instrumentation.uiAutomation
                fun find(node: android.view.accessibility.AccessibilityNodeInfo?, text: String): android.view.accessibility.AccessibilityNodeInfo? {
                    if (node == null) return null
                    if (node.text?.toString() == text) return node
                    repeat(node.childCount) { find(node.getChild(it), text)?.let { result -> return result } }
                    return null
                }
                fun waitFor(text: String): android.view.accessibility.AccessibilityNodeInfo {
                    repeat(100) {
                        find(ui.rootInActiveWindow, text)?.let { return it }
                        android.os.SystemClock.sleep(100)
                    }
                    error("Missing safe recovery control: $text")
                }
                fun click(text: String) {
                    var node: android.view.accessibility.AccessibilityNodeInfo? = waitFor(text)
                    repeat(8) {
                        if (node?.isClickable == true) { check(node!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)); return }
                        node = node?.parent
                    }
                    error("Unclickable recovery control")
                }
                try {
                    activity = instrumentation.startActivitySync(android.content.Intent(context, MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    waitFor("Нужно восстановить данные")
                    waitFor("RESTORE_AUTH_FAILED")
                    val damaged = journal.readBytes()
                    click("Повторить восстановление")
                    waitFor("Повторная попытка не помогла. Данные сохранены.")
                    check(journal.readBytes().contentEquals(damaged))
                    val sequence = NovaVpnService::class.java.getDeclaredField("SERVICE_SEQUENCE").apply { isAccessible = true }
                        .get(null) as java.util.concurrent.atomic.AtomicLong
                    val previousService = sequence.get()
                    val refusedToken = VpnUserRequests.gate.begin()
                    context.startForegroundService(android.content.Intent(context, NovaVpnService::class.java)
                        .setAction(NovaVpnService.ACTION_START).putExtra(VpnUserRequests.EXTRA_REQUEST, refusedToken)
                        .putExtra(NovaVpnService.EXTRA_CONFIG, "{must-not-parse-recovery-test"))
                    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    repeat(100) {
                        @Suppress("DEPRECATION")
                        val running = manager.getRunningServices(100).any { it.service.className == NovaVpnService::class.java.name }
                        if (sequence.get() == previousService || running) android.os.SystemClock.sleep(50)
                    }
                    check(sequence.get() > previousService && VpnApplyRequests.tracker.results.value[refusedToken] == null)
                    @Suppress("DEPRECATION")
                    check(manager.getRunningServices(100).none { it.service.className == NovaVpnService::class.java.name })
                    check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting && VpnRuntime.state.value.error == null)
                    // Keep the process alive beyond this Android guest's 10s/30s
                    // foreground deadlines; an early instrumentation finish can hide the crash.
                    android.os.SystemClock.sleep(35_000)
                    waitFor("Нужно восстановить данные")
                    val work = androidx.work.OneTimeWorkRequestBuilder<SubscriptionWorker>()
                        .setInputData(androidx.work.workDataOf("subscriptionId" to "recovery-test-no-source"))
                        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()
                    recoveryWork = work.id
                    val workManager = androidx.work.WorkManager.getInstance(context)
                    workManager.enqueue(work).result.get(5, java.util.concurrent.TimeUnit.SECONDS)
                    var deferred = false
                    repeat(100) {
                        val info = workManager.getWorkInfoById(work.id).get(5, java.util.concurrent.TimeUnit.SECONDS)
                        if (info?.state == androidx.work.WorkInfo.State.ENQUEUED && info.runAttemptCount > 0) deferred = true
                        if (!deferred) android.os.SystemClock.sleep(100)
                    }
                    check(deferred) { "Subscription worker did not defer corrupt-store access" }
                    workManager.cancelWorkById(work.id).result.get(5, java.util.concurrent.TimeUnit.SECONDS)
                    recoveryWork = null
                    check(journal.readBytes().contentEquals(damaged))
                    fun texts(node: android.view.accessibility.AccessibilityNodeInfo?): String {
                        if (node == null) return ""
                        return node.text?.toString().orEmpty() + (0 until node.childCount).joinToString { texts(node.getChild(it)) }
                    }
                    val visible = texts(ui.rootInActiveWindow)
                    listOf("fixture.example", "TEST_TOKEN", "11111111-1111").forEach { check(!visible.contains(it)) }
                    click("Скопировать код ошибки")
                    var copied: String? = null
                    instrumentation.runOnMainSync {
                        copied = (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .primaryClip?.getItemAt(0)?.text?.toString()
                    }
                    check(copied == "RESTORE_AUTH_FAILED")
                    ui.takeScreenshot()?.let { bitmap ->
                        File(context.getExternalFilesDir(null), "restore-recovery-error.png").outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                    java.io.FileOutputStream(journal).use { it.write(validJournal.readBytes()); it.fd.sync() }
                    click("Повторить восстановление")
                    repeat(100) {
                        if (RestoreRecoveryState.problem.value != null || find(ui.rootInActiveWindow, "Нужно восстановить данные") != null)
                            android.os.SystemClock.sleep(100)
                    }
                    check(RestoreRecoveryState.problem.value == null && find(ui.rootInActiveWindow, "Нужно восстановить данные") == null)
                    check(!journal.exists() && !VpnRuntime.state.value.connecting && !VpnRuntime.state.value.connected)
                    check(comparable(FullTransfer.read(FullTransfer.export(context, PASSWORD), PASSWORD)) ==
                        comparable(FullTransfer.read(original.readText(), PASSWORD)))
                } finally {
                    recoveryWork?.let { androidx.work.WorkManager.getInstance(context).cancelWorkById(it).result.get(5, java.util.concurrent.TimeUnit.SECONDS) }
                    if (journal.exists() && validJournal.exists()) java.io.FileOutputStream(journal).use {
                        it.write(validJournal.readBytes()); it.fd.sync()
                    }
                    instrumentation.runOnMainSync { activity?.finish() }
                    validJournal.delete()
                }
            }
            phase.startsWith("fault-") -> {
                check(!journal.exists())
                val document = FullTransfer.read(incoming.readText(), PASSWORD)
                fun leavePending() {
                    val interrupted = runCatching {
                        FullTransfer.restore(intercept(context, before = { if (it == 2) throw SyntheticInterruption() }), document)
                    }.exceptionOrNull()
                    check(interrupted is SyntheticInterruption && journal.exists())
                }
                when (phase) {
                    "fault-apply" -> {
                        val error = runCatching { FullTransfer.restore(intercept(context, before = {
                            if (it == 2) throw java.io.IOException("synthetic commit failure")
                        }), document) }.exceptionOrNull()
                        check(error is java.io.IOException && !journal.exists())
                    }
                    "fault-rollback" -> {
                        val error = runCatching { FullTransfer.restore(intercept(context, before = {
                            if (it >= 2) throw java.io.IOException("synthetic apply and rollback failure")
                        }), document) }.exceptionOrNull()
                        check(error is IllegalStateException && journal.exists())
                        ProfileVault(context).load() // MUST heal in this same process before returning.
                        check(!journal.exists())
                    }
                    "fault-recovery" -> {
                        leavePending()
                        val bytes = journal.readBytes()
                        check(runCatching { FullTransfer.recoverPending(intercept(context, before = {
                            throw java.io.IOException("synthetic recovery write failure")
                        })) }.isFailure)
                        check(journal.readBytes().contentEquals(bytes))
                        IdentityVault(context).load()
                        check(!journal.exists())
                    }
                    "fault-corrupt-journal" -> {
                        leavePending()
                        val valid = journal.readBytes()
                        val damaged = valid.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
                        java.io.FileOutputStream(journal).use { it.write(damaged); it.fd.sync() }
                        try {
                        val attempts = listOf<() -> Unit>(
                            { ProfileVault(context).load(); Unit }, { IdentityVault(context).load(); Unit },
                            { AppRoutingStore(context).load(); Unit }, { SelectedProfileStore(context).load(); Unit },
                            { AppPreferencesStore(context).autoConnectEnabled(); Unit },
                            { AutoServerSettingsStore(context).use { it.snapshot() }; Unit },
                            { ProfileVault(context).save(emptyList()) }, { IdentityVault(context).save(IdentityState()) },
                            { AppRoutingStore(context).save(AppRoutingSettings()) }, { SelectedProfileStore(context).clear() },
                            { AppPreferencesStore(context).setAutoConnectEnabled(false) },
                            { AutoServerSettingsStore(context).use { it.save(false) } }
                        )
                        attempts.forEach { check(runCatching(it).isFailure) { "Store bypassed corrupt recovery journal" } }
                        check(journal.readBytes().contentEquals(damaged))
                        } finally { java.io.FileOutputStream(journal).use { it.write(valid); it.fd.sync() } }
                        SelectedProfileStore(context).load()
                        check(!journal.exists())
                    }
                    "fault-journal-delete" -> {
                        val directory = context.filesDir.absolutePath
                        val mode = android.system.Os.stat(directory).st_mode and 511
                        try {
                            val error = runCatching { FullTransfer.restore(intercept(context, after = {
                                if (it == 7) android.system.Os.chmod(directory, 365) //0555: fail actual directory unlink.
                            }), document) }.exceptionOrNull()
                            check(error != null && journal.exists())
                            val expectedGrouping = JSONObject(document.getJSONObject("platforms").getString("android"))
                                .getJSONObject("app_preferences").getJSONObject("server_grouping").getString("value")
                            check(context.getSharedPreferences("app_preferences", 0).getString("server_grouping", null) == expectedGrouping) {
                                "Journal deletion failure incorrectly began another partial rollback"
                            }
                        } finally { android.system.Os.chmod(directory, mode) }
                        AppPreferencesStore(context).serverGrouping()
                        check(!journal.exists())
                    }
                    else -> error("Unknown restore fault phase")
                }
                check(comparable(FullTransfer.read(FullTransfer.export(context, PASSWORD), PASSWORD)) ==
                    comparable(FullTransfer.read(original.readText(), PASSWORD))) { "Fault recovery did not restore complete original state" }
            }
            phase.startsWith("cut-") -> {
                val cut = phase.removePrefix("cut-").toInt().also { require(it in 0..7) }
                check(!journal.exists()) { "Startup left a pending restore journal" }
                val document = FullTransfer.read(incoming.readText(), PASSWORD)
                var commits = 0
                fun crash() {
                    check(journal.exists()) { "Mutation began before the journal was durable" }
                    val encrypted = journal.readBytes().toString(Charsets.ISO_8859_1)
                    for (secret in listOf("fixture.example", "TEST_TOKEN", "outboundJson", "11111111-1111")) {
                        check(!encrypted.contains(secret)) { "Journal contains unencrypted profile data" }
                    }
                    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(journal.readBytes())
                        .joinToString("") { "%02x".format(it) }
                    val receipt = JSONObject().put("cut", cut).put("journal_checked", true)
                        .put("journal_sha256", hash).put("cleartext_markers_absent", true)
                        .put("damaged_profile_target", cut == 4)
                    if (cut == 4) java.io.FileOutputStream(File(context.filesDir, "profiles.vault")).use {
                        it.write(byteArrayOf(0, 1, 2)); it.fd.sync()
                    }
                    java.io.FileOutputStream(File(context.getExternalFilesDir(null), "restore-crash-$cut.json")).use {
                        it.write(receipt.toString().toByteArray(Charsets.UTF_8)); it.fd.sync()
                    }
                    android.os.Process.killProcess(android.os.Process.myPid())
                    Thread.sleep(Long.MAX_VALUE)
                }
                val interrupted = object : ContextWrapper(context) {
                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                        val prefs = super.getSharedPreferences(name, mode)
                        if (name !in setOf("app_preferences", "routing", "profile_selection")) return prefs
                        return object : SharedPreferences by prefs {
                            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                                private val editor = prefs.edit()
                                override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
                                override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { editor.putStringSet(key, values) }
                                override fun putInt(key: String?, value: Int) = apply { editor.putInt(key, value) }
                                override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
                                override fun putFloat(key: String?, value: Float) = apply { editor.putFloat(key, value) }
                                override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
                                override fun remove(key: String?) = apply { editor.remove(key) }
                                override fun clear() = apply { editor.clear() }
                                override fun commit(): Boolean {
                                    if (cut == 0 && commits == 0) crash()
                                    val saved = editor.commit()
                                    check(saved)
                                    commits++
                                    if (commits == cut) crash()
                                    return saved
                                }
                                override fun apply() { commit() }
                            }
                        }
                    }
                }
                FullTransfer.restore(interrupted, document)
                error("Crash cut $cut was not reached (commits=$commits)")
            }
            phase == "verify" -> {
                check(!journal.exists() && !File(journal.path + ".bak").exists()) { "Startup did not finish journal rollback" }
                val expected = comparable(FullTransfer.read(original.readText(), PASSWORD))
                val actual = comparable(FullTransfer.read(FullTransfer.export(context, PASSWORD), PASSWORD))
                check(actual == expected) { "Crash recovery left inconsistent profiles, identity, routing, selection or preferences" }
                check(!FullTransfer.recoverPending(context)) { "Recovery was not idempotent" }
            }
            phase == "cleanup" -> {
                check(original.delete()); check(incoming.delete())
                repeat(8) { File(context.getExternalFilesDir(null), "restore-crash-$it.json").delete() }
            }
            else -> error("Unknown crash-test phase")
        }
    }

    private class SyntheticInterruption : Error()

    private fun intercept(context: Context, before: (Int) -> Unit = {}, after: (Int) -> Unit = {}): Context {
        var count = 0
        return object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val prefs = super.getSharedPreferences(name, mode)
                if (name !in setOf("app_preferences", "routing", "profile_selection")) return prefs
                return object : SharedPreferences by prefs {
                    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                        private val editor = prefs.edit()
                        override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
                        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { editor.putStringSet(key, values) }
                        override fun putInt(key: String?, value: Int) = apply { editor.putInt(key, value) }
                        override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
                        override fun putFloat(key: String?, value: Float) = apply { editor.putFloat(key, value) }
                        override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
                        override fun remove(key: String?) = apply { editor.remove(key) }
                        override fun clear() = apply { editor.clear() }
                        override fun commit(): Boolean {
                            val next = ++count
                            before(next)
                            return editor.commit().also { check(it); after(next) }
                        }
                        override fun apply() { commit() }
                    }
                }
            }
        }
    }

    private fun comparable(document: JSONObject): String {
        val platforms = document.getJSONObject("platforms")
        platforms.put("android", JSONObject(platforms.getString("android")))
        return canonical(document)
    }
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") {
            JSONObject.quote(it) + ":" + canonical(value.get(it))
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
