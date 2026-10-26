package com.nova.vpn.android

import android.content.Context
import androidx.work.WorkerParameters

/** Deliberate failure exists only in the preview source set, never in optimized APKs. */
class SubscriptionScheduleFailureWorker(context: Context, parameters: WorkerParameters) : SubscriptionScheduleWorker(context, parameters) {
    override suspend fun reconcileSchedules(): Boolean {
        check(applicationContext.packageName == "com.nova.vpn.android.usbtest.realitypreview")
        throw java.io.IOException("Synthetic schedule reconciliation failure")
    }
}
