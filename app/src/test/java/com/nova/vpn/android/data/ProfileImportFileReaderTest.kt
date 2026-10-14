package com.nova.vpn.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ProfileImportFileReaderTest {
    @Test
    fun readsUtf8ConfigAndRemovesBom() {
        val source = "\uFEFF{\"outbounds\":[]}".toByteArray(Charsets.UTF_8)
        assertEquals("{\"outbounds\":[]}", ProfileImportFileReader.read(ByteArrayInputStream(source)))
    }

    @Test
    fun rejectsOversizedAndNonTextFiles() {
        val tooLarge = ByteArray(ProfileImportFileReader.MAX_FILE_BYTES + 1) { 'x'.code.toByte() }
        val sizeFailure = runCatching { ProfileImportFileReader.read(ByteArrayInputStream(tooLarge)) }.exceptionOrNull()
        assertTrue(sizeFailure is IOException)

        val binaryFailure = runCatching {
            ProfileImportFileReader.read(ByteArrayInputStream(byteArrayOf(0x7B, 0x00, 0x7D)))
        }.exceptionOrNull()
        assertTrue(binaryFailure is IllegalArgumentException)
    }
}
