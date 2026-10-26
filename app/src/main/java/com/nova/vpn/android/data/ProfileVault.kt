package com.nova.vpn.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-private AES-256-GCM vault; VPN secrets are never written as cleartext JSON. */
class ProfileVault(context: Context) {
    private val recoveryContext = context.applicationContext
    private val file = AtomicFile(File(context.filesDir, "profiles.vault"))
    private val alias = "nova.vpn.profile-store.v1"

    fun load(): List<ImportedProfile> = loadSnapshot().profiles
    internal fun loadSnapshot(): ProfileSnapshot = synchronized(lock) { readLocked() }
    internal fun updateSnapshot(transform: (ProfileSnapshot) -> ProfileSnapshot): ProfileSnapshot = synchronized(lock) {
        transform(readLocked()).also { writeLocked(it) }
    }
    /** A checked recovery pre-image must also replace an unreadable crash-damaged target. */
    internal fun restoreSnapshot(snapshot: ProfileSnapshot) = synchronized(lock) { writeLocked(snapshot) }

    /** Transform the current disk state, rather than overwriting it with a stale UI snapshot. */
    fun update(transform: (List<ImportedProfile>) -> List<ImportedProfile>): List<ImportedProfile> = synchronized(lock) {
        val previous = readLocked()
        val next = transform(previous.profiles)
        writeLocked(previous.withProfiles(next))
        next
    }

    private fun readLocked(): ProfileSnapshot {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return ProfileSnapshot()
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > HEADER_SIZE + GCM_TAG_BYTES) { "Хранилище профилей повреждено" }
        require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Неизвестный формат хранилища профилей" }
        val iv = bytes.copyOfRange(MAGIC.size, HEADER_SIZE)
        val encrypted = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        // Legacy arrays migrate on the next successful write; load never rewrites a vault.
        return ProfileDocument.decodeSnapshot(String(cipher.doFinal(encrypted), Charsets.UTF_8))
    }

    fun save(profiles: List<ImportedProfile>) = synchronized(lock) { writeLocked(readLocked().withProfiles(profiles)) }

    private fun writeLocked(snapshot: ProfileSnapshot) {
        StoreRecoveryGuard.beforeAccess(recoveryContext)
        val json = ProfileDocument.encodeSnapshot(snapshot)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val bytes = MAGIC + cipher.iv + encrypted
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            file.finishWrite(stream)
            revisions.value += 1
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

    companion object {
        private var replacement = 0L
        internal val replacementGeneration: Long get() = synchronized(lock) { replacement }
        /** In-flight provider responses from before full restoration must not merge afterwards. */
        internal fun invalidatePendingUpdates() = synchronized(lock) { replacement++ }
        private val revisions = MutableStateFlow(0L)
        internal val changes = revisions.asStateFlow()
        // All instances in the application process share a writer. Background workers must
        // use update; VPN/check processes are readers and never modify this document.
        val lock = Any()
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val MAGIC = byteArrayOf('N'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), 1)
        const val HEADER_SIZE = 16
        const val GCM_TAG_BYTES = 16
    }
}
