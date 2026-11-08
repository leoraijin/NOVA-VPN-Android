package com.nova.vpn.android.data

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
        val rules=settings.rules.filter { it.packageName!=null }.associateBy { it.packageName!! }
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