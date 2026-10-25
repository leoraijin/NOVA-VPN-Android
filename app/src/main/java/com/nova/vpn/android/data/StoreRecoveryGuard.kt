package com.nova.vpn.android.data

import android.content.Context

/** Shared monitor excludes other callers; active recovery may re-enter its own stores. */
internal object StoreRecoveryGuard {
    private var pending = false
    private var active = false

    fun beforeAccess(context: Context) {
        check(Thread.holdsLock(ProfileVault.lock))
        if (pending && !active && FullTransfer.recoverPending(context)) runCatching {
            com.nova.vpn.android.SubscriptionScheduling.request(context, force = true)
        }
    }
    fun completed() { check(Thread.holdsLock(ProfileVault.lock)); pending = false; RestoreRecoveryState.completed() }
    fun <T> transaction(action: () -> T): T {
        check(Thread.holdsLock(ProfileVault.lock) && !active)
        pending = true
        active = true
        return try { action() } catch (error: Exception) {
            if (pending) RestoreRecoveryState.failed(error)
            throw error
        } finally { active = false }
    }
}
