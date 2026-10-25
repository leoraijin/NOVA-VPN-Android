package com.nova.vpn.android

import android.content.Context
import androidx.work.*
import com.nova.vpn.android.data.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real WorkManager database/scheduler; no provider traffic (all work initially delayed). */
internal object SubscriptionScheduleSmoke {
    private const val PASSWORD = "Synthetic-schedule-backup-2026"
    private const val RECONCILE = "nova-subscription-schedule-reconcile"

    fun run(context: Context, phase: String) {
        check(context.packageName == "com.nova.vpn.android.usbtest.realitypreview")
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val manager = WorkManager.getInstance(context)
        val vault = ProfileVault(context)
        val backup = File(context.filesDir, "schedule-test-backup.nova")
        val meta = File(context.filesDir, "schedule-test-meta.json")
        fun infos(id: String) = manager.getWorkInfosForUniqueWork("nova-subscription-$id").get(10, TimeUnit.SECONDS)
        fun active(id: String) = infos(id).filterNot { it.state.isFinished }
        fun await(message: String, condition: () -> Boolean) {
            val until = android.os.SystemClock.elapsedRealtime() + 30_000
            while (!condition() && android.os.SystemClock.elapsedRealtime() < until) Thread.sleep(100)
            check(condition()) { message }
        }
        fun unrelated(): UUID {
            val work = OneTimeWorkRequestBuilder<SubscriptionWorker>().setInitialDelay(1, TimeUnit.DAYS)
                .setInputData(workDataOf("subscriptionId" to "unrelated-schedule-test")).build()
            manager.enqueue(work).result.get(10, TimeUnit.SECONDS)
            return work.id
        }
        fun makeSource(prefix: String) = Subscription(id = "$prefix-${UUID.randomUUID()}", name = "Schedule fixture",
            url = "https://schedule.test/sub/SYNTHETIC-${UUID.randomUUID()}", intervalHours = 6)
        fun cleanup(unrelatedId: UUID?) {
            if (backup.exists()) {
                FullTransfer.restore(context, FullTransfer.read(backup.readText(), PASSWORD))
                SubscriptionScheduling.reconcile(context)
                check(backup.delete())
            }
            unrelatedId?.let { manager.cancelWorkById(it).result.get(10, TimeUnit.SECONDS) }
            meta.delete()
        }
        when (phase) {
            "normal" -> {
                backup.writeText(FullTransfer.export(context, PASSWORD))
                val keep = makeSource("keep")
                val removed = makeSource("removed")
                var other: UUID? = null
                try {
                    val repo = SubscriptionRepository(context)
                    repo.save(keep); repo.save(removed)
                    SubscriptionScheduling.apply(context, listOf(keep, removed)).forEach { it.result.get(10, TimeUnit.SECONDS) }
                    val keptId = active(keep.id).single().id
                    check(active(removed.id).size == 1)
                    other = unrelated()
                    val target = FullTransfer.read(FullTransfer.export(context, PASSWORD), PASSWORD)
                    val platforms = target.getJSONObject("platforms")
                    val native = JSONObject(platforms.getString("android"))
                    val desired = vault.loadSnapshot().copy(subscriptions = vault.loadSnapshot().subscriptions
                        .filterNot { it.id == removed.id }.map { if (it.id == keep.id) it.copy(intervalHours = 12) else it })
                    native.put("profileDocument", ProfileDocument.encodeSnapshot(desired))
                    platforms.put("android", native.toString())
                    check(active(removed.id).size == 1) { "Removed job disappeared before import" }
                    FullTransfer.restore(context, target)
                    await("Imported schedules were not reconciled by background work") {
                        active(removed.id).isEmpty() && active(keep.id).singleOrNull()?.periodicityInfo?.repeatIntervalMillis == TimeUnit.HOURS.toMillis(12)
                    }
                    check(active(keep.id).single().id == keptId) { "UPDATE replaced the retained schedule identity" }
                    check(manager.getWorkInfoById(other).get(10, TimeUnit.SECONDS)?.state?.isFinished == false)
                } finally { cleanup(other) }
            }
            "stale-snapshot", "stale-prune" -> {
                backup.writeText(FullTransfer.export(context, PASSWORD))
                val retained = makeSource("race-keep")
                val revived = makeSource("race-revived")
                var other: UUID? = null
                val reached = java.util.concurrent.CountDownLatch(1)
                val resume = java.util.concurrent.CountDownLatch(1)
                val result = java.util.concurrent.atomic.AtomicReference<Boolean?>()
                val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
                var thread: Thread? = null
                try {
                    manager.cancelUniqueWork(RECONCILE).result.get(10, TimeUnit.SECONDS)
                    SubscriptionScheduling.apply(context, emptyList()) // Drain the cancelled reconciler.
                    vault.updateSnapshot { it.copy(subscriptions = listOf(retained)) }
                    SubscriptionScheduling.apply(context, listOf(retained, revived)).forEach { it.result.get(10, TimeUnit.SECONDS) }
                    val retainedId = active(retained.id).single().id
                    val revivedId = active(revived.id).single().id
                    other = unrelated()
                    val boundary = if (phase == "stale-snapshot") SubscriptionScheduling.Stage.AFTER_SNAPSHOT else SubscriptionScheduling.Stage.BEFORE_PRUNE
                    thread = kotlin.concurrent.thread(isDaemon = true, name = "NOVA-schedule-race-fixture") {
                        try { result.set(SubscriptionScheduling.reconcile(context) { stage ->
                            if (stage == boundary) { reached.countDown(); check(resume.await(10, TimeUnit.SECONDS)) }
                        }) } catch (error: Throwable) { failure.set(error) }
                    }
                    check(reached.await(15, TimeUnit.SECONDS)) { "Reconciliation boundary not reached" }
                    // A real encrypted commit from a concurrent thread after the old snapshot.
                    // The old pruning candidate is now legitimate and must keep its UUID.
                    vault.updateSnapshot { it.copy(subscriptions = listOf(retained.copy(intervalHours = 12), revived.copy(intervalHours = 12))) }
                    resume.countDown(); thread.join(20_000)
                    check(!thread.isAlive) { "Reconciler deadlocked after concurrent commit" }
                    failure.get()?.let { throw it }
                    check(result.get() == false) { "Stale snapshot was accepted" }
                    check(active(revived.id).single().id == revivedId) { "Stale pruning cancelled a now-current subscription" }
                    check(SubscriptionScheduling.reconcile(context))
                    check(active(retained.id).single().id == retainedId && active(revived.id).single().id == revivedId)
                    check(active(retained.id).single().periodicityInfo?.repeatIntervalMillis == TimeUnit.HOURS.toMillis(12))
                    check(active(revived.id).single().periodicityInfo?.repeatIntervalMillis == TimeUnit.HOURS.toMillis(12))
                    check(manager.getWorkInfoById(other).get(10, TimeUnit.SECONDS)?.state?.isFinished == false)
                } finally { resume.countDown(); thread?.join(20_000); cleanup(other) }
            }
            "terminal-retry" -> {
                backup.writeText(FullTransfer.export(context, PASSWORD))
                try {
                    manager.cancelUniqueWork(RECONCILE).result.get(10, TimeUnit.SECONDS)
                    SubscriptionScheduling.apply(context, emptyList())
                    val source = makeSource("retry")
                    vault.updateSnapshot { it.copy(subscriptions = listOf(source)) }
                    val failureClass = Class.forName("com.nova.vpn.android.SubscriptionScheduleFailureWorker").asSubclass(ListenableWorker::class.java)
                    val failureWork = OneTimeWorkRequest.Builder(failureClass)
                        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS).build()
                    SubscriptionScheduling.enqueue(context, listOf(source), true, failureWork)
                    val deadline = android.os.SystemClock.elapsedRealtime() + 180_000
                    var info = manager.getWorkInfoById(failureWork.id).get(10, TimeUnit.SECONDS)
                    while (info?.state?.isFinished != true && android.os.SystemClock.elapsedRealtime() < deadline) {
                        Thread.sleep(250); info = manager.getWorkInfoById(failureWork.id).get(10, TimeUnit.SECONDS)
                    }
                    check(info?.state == WorkInfo.State.FAILED && info.runAttemptCount >= 4) { "Worker did not exhaust real WorkManager retries: $info" }
                    val oldIds = manager.getWorkInfosForUniqueWork(RECONCILE).get(10, TimeUnit.SECONDS).map { it.id }.toSet()
                    SubscriptionScheduling.request(context, listOf(source)) // Same scope must no longer be suppressed.
                    await("Terminal failure suppressed a fresh request") {
                        manager.getWorkInfosForUniqueWork(RECONCILE).get(10, TimeUnit.SECONDS).any { it.id !in oldIds && it.state == WorkInfo.State.SUCCEEDED }
                    }
                    val repairedIds = manager.getWorkInfosForUniqueWork(RECONCILE).get(10, TimeUnit.SECONDS).map { it.id }.toSet()
                    SubscriptionScheduling.failed(failureWork.id) // A late older completion must preserve newer cache ownership.
                    SubscriptionScheduling.request(context, listOf(source))
                    Thread.sleep(500)
                    check(manager.getWorkInfosForUniqueWork(RECONCILE).get(10, TimeUnit.SECONDS).map { it.id }.toSet() == repairedIds) { "Old failure invalidated newer ownership" }
                } finally { cleanup(null) }
            }
            "prepare-boot" -> {
                backup.writeText(FullTransfer.export(context, PASSWORD))
                val keep = makeSource("boot-keep")
                val ghost = makeSource("boot-ghost")
                SubscriptionRepository(context).save(keep)
                manager.cancelUniqueWork(RECONCILE).result.get(10, TimeUnit.SECONDS)
                SubscriptionScheduling.apply(context, emptyList()) // Drain any cancelled blocking reconciler.
                SubscriptionScheduling.apply(context, listOf(keep, ghost)).forEach { it.result.get(10, TimeUnit.SECONDS) }
                val other = unrelated()
                check(active(ghost.id).size == 1 && vault.loadSnapshot().subscriptions.none { it.id == ghost.id })
                meta.writeText(JSONObject().put("keep", keep.id).put("keepWork", active(keep.id).single().id.toString())
                    .put("ghost", ghost.id).put("other", other.toString()).toString())
            }
            "verify-boot" -> {
                val data = JSONObject(meta.readText())
                val keep = data.getString("keep")
                val ghost = data.getString("ghost")
                val other = UUID.fromString(data.getString("other"))
                try {
                    // No Activity and no manual reconciliation call: startup provider must enqueue it.
                    await("Cold startup left obsolete NOVA work") { active(ghost).isEmpty() && active(keep).size == 1 }
                    check(active(keep).single().id.toString() == data.getString("keepWork"))
                    check(manager.getWorkInfoById(other).get(10, TimeUnit.SECONDS)?.state?.isFinished == false)
                } finally { cleanup(other) }
            }
            else -> error("Unknown scheduler smoke phase")
        }
    }
}
