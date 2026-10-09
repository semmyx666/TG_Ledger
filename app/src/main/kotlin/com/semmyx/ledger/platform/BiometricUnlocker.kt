package com.semmyx.ledger.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.semmyx.ledger.data.security.BiometricKeys
import java.security.SecureRandom

/** A callback without the crypto object is never sufficient to unlock. */
class BiometricUnlocker(
    private val activity: FragmentActivity,
    private val keys: BiometricKeys,
) {
    private var prompt: BiometricPrompt? = null
    fun authenticate(title: String, subtitle: String, negative: String, success: () -> Unit, failure: () -> Unit) {
        try {
            val cipher = keys.authenticationCipher()
            val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val biometricPrompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authenticated = requireNotNull(result.cryptoObject?.cipher)
                            require(authenticated === cipher)
                            authenticated.doFinal(challenge) // Keystore enforces a fresh strong-biometric auth.
                            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) success()
                        } catch (_: Exception) { failure() }
                        finally { challenge.fill(0) }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        challenge.fill(0)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_CANCELED) failure()
                    }
                    override fun onAuthenticationFailed() { failure() }
                })
            prompt = biometricPrompt
            val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)
                .setNegativeButtonText(negative).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(true).build()
            biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            // Invalidated enrollment keys are not recreated at the lock screen; use the PIN.
            failure()
        }
    }
    fun cancel() { prompt?.cancelAuthentication(); prompt = null }
}
