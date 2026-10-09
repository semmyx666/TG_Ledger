package com.semmyx.ledger.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Play's purchase signature algorithm is SHA1withRSA (a protocol requirement,
 * not the PIN KDF). No entitlement is granted when the key is missing / invalid.
 * For high-value products, server verification + RTDN must augment client checks.
 */
class PurchaseSignatureVerifier(base64PublicKey: String) {
    private val publicKey = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim())))
    } catch (_: Exception) { null }
    val configured: Boolean get() = publicKey != null
    fun verify(json: String, signature: String): Boolean {
        val key = publicKey ?: return false
        return try {
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(json.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature))
            }
        } catch (_: Exception) { false }
    }
}
