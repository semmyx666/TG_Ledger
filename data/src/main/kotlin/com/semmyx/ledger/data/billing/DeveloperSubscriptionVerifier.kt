package com.semmyx.ledger.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies activation codes issued manually by the developer (e.g. after a Telegram sale),
 * for installs where a Google Play purchase is not available or not yet published.
 * Only the developer's private key (never shipped in the app; see tools/issue_activation_code.py)
 * can issue a code. This class only ever holds the public key and can only verify, never issue.
 *
 * Code format (a plain-text token, safe to paste/copy from a chat app):
 *   base64url(payload) + "." + base64url(signature)
 * payload (before base64url; a pipe-delimited string, not JSON, so this class stays testable
 * without depending on Android's org.json stubs) is:
 *   "v1|<installationId>|<productId>|<issuedAtMillis>|<expiresAtMillisOrEmpty>"
 * signed with SHA256withRSA over the raw payload bytes.
 */
class DeveloperSubscriptionVerifier(base64PublicKey: String) {
    private val publicKey = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim())))
    } catch (_: Exception) { null }
    val configured: Boolean get() = publicKey != null

    data class Activation(
        val installationId: String,
        val productId: String,
        val issuedAtMillis: Long,
        val expiresAtMillis: Long?,
    )

    /**
     * Returns the decoded activation only if the signature is valid, the code targets this
     * exact installation and product, and (when present) it has not expired as of [nowMillis].
     * Returns null for any wrong, tampered, expired, or mismatched code, or when unconfigured.
     */
    fun verify(code: String, installationId: String, productId: String, nowMillis: Long = System.currentTimeMillis()): Activation? {
        val key = publicKey ?: return null
        val trimmed = code.trim()
        val dot = trimmed.lastIndexOf('.')
        if (dot <= 0 || dot == trimmed.length - 1) return null
        val payloadSegment = trimmed.substring(0, dot)
        val signatureSegment = trimmed.substring(dot + 1)
        return try {
            val payloadBytes = Base64.getUrlDecoder().decode(payloadSegment)
            val signatureBytes = Base64.getUrlDecoder().decode(signatureSegment)
            val verified = Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update(payloadBytes)
                verify(signatureBytes)
            }
            if (!verified) return null
            val fields = payloadBytes.toString(Charsets.UTF_8).split("|")
            if (fields.size != 5 || fields[0] != "v1") return null
            val expiry = if (fields[4].isEmpty()) null else fields[4].toLongOrNull() ?: return null
            val issued = fields[3].toLongOrNull() ?: return null
            if (issued <= 0 || issued > nowMillis + 300000 || (expiry != null && expiry <= issued)) return null
            val activation = Activation(
                installationId = fields[1],
                productId = fields[2],
                issuedAtMillis = issued,
                expiresAtMillis = expiry,
            )
            if (activation.installationId != installationId || activation.productId != productId) return null
            if (activation.expiresAtMillis != null && activation.expiresAtMillis <= nowMillis) return null
            activation
        } catch (_: Exception) { null }
    }
}
