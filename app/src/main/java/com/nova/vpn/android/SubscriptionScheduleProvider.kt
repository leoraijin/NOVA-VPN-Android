package com.nova.vpn.android

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.nova.vpn.android.data.RestoreRecoveryState

/** After recovery and AndroidX Startup/WorkManager; repairs interrupted scheduling before UI. */
class SubscriptionScheduleProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        if (RestoreRecoveryState.problem.value == null) runCatching {
            SubscriptionScheduling.request(requireNotNull(context), force = true)
        }
        return true
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
