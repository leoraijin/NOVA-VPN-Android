package com.nova.vpn.android
import org.json.JSONObject

import android.content.Context
import com.nova.vpn.android.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import org.json.JSONArray
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlin.concurrent.thread

/** Synthetic data only; the stable application package is explicitly forbidden. */
internal object SubscriptionStoreSmoke {
    fun run(context: Context) = runBlocking {
        check(context.packageName == "com.nova.vpn.android.usbtest.realitypreview")
        val vault = ProfileVault(context)
        val uri = "vless://11111111-1111-4111-8111-111111111111@fixture.example:443?type=xhttp#Fixture"
        val node = ProfileLinkParser.parseText(uri).single().copy(id = "fixture-node", subscriptionUrl = "https://provider.test/sub/TEST_TOKEN")
        vault.updateSnapshot { ProfileSnapshot.migrate(listOf(node)) }
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey("nova.vpn.profile-store.v1", null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val legacy = byteArrayOf(78, 86, 80, 1) + cipher.iv + cipher.doFinal(JSONArray().put(node.toJson()).toString().toByteArray())
        val file = File(context.filesDir, "profiles.vault")
        file.writeBytes(legacy)
        check(vault.load().single() == node)
        check(file.readBytes().contentEquals(legacy)) { "Load rewrote legacy vault" }
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val workers = List(4) {
            thread {
                try { repeat(10) { ProfileVault(context).update { profiles -> profiles.map { p -> p.copy(userSettings = p.userSettings.copy(order = p.userSettings.order + 1)) } } } }
                catch (error: Throwable) { failures += error }
            }
        }
        workers.forEach { it.join() }
        check(failures.isEmpty())
        check(vault.load().single().userSettings.order == 40L) { "Lost concurrent update" }
        val source = vault.loadSnapshot().subscriptions.single()
        SelectedProfileStore(context).save(node.id)
        vault.update { profiles -> profiles.map { it.copy(userSettings = it.userSettings.copy(favorite = true, personalName = "My fixture")) } }
        var response = SubscriptionFetcher.Response(uri, etag = "fixture-etag", userInfo = "upload=10; download=20; total=100; expire=1900000000")
        val repository = SubscriptionRepository(context) { _, _, _ -> response }
        var state = repository.refresh(source.id)
        check(state.profiles.single().id == node.id && state.profiles.single().userSettings.favorite)
        check(state.subscriptions.single().usedBytes == 30L)
        check(state.subscriptions.single().etag == "fixture-etag")
        val before304 = state.profiles
        response = SubscriptionFetcher.Response("", status = 304)
        state = repository.refresh(source.id)
        check(state.profiles == before304 && state.subscriptions.single().status == "not_modified")
        check(state.subscriptions.single().usedBytes == 30L)
        for (code in listOf(401, 429)) {
            val failed = SubscriptionRepository(context) { _, _, _ -> throw SubscriptionFetcher.FetchFailure(code, if (code == 429) 60000 else 0) }
            state = failed.refresh(source.id)
            check(state.profiles == before304)
            check(state.subscriptions.single().status == if (code == 401) "auth" else "rate_limited")
        }
        response = SubscriptionFetcher.Response("")
        state = repository.refresh(source.id)
        check(state.profiles == before304 && state.subscriptions.single().status == "error")
        response = SubscriptionFetcher.Response("$uri\nunsupported://TEST_SECRET")
        state = repository.refresh(source.id)
        check(state.subscriptions.single().status == "partial")
        check(state.subscriptions.single().etag == null)
        val other = Subscription(id = "other-source", name = "Second", url = "https://other.test/sub/TEST_OTHER")
        repository.save(other)
        SubscriptionRepository(context) { _, _, _ -> SubscriptionFetcher.Response(uri) }.refresh(other.id)
        state = vault.loadSnapshot()
        check(state.profiles.size == 1 && state.memberships.size == 2)
        val password = "Synthetic-transfer-password-2026"
        val routingStore = AppRoutingStore(context)
        val committedRouting = routingStore.load()
        routingStore.stage(routingStore.loadPresets().activeId, "synthetic-transfer-pending", committedRouting.copy(ipv6Enabled = !committedRouting.ipv6Enabled))
        val packed = FullTransfer.export(context, password)
        check(!packed.contains("TEST_TOKEN"))
        val document = FullTransfer.read(packed, password)
        val androidSnapshot = JSONObject(document.getJSONObject("platforms").getString("android"))
        val transferredRoutes = RoutingPresets.fromJson(JSONObject(androidSnapshot.getJSONObject("routing").getJSONObject("routing_presets_v1").getString("value")))
        check(transferredRoutes.pending == null && transferredRoutes.active.settings == committedRouting)
        check(routingStore.loadPresets().pending?.requestId == "synthetic-transfer-pending")
        vault.updateSnapshot { ProfileSnapshot() }
        FullTransfer.restore(context, document)
        check(routingStore.loadPresets().pending == null && routingStore.load() == committedRouting)
        val restored = vault.loadSnapshot()
        check(restored.profiles == state.profiles && restored.subscriptions == state.subscriptions && restored.memberships == state.memberships)
        // A provider response may have started before restore and finish after it,
        // even when the restored subscription retains exactly the same ID/URL.
        for (late in listOf("200", "304", "401")) {
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val refresh = executor.submit { runBlocking {
                    SubscriptionRepository(context) { _, _, _ ->
                        entered.countDown()
                        check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
                        when (late) {
                            "200" -> SubscriptionFetcher.Response(uri.replace(":443?", ":8443?"))
                            "304" -> SubscriptionFetcher.Response("", status = 304)
                            else -> throw SubscriptionFetcher.FetchFailure(401, 0)
                        }
                    }.refresh(source.id)
                } }
                check(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
                FullTransfer.restore(context, document)
                val afterRestore = vault.loadSnapshot()
                val restoredBytes = file.readBytes()
                release.countDown()
                refresh.get(15, java.util.concurrent.TimeUnit.SECONDS)
                check(vault.loadSnapshot() == afterRestore) { "Late HTTP $late changed the restored document" }
                check(file.readBytes().contentEquals(restoredBytes)) { "Late HTTP $late rewrote the encrypted vault" }
            } finally { release.countDown(); executor.shutdownNow() }
        }
        // Separate store instances participate in the same restore boundary.
        val storeWriters = java.util.concurrent.Executors.newFixedThreadPool(6)
        val waiting = java.util.concurrent.CountDownLatch(6)
        val identityBefore = IdentityVault(context).load()
        val routesBefore = routingStore.load()
        val preferencesBefore = AppPreferencesStore(context)
        val sortBefore = preferencesBefore.serverSort()
        val favoritesBefore = preferencesBefore.favoritesOnly()
        val autoBefore = AutoServerSettingsStore(context).use { it.snapshot() }
        val writes = mutableListOf<java.util.concurrent.Future<*>>()
        try {
            synchronized(ProfileVault.lock) {
                listOf<() -> Unit>(
                    { IdentityVault(context).save(identityBefore) },
                    { AppRoutingStore(context).save(routesBefore) },
                    { SelectedProfileStore(context).save(node.id) },
                    { ProfileVault(context).updateSnapshot { it } },
                    { AppPreferencesStore(context).setServerListOptions(sortBefore, favoritesBefore) },
                    { AutoServerSettingsStore(context).use { it.save(autoBefore.enabled, autoBefore.candidateIds) } }
                ).forEach { writer -> writes += storeWriters.submit { waiting.countDown(); writer() } }
                check(waiting.await(5, java.util.concurrent.TimeUnit.SECONDS))
                check(writes.none { it.isDone }) { "A store writer bypassed the restore boundary" }
            }
            writes.forEach { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
            check(IdentityVault(context).load() == identityBefore && routingStore.load() == routesBefore)
            check(AppPreferencesStore(context).serverSort() == sortBefore && AppPreferencesStore(context).favoritesOnly() == favoritesBefore)
            AutoServerSettingsStore(context).use { check(it.snapshot().let { saved -> saved.enabled == autoBefore.enabled && saved.candidateIds == autoBefore.candidateIds }) }
        } finally { storeWriters.shutdownNow() }
        val automatic = AutoServerSettingsStore(context)
        val observerReady = java.util.concurrent.CountDownLatch(1)
        val published = java.util.concurrent.CountDownLatch(1)
        val publishedUnderLock = java.util.concurrent.atomic.AtomicBoolean(false)
        val freshScope = setOf("fresh-${java.util.UUID.randomUUID()}")
        val observer = launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            automatic.state.collect { options ->
                observerReady.countDown()
                if (options.candidateIds == freshScope) {
                    publishedUnderLock.set(Thread.holdsLock(ProfileVault.lock))
                    published.countDown()
                }
            }
        }
        try {
            check(observerReady.await(5, java.util.concurrent.TimeUnit.SECONDS))
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                automatic.save(false, freshScope)
                // The posted preference observer cannot run inside this main
                // callback. Default scope must come from disk, not stale flow.
                check(automatic.state.value.candidateIds != freshScope)
                automatic.save(false)
                check(automatic.snapshot().candidateIds == freshScope)
            }
            check(published.await(5, java.util.concurrent.TimeUnit.SECONDS))
            check(!publishedUnderLock.get()) { "Automatic controller resumed under the store monitor" }
        } finally {
            observer.cancelAndJoin()
            automatic.close()
            AutoServerSettingsStore(context).use { it.save(autoBefore.enabled, autoBefore.candidateIds) }
        }
        repository.remove(source.id, keepServers = false)
        check(vault.loadSnapshot().memberships == setOf(SubscriptionMembership(node.id, other.id)))
        check(SelectedProfileStore(context).load() == node.id)
        val scheduled = vault.loadSnapshot().subscriptions.single().copy(intervalHours = 6)
        repository.save(scheduled)
        repeat(2) { SubscriptionScheduling.apply(context, listOf(scheduled)).forEach { it.result.get(10, java.util.concurrent.TimeUnit.SECONDS) } }
        val works = androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWork("nova-subscription-${scheduled.id}")
            .get(10, java.util.concurrent.TimeUnit.SECONDS)
        check(works.count { !it.state.isFinished } == 1) { "Duplicate subscription schedule" }
        val disabled = scheduled.copy(intervalHours = 0)
        repository.save(disabled)
        SubscriptionScheduling.apply(context, listOf(disabled)).forEach { it.result.get(10, java.util.concurrent.TimeUnit.SECONDS) }
        check(androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWork("nova-subscription-${scheduled.id}")
            .get(10, java.util.concurrent.TimeUnit.SECONDS).all { it.state.isFinished })
        val encrypted = String(file.readBytes(), Charsets.ISO_8859_1)
        for (secret in listOf("TEST_TOKEN", "TEST_OTHER", "11111111-1111", "My fixture")) check(!encrypted.contains(secret))
        AppPreferencesStore(context).apply { dismissTutorial(); setAutoConnectEnabled(false); setUpdateChecksEnabled(false) }
    }
}
