package com.nova.vpn.android

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

internal object AutoSwitchBudgetSmoke {
    fun run(context: Context, phase: String = "roundtrip") {
        require(phase in setOf("roundtrip", "prepare", "verify"))
        check(isIsolatedUsbTestPackage(context.packageName))
        check(!VpnRuntime.state.value.connected && !VpnRuntime.state.value.connecting)
        val preferences = context.getSharedPreferences("auto_switch_budget", Context.MODE_PRIVATE)
        val previous = preferences.getString("budget_v1", null)
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        check(boot >= 0)
        fun restoreBackup() {
            check(preferences.contains("smoke_original_present"))
            val editor = preferences.edit()
            if (preferences.getBoolean("smoke_original_present", false))
                editor.putString("budget_v1", preferences.getString("smoke_original_value", null))
            else editor.remove("budget_v1")
            check(editor.remove("smoke_original_present").remove("smoke_original_value").commit())
        }
        if (phase == "prepare") {
            check(!preferences.contains("smoke_original_present"))
            check(preferences.edit().putBoolean("smoke_original_present", previous != null)
                .putString("smoke_original_value", previous.orEmpty()).remove("budget_v1").commit())
            try {
                repeat(3) { check(AutoSwitchBudgetStore(context).reserve("cross-process-$it")) }
            } catch (error: Throwable) { restoreBackup(); throw error }
            return
        }
        if (phase == "verify") {
            try {
                check(preferences.contains("smoke_original_present"))
                val restored = AutoSwitchBudget.decode(previous, boot, SystemClock.elapsedRealtime())
                check(restored.events.map { it.id }.toSet() == setOf("cross-process-0", "cross-process-1", "cross-process-2"))
                check(!AutoSwitchBudgetStore(context).reserve("cross-process-fourth"))
                AutoSwitchBudgetStore(context).finish("cross-process-0", false)
                check(!AutoSwitchBudgetStore(context).reserve("cross-process-retry"))
            } finally { restoreBackup() }
            return
        }
        try {
            check(preferences.edit().remove("budget_v1").commit())
            repeat(3) { check(AutoSwitchBudgetStore(context).reserve("android-reservation-$it")) }
            check(!AutoSwitchBudgetStore(context).reserve("android-fourth"))
            val restored = AutoSwitchBudget.decode(preferences.getString("budget_v1", null), boot, SystemClock.elapsedRealtime())
            check(restored.events.size == 3)
            AutoSwitchBudgetStore(context).finish("android-reservation-1", false)
            check(!AutoSwitchBudgetStore(context).reserve("android-retry-too-early"))
            val failed = AutoSwitchBudget.decode(preferences.getString("budget_v1", null), boot, SystemClock.elapsedRealtime())
            check(failed.events.size == 2 && failed.retryAtMillis > SystemClock.elapsedRealtime())
        } finally {
            val editor = preferences.edit()
            if (previous == null) editor.remove("budget_v1") else editor.putString("budget_v1", previous)
            check(editor.commit())
        }
    }
}
