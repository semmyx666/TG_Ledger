package com.semmyx.ledger.data.billing

import com.google.gson.JsonParser
import com.semmyx.ledger.domain.ActivationProblem
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class ActivationDiagnosticsTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val verifier = DeveloperSubscriptionVerifier(Base64.getEncoder().encodeToString(pair.public.encoded))
    private fun signed(fields: List<String>, envelope: String = verifier.keyId): String {
        val raw = fields.joinToString("|").toByteArray()
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(raw); sign() }
        val enc = Base64.getUrlEncoder().withoutPadding()
        return "TG2.$envelope.${enc.encodeToString(raw)}.${enc.encodeToString(signature)}"
    }
    private fun code(installation: String = "AB12-CD34-EF56", product: String = "ledger_plus", issued: Long = 1000, end: String = "10000") =
        signed(listOf("v2",verifier.keyId,installation,product,issued.toString(),end))
    private fun problem(code: String, now: Long = 5000) = verifier.check(code,"AB12-CD34-EF56","ledger_plus",now).problem

    @Test fun pythonV1AndV2SignaturesVerifyInTheActualKotlinVerifier() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("activation_python.json")!!.reader().use { JsonParser.parseReader(it).asJsonObject }
        val v = DeveloperSubscriptionVerifier(fixture["public_key"].asString)
        assertEquals(fixture["key_id"].asString,v.keyId)
        for (name in listOf("code_v1","code_v2")) {
            val result = v.check(fixture[name].asString,fixture["installation_id"].asString,fixture["product_id"].asString,fixture["now_millis"].asLong)
            assertNull(result.problem);assertEquals(1767225600000L+5*86400000L,result.activation!!.expiresAtMillis)
        }
    }
    @Test fun lineWrappingAndChatSeparatorsDoNotInvalidateTheSignature() {
        val wrapped=code().chunked(31).joinToString("\r\n \u200B")
        assertNull(problem("\uFEFF$wrapped\n"));assertNotNull(verifier.verify(wrapped,"AB12-CD34-EF56","ledger_plus",5000))
    }
    @Test fun wrongSigningKeyHasSpecificDiagnostic() {
        assertEquals(ActivationProblem.KEY_MISMATCH,problem(code().replace("TG2.${verifier.keyId}.","TG2.0000000000000000.")))
    }
    @Test fun envelopeMustMatchTheSignedKeyId() {
        assertEquals(ActivationProblem.MALFORMED,problem(signed(listOf("v2","0000000000000000","AB12-CD34-EF56","ledger_plus","1000","10000"))))
    }
    @Test fun wrongDeviceAndWrongProductAreDistinct() {
        assertEquals(ActivationProblem.DEVICE,problem(code(installation="1111-2222-3333")))
        assertEquals(ActivationProblem.PRODUCT,problem(code(product="other_product")))
    }
    @Test fun expiredAndFutureIssuedAreDistinct() {
        assertEquals(ActivationProblem.EXPIRED,problem(code(end="5000")))
        assertEquals(ActivationProblem.CLOCK,problem(code(issued=305001,end="999999")))
        assertNull(problem(code(issued=305000,end="999999")))
    }
    @Test fun permanentCodeAndLowercaseDeviceIdWork() {
        assertNotNull(verifier.verify(code(end=""),"ab12-cd34-ef56","ledger_plus",5000))
    }
    @Test fun invalidTimeRangeIsNotAccepted() {
        assertEquals(ActivationProblem.MALFORMED,problem(code(end="1000")))
        assertEquals(ActivationProblem.MALFORMED,problem(code(issued=0)))
    }
    @Test fun changedSignatureCannotGrantAccess() {
        val parts=code().split('.').toMutableList();val bytes=Base64.getUrlDecoder().decode(parts[3]);bytes[0]=(bytes[0].toInt() xor 1).toByte()
        parts[3]=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertEquals(ActivationProblem.SIGNATURE,problem(parts.joinToString(".")))
    }
    @Test fun oversizedOrMalformedCodeFailsClosed() {
        for (code in listOf("x".repeat(16385),"TG2...","TG2.AAAAAAAAAAAAAAAA.?.?","hello")) assertNotNull(problem(code))
    }
    @Test fun noPublicKeyIsExplicitlyUnconfigured() {
        assertEquals(ActivationProblem.NOT_CONFIGURED,DeveloperSubscriptionVerifier("").check(code(),"AB12-CD34-EF56","ledger_plus",5000).problem)
    }
}
