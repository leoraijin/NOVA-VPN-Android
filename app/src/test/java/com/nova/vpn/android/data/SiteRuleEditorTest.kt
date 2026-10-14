package com.nova.vpn.android.data

import org.junit.Assert.*
import org.junit.Test

class SiteRuleEditorTest {
    private val settings = AppRoutingSettings(mode = AppRoutingMode.INCLUDE_SELECTED, packages = setOf("browser"),
        sites = listOf(SiteRoutingRule("old.example", SiteRoutingTarget.DIRECT), SiteRoutingRule("other.example")))

    @Test fun renamePreservesTargetOrderAndAppPolicy() {
        val next = SiteRuleEditor.rename(settings, "old.example", " NEW.Example. ")
        assertEquals(SiteRoutingRule("new.example", SiteRoutingTarget.DIRECT), next.sites[0])
        assertEquals(settings.sites[1], next.sites[1])
        assertEquals(settings.mode, next.mode)
        assertEquals(settings.packages, next.packages)
        assertEquals("old.example", settings.sites[0].domain)
    }
    @Test fun sameDomainCanBeSaved() {
        assertEquals(settings, SiteRuleEditor.rename(settings, "old.example", "OLD.EXAMPLE"))
    }
    @Test fun duplicateAndInvalidDomainsAreRejected() {
        for (value in listOf("other.example", "OTHER.EXAMPLE.", "https://example.com/page", "", "bad domain", "example.com:443")) {
            assertThrows(IllegalArgumentException::class.java) { SiteRuleEditor.rename(settings, "old.example", value) }
        }
    }
    @Test fun missingOriginalNeverCreatesNewRule() {
        assertThrows(IllegalArgumentException::class.java) { SiteRuleEditor.rename(settings, "missing.example", "new.example") }
    }
    @Test fun internationalDomainIsCanonicalAndVpnTargetStaysVpn() {
        val next = SiteRuleEditor.rename(settings, "other.example", "пример.рф")
        assertEquals("xn--e1afmkfd.xn--p1ai", next.sites[1].domain)
        assertEquals(SiteRoutingTarget.VPN, next.sites[1].target)
    }
}
