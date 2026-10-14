package com.nova.vpn.android.data

import java.net.IDN

/** App selection policy used by both the UI and Android VPN configuration. */
enum class AppRoutingMode { INCLUDE_SELECTED, EXCLUDE_SELECTED }

data class AppRoutingSettings(
    val mode: AppRoutingMode = AppRoutingMode.EXCLUDE_SELECTED,
    val packages: Set<String> = emptySet(),
    val sites: List<SiteRoutingRule> = emptyList()
)

enum class SiteRoutingTarget { VPN, DIRECT }

data class SiteRoutingRule(
    val domain: String,
    val target: SiteRoutingTarget = SiteRoutingTarget.VPN
)

/** Accepts a bare hostname and returns a canonical ASCII domain for sing-box rules. */
object SiteDomain {
    private val labelPattern = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")

    fun normalize(input: String): String? {
        val candidate = input.trim().trimEnd('.').lowercase()
        if (candidate.isEmpty() || candidate.any { it.isWhitespace() }) return null
        if (candidate.contains("://") || candidate.any { it in "/\\:*@?#" }) return null

        val ascii = runCatching { IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase() }
            .getOrNull() ?: return null
        if (ascii.length > 253) return null
        val labels = ascii.split('.')
        if (labels.size < 2 || labels.any { it.length !in 1..63 || !labelPattern.matches(it) }) return null
        return ascii
    }
}
