package com.semmyx.ledger.data

import com.semmyx.ledger.data.billing.PurchaseSignatureVerifier
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class PurchaseSignatureVerifierTest {
    @Test fun acceptsCorrectSignatureAndRejectsTampering() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val payload = "{\"productId\":\"ledger_plus\"}"
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private); update(payload.toByteArray()); Base64.getEncoder().encodeToString(sign())
        }
        val verifier = PurchaseSignatureVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
        assertTrue(verifier.configured)
        assertTrue(verifier.verify(payload, signature))
        assertFalse(verifier.verify(payload + " ", signature))
        assertFalse(verifier.verify(payload, "invalid"))
    }
    @Test fun missingLicenseKeyFailsClosed() {
        val verifier = PurchaseSignatureVerifier("")
        assertFalse(verifier.configured)
        assertFalse(verifier.verify("{}", ""))
    }
}
