package com.semmyx.ledger.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Separate per-use, strong-biometric key. Enrollment changes invalidate it.
 * Only an already unlocked Settings screen may provision / replace this key.
 */
class BiometricKeys(context: Context) {
    private val alias = "${context.packageName}.biometric.v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun provision() {
        val keyStore = store()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(spec.build())
            generateKey()
        }
    }
    fun remove() { store().deleteEntry(alias) }
    fun authenticationCipher(): Cipher {
        val key = store().getKey(alias, null) as? SecretKey ?: error("Biometric key unavailable")
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
}
