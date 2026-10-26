package com.nova.vpn.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** One serialized refresh pipeline shared by UI and background work in the app process. */
internal class SubscriptionRepository(context: Context,
    private val request: ((Subscription, String?, SubscriptionIdentity?) -> SubscriptionFetcher.Response)? = null) {
    private val context = context.applicationContext
    private val vault = ProfileVault(this.context)

    fun importReport(url: String, sendDeviceId: Boolean, report: ProfileLinkParser.ImportResult): ProfileSnapshot = vault.updateSnapshot { state ->
        val source = (state.subscriptions.firstOrNull { it.url == url }
            ?: Subscription(name = java.net.URI(url).host ?: "Подписка", url = url)).copy(sendDeviceId = sendDeviceId)
        source.validate()
        val changes = SubscriptionReconciler.reconcile(state.catalog(), source.id,
            report.profiles.map { it.copy(subscriptionUrl = url) }, report.warnings.isEmpty(),
            setOfNotNull(SelectedProfileStore(context).load()))
        val now = System.currentTimeMillis()
        state.copy(profiles = changes.catalog.profiles, memberships = changes.catalog.memberships,
            manuallyOwned = changes.catalog.manuallyOwned, subscriptions = state.subscriptions.filterNot { it.id == source.id } +
                source.copy(status = if (changes.partial) "partial" else "ok", lastAttempt = now, lastSuccess = now,
                    etag = null, lastModified = null, retryAfterMillis = 0,
                    added = changes.added, updated = changes.updated, removed = changes.removed))
    }

    fun save(source: Subscription): ProfileSnapshot {
        source.validate()
        return vault.updateSnapshot { state ->
            require(state.subscriptions.none { it.id != source.id && it.url == source.url }) { "Эта подписка уже добавлена" }
            val old = state.subscriptions.firstOrNull { it.id == source.id }
            val next = if (old != null && (old.url != source.url || old.sendDeviceId != source.sendDeviceId))
                source.copy(etag = null, lastModified = null, status = "never", usedBytes = null, totalBytes = null,
                    expiresAtSeconds = null, retryAfterMillis = 0) else source
            state.copy(subscriptions = state.subscriptions.filterNot { it.id == source.id } + next)
        }
    }

    fun remove(id: String, keepServers: Boolean): ProfileSnapshot = vault.updateSnapshot { state ->
        val source = state.subscriptions.firstOrNull { it.id == id } ?: return@updateSnapshot state
        val owned = state.memberships.filter { it.subscriptionId == id }.map { it.profileId }.toSet()
        val links = state.memberships.filterNot { it.subscriptionId == id }.toSet()
        val retained = links.map { it.profileId }.toSet() + state.manuallyOwned +
            if (keepServers) owned else setOfNotNull(SelectedProfileStore(context).load())
        val profiles = state.profiles.filter { it.id !in owned || it.id in retained }.map { profile ->
            if (profile.subscriptionUrl == source.url) profile.copy(subscriptionUrl = null) else profile
        }
        state.copy(profiles = profiles, subscriptions = state.subscriptions.filterNot { it.id == id }, memberships = links,
            manuallyOwned = state.manuallyOwned + if (keepServers) owned else emptySet())
    }

    suspend fun refresh(id: String, force: Boolean = true): ProfileSnapshot = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val (initial, generation) = synchronized(ProfileVault.lock) {
                vault.loadSnapshot().subscriptions.firstOrNull { it.id == id } to ProfileVault.replacementGeneration
            }
            if (initial == null) return@withContext vault.loadSnapshot()
            val now = System.currentTimeMillis()
            if (!force && initial.retryAfterMillis > now) return@withContext vault.loadSnapshot()
            updateSource(initial, generation) { it.copy(lastAttempt = now) }
            try {
                val identity = IdentityVault(context).load().forUrl(initial.url)
                val deviceId = if (initial.sendDeviceId && identity == null) SubscriptionDeviceIdentity(context).forUrl(initial.url) else null
                val response = request?.invoke(initial, deviceId, identity)
                    ?: SubscriptionFetcher.fetchSubscription(initial.url, deviceId, initial.etag, initial.lastModified, identity)
                coroutineContext.ensureActive()
                val usage = SubscriptionUsage.parse(response.userInfo)
                if (response.status == 304) {
                    require(initial.lastSuccess > 0) { "Сервер вернул 304 без сохранённой подписки" }
                    return@withContext updateSource(initial, generation) { it.copy(lastAttempt = now, lastSuccess = now,
                        status = "not_modified", retryAfterMillis = 0, etag = response.etag ?: it.etag,
                        lastModified = response.lastModified ?: it.lastModified,
                        usedBytes = if (response.userInfo == null) it.usedBytes else usage.used,
                        totalBytes = if (response.userInfo == null) it.totalBytes else usage.total,
                        expiresAtSeconds = if (response.userInfo == null) it.expiresAtSeconds else usage.expires,
                        added = 0, updated = 0, removed = 0) }
                }
                val report = ProfileLinkParser.parseReport(response.body)
                require(report.profiles.isNotEmpty())
                coroutineContext.ensureActive()
                updateIfCurrent(generation) { current ->
                    val source = current.subscriptions.firstOrNull { it.id == id && it.url == initial.url && it.sendDeviceId == initial.sendDeviceId }
                        ?: return@updateIfCurrent current
                    val incoming = report.profiles.map { it.copy(subscriptionUrl = source.url) }
                    val changes = SubscriptionReconciler.reconcile(current.catalog(), id, incoming,
                        complete = report.warnings.isEmpty(), retainIds = setOfNotNull(SelectedProfileStore(context).load()))
                    current.copy(profiles = changes.catalog.profiles, memberships = changes.catalog.memberships,
                        manuallyOwned = changes.catalog.manuallyOwned, subscriptions = current.subscriptions.map {
                            if (it.id != id) it else it.copy(lastAttempt = now, lastSuccess = now,
                                status = if (changes.partial) "partial" else "ok", retryAfterMillis = 0,
                                // A partial parse must be retried, not converted into permanent 304s.
                                etag = response.etag.takeUnless { changes.partial }, lastModified = response.lastModified.takeUnless { changes.partial },
                                usedBytes = usage.used, totalBytes = usage.total, expiresAtSeconds = usage.expires,
                                added = changes.added, updated = changes.updated, removed = changes.removed)
                        })
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                val failure = error as? SubscriptionFetcher.FetchFailure
                updateSource(initial, generation) { it.copy(status = when (failure?.status) {
                    401, 403 -> "auth"; 429 -> "rate_limited"; else -> "error"
                }, retryAfterMillis = if (failure?.retryAfterMillis != null && failure.retryAfterMillis > 0)
                    now + failure.retryAfterMillis else 0) }
            }
        }
    }

    private fun updateIfCurrent(generation: Long, transform: (ProfileSnapshot) -> ProfileSnapshot): ProfileSnapshot = synchronized(ProfileVault.lock) {
        if (ProfileVault.replacementGeneration == generation) vault.updateSnapshot(transform) else vault.loadSnapshot()
    }

    private fun updateSource(expected: Subscription, generation: Long, transform: (Subscription) -> Subscription): ProfileSnapshot = updateIfCurrent(generation) { current ->
        current.copy(subscriptions = current.subscriptions.map {
            if (it.id == expected.id && it.url == expected.url && it.sendDeviceId == expected.sendDeviceId) transform(it) else it
        })
    }

    private companion object { val refreshMutex = Mutex() }
}
