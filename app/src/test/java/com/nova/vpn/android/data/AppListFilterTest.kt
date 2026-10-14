package com.nova.vpn.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {
    private val apps = listOf(
        AppListEntry("com.discord", "Discord"),
        AppListEntry("com.google.android.youtube", "YouTube"),
        AppListEntry("org.mozilla.firefox", "Firefox")
    )

    @Test
    fun blankQueryKeepsAllAppsAndTheirOrder() {
        assertEquals(apps, filterAppList(apps, "  "))
    }

    @Test
    fun filtersVisibleNamesWithoutCaseSensitivity() {
        assertEquals(listOf(apps[1]), filterAppList(apps, "youTUBE"))
    }

    @Test
    fun filtersByPackageIdentifier() {
        assertEquals(listOf(apps[0]), filterAppList(apps, "  COM.DISCORD  "))
    }

    @Test
    fun unmatchedQueryReturnsNoApps() {
        assertEquals(emptyList<AppListEntry>(), filterAppList(apps, "not-installed"))
    }
}
