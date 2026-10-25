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
class IdentityVault(context: Context, testNamespace: Boolean = false) {
    private val recoveryContext = context.applicationContext
    private val file = AtomicFile(File(context.filesDir, if (testNamespace) "identities-test.vault" else "identities.vault"))
    private val alias = if (testNamespace) "nova.vpn.identity-test.v1" else "nova.vpn.identity-store.v1"

    fun load(): IdentityState = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized IdentityState()
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > HEADER_SIZE + GCM_TAG_BYTES) { "Хранилище профилей повреждено" }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Неизвестный формат хранилища профилей" }
        val iv = bytes.copyOfRange(MAGIC.size, HEADER_SIZE)
        val encrypted = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        IdentityState.fromJson(json)
    }

    fun save(state: IdentityState) = synchronized(ProfileVault.lock) {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        state.validate()
        val json = state.toJson()
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
