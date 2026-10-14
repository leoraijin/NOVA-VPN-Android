package com.nova.vpn.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteDomainTest {
    @Test
    fun normalizesHostnamesAndInternationalDomains() {
        assertEquals("youtube.com", SiteDomain.normalize("  YouTube.COM. "))
        assertEquals("xn--e1afmkfd.xn--p1ai", SiteDomain.normalize("пример.рф"))
    }

    @Test
    fun rejectsUrlsPathsWildcardsAndMalformedHosts() {
        listOf("https://youtube.com", "youtube.com/watch", "*.youtube.com", "bad..example", "-bad.example")
            .forEach { assertNull("Expected $it to be rejected", SiteDomain.normalize(it)) }
    }
}
