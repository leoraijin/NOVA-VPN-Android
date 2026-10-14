package com.nova.vpn.android.data

/** Rename in place, preserving the route target, list order and unrelated app settings. */
object SiteRuleEditor {
    fun rename(settings: AppRoutingSettings, original: String, input: String): AppRoutingSettings {
        val domain = SiteDomain.normalize(input)
            ?: throw IllegalArgumentException("Введите домен, например youtube.com, без адреса страницы.")
        require(settings.sites.any { it.domain == original }) { "Правило уже удалено. Закройте окно и выберите другое." }
        require(settings.sites.none { it.domain != original && SiteDomain.normalize(it.domain) == domain }) {
            "Правило для этого домена уже существует."
        }
        return settings.copy(sites = settings.sites.map { if (it.domain == original) it.copy(domain = domain) else it })
    }
}
