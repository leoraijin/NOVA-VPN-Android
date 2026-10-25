package com.nova.vpn.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Durable encrypted pre-image. All access belongs to the main process store transaction. */
internal class TransferRestoreJournal(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "restore-pending.vault"))
    private val preferencesDirectory = File(context.dataDir, "shared_prefs")
    private fun exists() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    fun prepare(preimage: JSONObject) {
        check(Thread.holdsLock(ProfileVault.lock))
        check(!exists()) { "Не завершено предыдущее восстановление" }
        val plain = preimage.toString().toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_BYTES) { "Слишком большой объём данных для безопасного восстановления" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        cipher.updateAAD(MAGIC)
        val bytes = MAGIC + cipher.iv + cipher.doFinal(plain)
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
            check(file.openRead().use { it.readBytes().contentEquals(bytes) }) { "Не удалось сохранить журнал восстановления" }
            syncDirectory(requireNotNull(file.baseFile.parentFile))
        } catch (error: Exception) {
            stream?.let(file::failWrite)
            throw error
        }
    }

    fun read(): JSONObject? {
        check(Thread.holdsLock(ProfileVault.lock))
        if (!exists()) return null
        val bytes = file.openRead().use { stream ->
            require(stream.channel.size() in 33..(MAX_BYTES + 32).toLong()) { "Повреждён журнал восстановления" }
            val content = stream.readBytes()
            require(content.size in 33..MAX_BYTES + 32) { "Повреждён журнал восстановления" }
            content
        }
        require(bytes.copyOfRange(0, 4).contentEquals(MAGIC)) { "Неизвестный журнал восстановления" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(4, 16)))
        cipher.updateAAD(MAGIC)
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(16, bytes.size)), Charsets.UTF_8))
    }

    fun clear() {
        check(Thread.holdsLock(ProfileVault.lock))
        // Commit all restored file names before making the rollback record disappear.
        if (preferencesDirectory.exists()) syncDirectory(preferencesDirectory)
        syncDirectory(requireNotNull(file.baseFile.parentFile))
        file.delete()
        check(!exists() && !File(file.baseFile.path + ".new").exists()) { "Не удалось завершить восстановление" }
        syncDirectory(requireNotNull(file.baseFile.parentFile))
    }

    private fun syncDirectory(directory: File) {
        val descriptor = android.system.Os.open(directory.absolutePath,
            android.system.OsConstants.O_RDONLY, 0)
        try { android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
    }

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw RestoreKeyUnavailableException()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    private companion object {
        const val ALIAS = "nova.vpn.restore-journal.v1"
        const val MAX_BYTES = 64 * 1024 * 1024
        val MAGIC = byteArrayOf(78, 86, 74, 1)
    }
}

internal class RestoreKeyUnavailableException : IllegalStateException("Недоступен ключ журнала восстановления")
