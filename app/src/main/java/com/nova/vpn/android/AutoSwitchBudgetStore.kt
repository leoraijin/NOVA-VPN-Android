package com.nova.vpn.android

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** Reserve durably BEFORE dispatch. An unacknowledged operation still consumes a slot after a crash. */
internal class AutoSwitchBudgetStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("auto_switch_budget", Context.MODE_PRIVATE)
    private val resolver = context.applicationContext.contentResolver
    private fun boot() = runCatching { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT) }.getOrNull()?.takeIf { it >= 0 }

    fun reserve(id: String): Boolean = synchronized(LOCK) {
        val boot = boot() ?: return@synchronized false
        val now = SystemClock.elapsedRealtime()
        val budget = AutoSwitchBudget.decode(preferences.getString(KEY, null), boot, now)
        val reserved = budget.reserve(id, now)
        // Persist repaired/cooldown state even on refusal, rather than extending it on each read.
        check(preferences.edit().putString(KEY, (reserved ?: budget).encode()).commit()) { "Unable to persist automatic switch budget" }
        reserved != null
    }

    fun finish(id: String, applied: Boolean) = synchronized(LOCK) {
        val boot = boot() ?: return@synchronized
        val now = SystemClock.elapsedRealtime()
        val budget = AutoSwitchBudget.decode(preferences.getString(KEY, null), boot, now)
        val next = budget.finish(id, applied, now)
        check(preferences.edit().putString(KEY, next.encode()).commit()) { "Unable to persist automatic switch acknowledgement" }
    }

    companion object {
        private const val KEY = "budget_v1"
        private val LOCK = Any()
    }
}
