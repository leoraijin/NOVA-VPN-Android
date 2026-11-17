package com.nova.vpn.android.data

import android.content.pm.PackageManager

class SharedUidRoutingException(message: String) : IllegalArgumentException(message)

/** Validates a caller-supplied complete UID inventory; does not query Android or change visibility. */
object SharedUidRouting {
    /** Rechecks every member so a stale/incomplete inventory cannot create a false group. */
    fun groupFor(name: String, uidOf: (String) -> Int?, packagesOf: (Int) -> Set<String>): Set<String> {
        val uid=uidOf(name)?.takeIf { it>=0 }
            ?: throw SharedUidRoutingException("Приложение недоступно: $name. Обновите список приложений.")
        val members=packagesOf(uid).toSet()
        if(name !in members || members.any { uidOf(it)!=uid })
            throw SharedUidRoutingException("Состав группы приложений изменился. Обновите список и повторите назначение.")
        return members
    }

    fun validate(settings: MultiServerSettings, uidOf: (String) -> Int?, packagesOf: (Int) -> Set<String>, excludedPackage: String? = null) {
        settings.validate()
        val rules=settings.effectiveRules().filter { it.packageName!=null }.associateBy { it.packageName!! }
        fun effective(target: ServerRouteTarget?) = if(target==null||target.kind==ServerRouteKind.DEFAULT)
            ServerRouteTarget(ServerRouteKind.PROFILE,settings.defaultProfileId) else target
        val checked=mutableSetOf<String>()
        rules.keys.forEach { name ->
            if(name in checked) return@forEach
            val members=groupFor(name,uidOf,packagesOf)
            if(excludedPackage in members) throw SharedUidRoutingException("Группа содержит NOVA VPN и не может получить отдельный маршрут.")
            checked.addAll(members)
            if(members.map { effective(rules[it]?.target) }.distinct().size>1)
                throw SharedUidRoutingException("Приложения имеют общий сетевой идентификатор: ${members.sorted().take(8).joinToString()}. Назначьте им одинаковый маршрут.")
        }
    }
}
/** Returns saved package rules that should remain dormant until Android can resolve them. */
internal fun validateInstalledRoutingApps(
    settings: MultiServerSettings,
    pm: PackageManager,
    ownPackage: String
): Set<String> {
    val packageUids = mutableMapOf<String, Int?>()
    fun uidOf(name: String): Int? {
        if (!packageUids.containsKey(name)) {
            packageUids[name] = try {
                pm.getApplicationInfo(name, 0).uid
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
        return packageUids[name]
    }
    val missing = settings.rules.mapNotNull { it.packageName }.filter { uidOf(it) == null }.toSet()
    val installedRules = settings.rules.filterNot { it.packageName in missing }
    SharedUidRouting.validate(settings.copy(rules = installedRules),
        ::uidOf, { uid -> pm.getPackagesForUid(uid)?.toSet().orEmpty() }, ownPackage)
    return missing
}
