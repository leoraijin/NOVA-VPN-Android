package com.nova.vpn.android.data

import android.graphics.drawable.Drawable
import java.util.Locale

data class AppListEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null
) {
    // Built once when package metadata loads, rather than allocating two strings per keystroke.
    internal val searchLabel = label.lowercase(Locale.ROOT)
    internal val searchPackage = packageName.lowercase(Locale.ROOT)
}

/** Filters installed apps by their visible name or Android package identifier. */
fun filterAppList(apps: List<AppListEntry>, query: String): List<AppListEntry> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isEmpty()) return apps

    return apps.filter { app ->
        app.searchLabel.contains(normalizedQuery) || app.searchPackage.contains(normalizedQuery)
    }
}
