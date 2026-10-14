package com.nova.vpn.android.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/** Shares non-secret connection status only; VPN profiles and credentials never leave the phone. */
object WearStatusSync {
    const val STATUS_PATH = "/nova/status"
    const val TOGGLE_PATH = "/nova/toggle"

    fun publish(context: Context, state: String) {
        val request = PutDataMapRequest.create(STATUS_PATH).apply {
            dataMap.putString(KEY_STATE, state)
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(context).putDataItem(request) }
            .onFailure { Log.d(TAG, "Wear status sync is unavailable", it) }
            .onSuccess { task -> task.addOnFailureListener { Log.d(TAG, "Unable to publish Wear status", it) } }
    }

    private const val KEY_STATE = "state"
    private const val TAG = "NovaWearSync"
}
