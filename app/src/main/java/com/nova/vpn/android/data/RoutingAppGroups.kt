package com.nova.vpn.android.data

import android.content.pm.PackageManager

/** UI inventory includes non-launchable siblings; assignments still revalidate at prepare time. */
internal data class RoutingAppGroup(val members: List<AppListEntry>, val unavailableReason: String? = null) {
    val packages: Set<String> get() = members.map { it.packageName }.toSet()
    val key: String get() = packages.sorted().joinToString("|")
}

internal fun loadRoutingAppGroups(pm: PackageManager, ownPackage: String,
    apps: List<AppListEntry>, assignedPackages: Set<String>): List<RoutingAppGroup> {
    val known=apps.associateBy { it.packageName }
    @Suppress("DEPRECATION")
    val launchable=pm.getInstalledApplications(0).filter { it.packageName!=ownPackage && pm.getLaunchIntentForPackage(it.packageName)!=null }.map { it.packageName }
    val names=(known.keys+launchable+assignedPackages).sorted()
    val result=mutableListOf<RoutingAppGroup>()
    val seen=mutableSetOf<String>()
    // Cache within this one read, never across installs or reloads.
    val info=mutableMapOf<String,android.content.pm.ApplicationInfo?>()
    fun metadata(name: String) = info.getOrPut(name) { runCatching { pm.getApplicationInfo(name,0) }.getOrNull() }
    fun entry(name: String): AppListEntry {
        val value=metadata(name)
        return AppListEntry(name, value?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?: known[name]?.label ?: name,
            value?.let { runCatching { pm.getApplicationIcon(it) }.getOrNull() })
    }
    names.forEach { name ->
        if(name in seen) return@forEach
        val group=try {
            val members=SharedUidRouting.groupFor(name,{metadata(it)?.uid},{pm.getPackagesForUid(it)?.toSet().orEmpty()})
            RoutingAppGroup(members.sorted().map(::entry), if(ownPackage in members) "Группа содержит NOVA VPN" else null)
        } catch(_:SharedUidRoutingException) {
            RoutingAppGroup(listOf(entry(name)),"Приложение недоступно или состав группы изменился")
        }
        seen.addAll(group.packages)
        result+=group
    }
    return result.sortedBy { it.members.first().label.lowercase(java.util.Locale.ROOT) }
}

/** Apply/remove the entire displayed group while retaining site rules and other app assignments. */
internal fun assignRoutingApps(rules: List<MultiServerRule>, packages: Set<String>, target: ServerRouteTarget?): List<MultiServerRule> =
    rules.filterNot { it.packageName in packages } + if(target==null) emptyList()
    else packages.sorted().map { MultiServerRule(packageName=it,target=target) }
