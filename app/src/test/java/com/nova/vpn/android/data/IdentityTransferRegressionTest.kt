package com.nova.vpn.android.data

import java.io.File
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class IdentityTransferRegressionTest {
    private val password = "Local-test-only-2026"
    private val fixture = SubscriptionIdentity(id = "test-profile", name = "Fixture",
        hwid = "0123456789abcdef0123456789abcdef", userAgent = "NOVA-Test/1.0",
        headers = mapOf("X-Test-Token" to "synthetic-not-a-secret"))

    @Test fun encryptedRoundtripRejectsPasswordAndTampering() {
        val state = IdentityState(listOf(fixture))
        val code = IdentityTransfer.export(state, password)
        assertEquals(state, IdentityTransfer.import(code, password))
        assertFalse(code.contains(fixture.hwid))
        assertTrue(runCatching { IdentityTransfer.import(code, "Incorrect-password") }.isFailure)
        val chars = code.toCharArray()
        chars[chars.size - 20] = if (chars[chars.size - 20] == 'A') 'B' else 'A'
        assertTrue(runCatching { IdentityTransfer.import(String(chars), password) }.isFailure)
    }

    @Test fun headersCannotInjectReservedFieldsOrNewlines() {
        assertTrue(runCatching { fixture.copy(headers = mapOf("Host" to "example.test")).validate() }.isFailure)
        assertTrue(runCatching { fixture.copy(userAgent = "test\r\nInjected: yes").validate() }.isFailure)
    }

    @Test fun legacyKdfMatchesStandard() {
        val salt = ByteArray(16) { it.toByte() }
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, 210000, 512)).encoded
        assertArrayEquals(expected, IdentityTransfer.legacyKeys(password, salt))
    }

    @Test fun optionalWindowsInteropUsesOnlySyntheticFiles() {
        val dir = System.getenv("NOVA_INTEROP_DIR")
        assumeTrue(dir != null)
        val identity = IdentityTransfer.import(File(dir, "windows-identity.txt").readText(), password)
        val item = identity.forUrl("https://provider.example.test/sub/test")!!
        assertEquals(fixture.hwid, item.hwid)
        assertEquals(fixture.headers, item.headers)
        File(dir, "android-identity.txt").writeText(IdentityTransfer.export(identity, password))
        val full = FullTransfer.read(File(dir, "windows-full.txt").readText(), password)
        assertEquals("full-test-profile", full.getJSONArray("profiles").getJSONObject(0).getString("id"))
        File(dir, "android-full.txt").writeText(IdentityTransfer.seal(full.toString(), password))
    }
}
