package com.nova.vpn.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidReleaseCheckerTest {
    @Test
    fun comparesNumericVersionParts() {
        assertTrue(AndroidReleaseChecker.isNewerVersion("v0.10.0", "0.9.9"))
        assertTrue(AndroidReleaseChecker.isNewerVersion("1.2", "1.1.99"))
        assertFalse(AndroidReleaseChecker.isNewerVersion("0.1.0", "0.1"))
        assertFalse(AndroidReleaseChecker.isNewerVersion("0.1.0", "0.2.0"))
    }

    @Test
    fun parsesOnlyReleaseAssetsFromTheExpectedRepository() {
        val release = AndroidReleaseChecker.parseLatestRelease(
            """{
                "tag_name":"v0.2.0",
                "html_url":"https://github.com/leoraijin/NOVA-VPN-Android/releases/tag/v0.2.0",
                "body":"App icons, themes, and Wear sync.",
                "assets":[
                    {"name":"nova-universal.apk","browser_download_url":"https://github.com/leoraijin/NOVA-VPN-Android/releases/download/v0.2.0/nova-universal.apk"},
                    {"name":"nova-arm64-v8a.apk","browser_download_url":"https://github.com/leoraijin/NOVA-VPN-Android/releases/download/v0.2.0/nova-arm64-v8a.apk"}
                ]
            }"""
        )

        assertEquals("0.2.0", release?.version)
        assertEquals("App icons, themes, and Wear sync.", release?.notes)
        assertEquals(
            "https://github.com/leoraijin/NOVA-VPN-Android/releases/download/v0.2.0/nova-arm64-v8a.apk",
            release?.downloadUrl
        )
    }

    @Test
    fun rejectsInvalidTagsAndForeignAssetHosts() {
        assertNull(AndroidReleaseChecker.parseLatestRelease("""{"tag_name":"latest"}"""))

        val release = AndroidReleaseChecker.parseLatestRelease(
            """{
                "tag_name":"0.2.0",
                "html_url":"https://github.com.evil.example/release",
                "assets":[{"name":"nova-arm64.apk","browser_download_url":"https://github.com.evil.example/leoraijin/NOVA-VPN-Android/releases/download/0.2.0/app.apk"}]
            }"""
        )
        assertEquals(AndroidReleaseChecker.RELEASES_PAGE, release?.pageUrl)
        assertNull(release?.downloadUrl)
    }
}
