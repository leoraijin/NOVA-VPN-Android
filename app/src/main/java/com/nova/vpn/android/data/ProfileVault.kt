package com.nova.vpn.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private AES-256-GCM vault; VPN secrets are never written as cleartext JSON. */
class ProfileVault(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "profiles.vault"))
    private val alias = "nova.vpn.profile-store.v1"

    @Synchronized
    fun load(): List<ImportedProfile> {
        if (!file.baseFile.exists()) return emptyList()
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > HEADER_SIZE + GCM_TAG_BYTES) { "Хранилище профилей повреждено" }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Неизвестный формат хранилища профилей" }
        val iv = bytes.copyOfRange(MAGIC.size, HEADER_SIZE)
        val encrypted = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = JSONArray(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        // Hide known panel status entries imported by older versions. Do not rewrite the
        // encrypted vault during load; retain every real profile and its identity.
        return SubscriptionPlaceholders.withoutPlaceholders(
            List(json.length()) { ImportedProfile.fromJson(json.getJSONObject(it)) }
        )
    }

    @Synchronized
    fun save(profiles: List<ImportedProfile>) {
        val json = JSONArray().apply { profiles.forEach { put(it.toJson()) } }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val bytes = MAGIC + cipher.iv + encrypted
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let(file::failWrite)
            throw error
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val MAGIC = byteArrayOf('N'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), 1)
        const val HEADER_SIZE = 16
        const val GCM_TAG_BYTES = 16
    }
}
