package com.nova.vpn.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.SystemClock
import com.nova.vpn.android.data.*

/** A bounded synthetic TUN for measuring visibility from a different Android user. */
internal object ProfileVpnHold {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        val result = Bundle()
        var outcome = Activity.RESULT_CANCELED
        var activity: Activity? = null
        try {
            check(context.packageName == "com.nova.vpn.android.usbtest.realitypreview")
            check(VpnService.prepare(context) == null) { "Test VPN consent missing" }
            AppPreferencesStore(context).apply {
                dismissTutorial()
                setAutoConnectEnabled(false)
                setUpdateChecksEnabled(false)
                setVpnEngine(VpnEngine.SING_BOX)
            }
            val profile = ImportedProfile(name = "Profile isolation fixture", protocol = "json",
                server = "fixture", port = 0, outboundJson = "{}",
                fullConfigJson = """{"outbounds":[{"type":"direct","tag":"direct"}],"route":{"final":"direct"}}""")
            val config = SingBoxConfigFactory.build(profile, ownPackage = context.packageName)
            context.startForegroundService(Intent(context, NovaVpnService::class.java)
                .setAction(NovaVpnService.ACTION_START).putExtra(NovaVpnService.EXTRA_CONFIG, config)
                .putExtra(NovaVpnService.EXTRA_PROFILE_NAME, "Profile isolation fixture"))
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (!VpnRuntime.state.value.connected && VpnRuntime.state.value.error == null &&
                SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            check(VpnRuntime.state.value.connected) { "Test VPN failed: ${VpnRuntime.state.value.error}" }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PROFILE_VPN_READY\n") })
            val until = SystemClock.elapsedRealtime() + 120000
            while (SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
            result.putString("stream", "Profile VPN hold finished\n")
            outcome = Activity.RESULT_OK
        } catch (error: Throwable) {
            result.putString("stream", "Profile VPN hold failed: ${error.javaClass.simpleName}: ${error.message}\n")
        } finally {
            runCatching { context.startService(Intent(context, NovaVpnService::class.java).setAction(NovaVpnService.ACTION_STOP)) }
            activity?.let { window -> instrumentation.runOnMainSync { window.finish() } }
        }
        instrumentation.finish(outcome, result)
    }
}
