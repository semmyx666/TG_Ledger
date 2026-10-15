package com.semmyx.ledger.data.billing

import com.semmyx.ledger.domain.ActivationProblem
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale

/** Offline RSA verification. v1 grants remain valid; v2 identifies the signing key. Never contains a private key. */
class DeveloperSubscriptionVerifier(base64PublicKey: String) {
    private val publicKey = try {
        (KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim()))) as RSAPublicKey)
            .takeIf { it.modulus.bitLength() >= 2048 }
    } catch (_: Exception) { null }
    val configured: Boolean get() = publicKey != null
    val keyId: String = publicKey?.encoded?.let { encoded ->
        MessageDigest.getInstance("SHA-256").digest(encoded).take(8).joinToString("") { "%02X".format(it) }
    }.orEmpty()

    data class Activation(val installationId: String, val productId: String, val issuedAtMillis: Long, val expiresAtMillis: Long?)
    data class Result(val activation: Activation? = null, val problem: ActivationProblem? = null)

    /** Accept chat line wrapping/zero-width separators, not arbitrary prose or changed payload characters. */
    fun normalize(code: String): String = code.filterNot { it.isWhitespace() || it == '\u200B' || it == '\uFEFF' }.trim()

    fun verify(code: String, installationId: String, productId: String, nowMillis: Long = System.currentTimeMillis()): Activation? =
        check(code, installationId, productId, nowMillis).activation

    fun check(code: String, installationId: String, productId: String, nowMillis: Long = System.currentTimeMillis()): Result {
        fun fail(problem: ActivationProblem) = Result(problem = problem)
        val key = publicKey ?: return fail(ActivationProblem.NOT_CONFIGURED)
        if (code.length > 16384) return fail(ActivationProblem.MALFORMED)
        val parts = normalize(code).split('.')
        val v2 = parts.size == 4 && parts[0] == "TG2"
        if ((!v2 && parts.size != 2) || parts.any { it.isEmpty() }) return fail(ActivationProblem.MALFORMED)
        if (v2 && !parts[1].matches(Regex("[A-F0-9]{16}"))) return fail(ActivationProblem.MALFORMED)
        if (v2 && parts[1] != keyId) return fail(ActivationProblem.KEY_MISMATCH)
        return try {
            val payloadBytes = Base64.getUrlDecoder().decode(parts[if (v2) 2 else 0])
            val signatureBytes = Base64.getUrlDecoder().decode(parts[if (v2) 3 else 1])
            val verified = Signature.getInstance("SHA256withRSA").run { initVerify(key); update(payloadBytes); verify(signatureBytes) }
            if (!verified) return fail(ActivationProblem.SIGNATURE)
            val fields = payloadBytes.toString(Charsets.UTF_8).split('|')
            val body = if (v2) {
                if (fields.size != 6 || fields[0] != "v2" || fields[1] != keyId) return fail(ActivationProblem.MALFORMED)
                fields.drop(2)
            } else {
                if (fields.size != 5 || fields[0] != "v1") return fail(ActivationProblem.MALFORMED)
                fields.drop(1)
            }
            val issued = body[2].toLongOrNull() ?: return fail(ActivationProblem.MALFORMED)
            val expiry = if (body[3].isEmpty()) null else body[3].toLongOrNull() ?: return fail(ActivationProblem.MALFORMED)
            if (issued <= 0 || (expiry != null && expiry <= issued)) return fail(ActivationProblem.MALFORMED)
            if (issued > nowMillis && issued - nowMillis > 300000L) return fail(ActivationProblem.CLOCK)
            if (normalizeId(body[0]) != normalizeId(installationId)) return fail(ActivationProblem.DEVICE)
            if (body[1] != productId) return fail(ActivationProblem.PRODUCT)
            if (expiry != null && expiry <= nowMillis) return fail(ActivationProblem.EXPIRED)
            Result(activation = Activation(body[0], body[1], issued, expiry))
        } catch (_: Exception) { fail(ActivationProblem.MALFORMED) }
    }

    private fun normalizeId(id: String) = id.trim().uppercase(Locale.ROOT)
}
