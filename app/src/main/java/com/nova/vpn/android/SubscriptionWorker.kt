package com.nova.vpn.android

import android.content.Context
import androidx.work.*
import com.nova.vpn.android.data.ProfileVault
import com.nova.vpn.android.data.Subscription
import com.nova.vpn.android.data.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SubscriptionWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("subscriptionId") ?: return Result.failure()
        return try {
            val source = withContext(Dispatchers.IO) { ProfileVault(applicationContext).loadSnapshot().subscriptions.firstOrNull { it.id == id } }
                ?: return Result.success()
            if (source.intervalHours == 0) return Result.success()
            val result = SubscriptionRepository(applicationContext).refresh(id, force = false).subscriptions.firstOrNull { it.id == id }
                ?: return Result.success()
            if (result.status == "error" && runAttemptCount < 3) Result.retry() else Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}

internal object SubscriptionScheduling {
    private val schedulingLock = Any()
    private val requestLock = Any()
    private var lastRequested: String? = null
    private var lastRequestedId: java.util.UUID? = null
    private const val RECONCILE = "nova-subscription-schedule-reconcile"
    private const val TAG = "nova-subscriptions"

    private fun signature(sources: List<Subscription>): String {
        val encoded = org.json.JSONArray().apply { sources.sortedBy { it.id }.forEach {
            put(org.json.JSONArray().put(it.id).put(it.intervalHours))
        } }.toString().toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
    }

    fun request(context: Context, subscriptions: List<Subscription>? = null, force: Boolean = false) =
        enqueue(context, subscriptions, force, OneTimeWorkRequestBuilder<SubscriptionScheduleWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())

    internal fun enqueue(context: Context, subscriptions: List<Subscription>?, force: Boolean, request: OneTimeWorkRequest) = synchronized(requestLock) {
        val scope = subscriptions?.let(::signature)
        if (!force && scope != null && scope == lastRequested) return@synchronized
        val manager = WorkManager.getInstance(context)
        // Appending preserves a request arriving after a running worker's final
        // snapshot check but before WorkManager records that worker's success.
        val submitted = manager.enqueueUniqueWork(RECONCILE, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        lastRequested = scope
        lastRequestedId = request.id
        submitted.result.addListener({
            if (runCatching { submitted.result.get() }.isFailure) synchronized(requestLock) {
                if (lastRequestedId == request.id) {
                    lastRequested = null
                    lastRequestedId = null
                }
            }
        }, java.util.concurrent.Executor { it.run() })
    }

    fun failed(id: java.util.UUID) = synchronized(requestLock) {
        // A failed older worker must not invalidate a newer queued request.
        if (lastRequestedId == id) {
            lastRequested = null
            lastRequestedId = null
        }
    }

    fun apply(context: Context, subscriptions: List<Subscription>): List<Operation> = synchronized(schedulingLock) {
        val manager = WorkManager.getInstance(context)
        // A tag contains no URLs, tokens or hardware identifiers.
        subscriptions.map { source ->
            val name = "nova-subscription-${source.id}"
            if (source.intervalHours == 0) manager.cancelUniqueWork(name)
            else {
                val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(source.intervalHours.toLong(), TimeUnit.HOURS)
                    .setInputData(workDataOf("subscriptionId" to source.id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .setInitialDelay(source.intervalHours.toLong(), TimeUnit.HOURS)
                    .addTag(TAG).build()
                manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }
    }

    /** Blocking WorkManager calls run on IO, never while the profile monitor is held. */
    internal enum class Stage { AFTER_SNAPSHOT, BEFORE_PRUNE }

    fun reconcile(context: Context, observer: ((Stage) -> Unit)? = null): Boolean = synchronized(schedulingLock) {
        val (sources, generation) = synchronized(ProfileVault.lock) {
            ProfileVault(context).loadSnapshot().subscriptions to ProfileVault.replacementGeneration
        }
        observer?.invoke(Stage.AFTER_SNAPSHOT)
        val expected = signature(sources)
        val manager = WorkManager.getInstance(context)
        apply(context, sources).forEach { it.result.get(30, TimeUnit.SECONDS) }
        val keep = sources.filter { it.intervalHours > 0 }.map { "nova-subscription-${it.id}" }
            .chunked(100).flatMap { names ->
                manager.getWorkInfos(WorkQuery.Builder.fromUniqueWorkNames(names).build()).get(30, TimeUnit.SECONDS)
            }.filterNot { it.state.isFinished }.map { it.id }.toSet()
        fun current() = synchronized(ProfileVault.lock) {
            ProfileVault.replacementGeneration == generation && signature(ProfileVault(context).loadSnapshot().subscriptions) == expected
        }
        if (!current()) return@synchronized false
        val obsolete = manager.getWorkInfosByTag(TAG).get(30, TimeUnit.SECONDS)
            .filter { !it.state.isFinished && it.id !in keep }
        observer?.invoke(Stage.BEFORE_PRUNE)
        // The prior check may become stale while querying WorkManager. Linearize the
        // cancellation decisions with profile commits; never await database work under
        // ProfileVault.lock. A newer import must not lose its retained work identity.
        val cancellations = synchronized(ProfileVault.lock) {
            if (!current()) null else obsolete.map { manager.cancelWorkById(it.id) }
        } ?: return@synchronized false
        cancellations.forEach { it.result.get(30, TimeUnit.SECONDS) }
        current()
    }

    fun cancel(context: Context, id: String) { WorkManager.getInstance(context).cancelUniqueWork("nova-subscription-$id") }
}

open class SubscriptionScheduleWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    protected open suspend fun reconcileSchedules(): Boolean = withContext(Dispatchers.IO) { SubscriptionScheduling.reconcile(applicationContext) }

    override suspend fun doWork(): Result = try {
        val current = reconcileSchedules()
        if (!current) SubscriptionScheduling.request(applicationContext, force = true)
        Result.success()
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else {
            SubscriptionScheduling.failed(id)
            Result.failure()
        }
    }
}
