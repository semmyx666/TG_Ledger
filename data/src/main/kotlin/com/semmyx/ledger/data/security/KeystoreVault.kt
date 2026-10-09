package com.semmyx.ledger.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AES-256-GCM envelope encryption. This vault contains authentication verifiers,
 * lockout state and signed Play receipts, NOT the Room database. AAD binds the
 * ciphertext to this installation's package. Android backup is disabled.
 * Corruption / key loss is intentionally fatal to the operation, never a reset.
 */
class KeystoreVault(context: Context) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "security.vault"))
    private val lock = Mutex()
    private val alias = "${app.packageName}.vault.v1"
    private val aad = "$alias:1".toByteArray(Charsets.UTF_8)
    private val keyStore: KeyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(mayCreate: Boolean): SecretKey {
        val store = keyStore
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(mayCreate) { "Existing vault key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    private fun readUnlocked(): JSONObject {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return JSONObject()
        val bytes = file.openRead().use { input ->
            // Authentication data is bounded, preventing memory exhaustion on a damaged file.
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= 1_048_576) { "Vault exceeds size limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray().also { check(it.size >= 30) { "Invalid vault size" } }
        }
        val buffer = ByteBuffer.wrap(bytes)
        check(buffer.get().toInt() == 1) { "Unsupported vault version" }
        val iv = ByteArray(12).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(mayCreate = false), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val plaintext = cipher.doFinal(ciphertext)
        return try { JSONObject(plaintext.toString(Charsets.UTF_8)) } finally { plaintext.fill(0) }
    }

    private fun writeUnlocked(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(mayCreate = !file.baseFile.exists()))
        cipher.updateAAD(aad)
        val plaintext = value.toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(1.toByte()).put(cipher.iv).put(encrypted).array()
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    suspend fun <T> read(block: (JSONObject) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { block(readUnlocked()) }
    }
    /** Serial read-modify-write prevents PIN and billing updates from clobbering each other. */
    suspend fun <T> update(block: (JSONObject) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val value = readUnlocked()
            val result = block(value)
            writeUnlocked(value)
            result
        }
    }
}
