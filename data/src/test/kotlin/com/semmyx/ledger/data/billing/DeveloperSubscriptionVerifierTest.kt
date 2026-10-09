package com.semmyx.ledger.data.billing

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSubscriptionVerifierTest {
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    /** Mirrors tools/issue_activation_code.py's wire format: "v1|installationId|productId|issuedAt|expiresAt". */
    private fun token(installationId: String, productId: String, expiresAtMillis: Long?, issuedAtMillis: Long = 1L): String {
        val payload = listOf("v1", installationId, productId, issuedAtMillis.toString(), expiresAtMillis?.toString() ?: "")
            .joinToString("|").toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private); update(payload); sign()
        }
        val encoder = Base64.getUrlEncoder()
        return "${encoder.encodeToString(payload)}.${encoder.encodeToString(signature)}"
    }

    @Test
    fun acceptsAValidUnexpiredCodeForTheRightInstallation() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        assertTrue(verifier.configured)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        val result = verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000)
        assertEquals(10_000L, result?.expiresAtMillis)
        assertEquals("AAAA-BBBB-CCCC", result?.installationId)
        assertEquals("ledger_plus", result?.productId)
    }

    @Test
    fun acceptsACodeWithNoExpiryAtAll() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = null)
        val result = verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 999_999_999)
        assertNull(result?.expiresAtMillis)
        assertEquals("AAAA-BBBB-CCCC", result?.installationId)
    }

    @Test
    fun rejectsAnExpiredCode() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 1_000)
        assertNull(verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun rejectsACodeIssuedForADifferentInstallation() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        assertNull(verifier.verify(code, "ZZZZ-ZZZZ-ZZZZ", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun rejectsACodeIssuedForADifferentProduct() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        assertNull(verifier.verify(code, "AAAA-BBBB-CCCC", "other_product", nowMillis = 5_000))
    }

    @Test
    fun rejectsATamperedPayload() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        val parts = code.split(".")
        val forgedPayload = Base64.getUrlEncoder().encodeToString(
            listOf("v1", "AAAA-BBBB-CCCC", "ledger_plus", "0", "99999999999").joinToString("|").toByteArray(Charsets.UTF_8),
        )
        assertNull(verifier.verify("$forgedPayload.${parts[1]}", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun rejectsAMalformedCodeWithoutCrashing() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        assertNull(verifier.verify("not-a-real-code", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
        assertNull(verifier.verify("", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
        assertNull(verifier.verify(".", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun unconfiguredVerifierRejectsEverything() {
        val verifier = DeveloperSubscriptionVerifier("")
        assertFalse(verifier.configured)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        assertNull(verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }
}
